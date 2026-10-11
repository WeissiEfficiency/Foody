package de.foody.server.web

import de.foody.domain.IngredientCatalog
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.http.Parameters
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Formular einer Zutat als Texte (bleiben bei Fehlern stehen). Nährwerte je 100 g bzw. 100 ml, Energie in kJ. */
internal data class ZutatFormular(
    val id: String?,
    val name: String,
    val kategorie: String,
    val dichte: String,
    val stueckgewicht: String,
    val basis: String,
    val kj: String,
    val eiweiss: String,
    val kh: String,
    val fett: String,
    val ballaststoffe: String,
    val zucker: String,
    val salz: String,
) {
    /** Zahlenfelder mit Payload-Schlüssel und Bezeichnung (für Meldungen). */
    val zahlen: List<Triple<String, String, String>> get() = listOf(
        Triple("density", "Dichte", dichte), Triple("pieceWeight", "Stückgewicht", stueckgewicht), Triple("energyKj", "Energie", kj),
        Triple("protein", "Eiweiß", eiweiss), Triple("carbs", "Kohlenhydrate", kh), Triple("fat", "Fett", fett),
        Triple("fiber", "Ballaststoffe", ballaststoffe), Triple("sugar", "Zucker", zucker), Triple("salt", "Salz", salz),
    )

    fun basisWert(): String? = basis.takeIf { b -> b in BASEN }

    fun pruefen(): List<String> = buildList {
        if (name.isBlank()) add("Bitte einen Namen eingeben.") else if (name.trim().length > MAX_NAME) add("Der Name ist zu lang (höchstens $MAX_NAME Zeichen).")
        if (kategorie.trim().length > MAX_NAME) add("Die Abteilung ist zu lang (höchstens $MAX_NAME Zeichen).")
        if (basis.isNotEmpty() && basisWert() == null) add("Bitte eine Basis wählen.")
        for ((_, text, wert) in zahlen) if (wert.isNotBlank() && Eingaben.zahl(wert) == null) add("$text: bitte eine Zahl ≥ 0.")
    }

    companion object {
        const val MAX_NAME = 200
        val BASEN = setOf("PER_100_G", "PER_100_ML")

        fun aus(p: Parameters) = ZutatFormular(
            p["id"]?.takeIf { it.isNotEmpty() }, p["name"].orEmpty(), p["kategorie"].orEmpty(), p["dichte"].orEmpty(), p["stueckgewicht"].orEmpty(),
            p["basis"].orEmpty(), p["kj"].orEmpty(), p["eiweiss"].orEmpty(), p["kh"].orEmpty(), p["fett"].orEmpty(), p["ballaststoffe"].orEmpty(),
            p["zucker"].orEmpty(), p["salz"].orEmpty(),
        )

        fun aus(z: WebZutat): ZutatFormular {
            fun t(s: String?) = s?.toBigDecimalOrNull()?.let(Eingaben::anzeigen).orEmpty()
            val p = z.payload
            return ZutatFormular(z.id, p.name, p.category.orEmpty(), t(p.density), t(p.pieceWeight), p.basis.orEmpty(), t(p.energyKj),
                t(p.protein), t(p.carbs), t(p.fat), t(p.fiber), t(p.sugar), t(p.salt))
        }

        fun leer() = ZutatFormular(null, "", "", "", "", "PER_100_G", "", "", "", "", "", "", "")
    }
}

internal sealed interface ZutatSpeichern {
    data class Ok(val id: String) : ZutatSpeichern
    data class GibtEsSchon(val id: String, val name: String) : ZutatSpeichern
    data class Fehler(val meldungen: List<String>) : ZutatSpeichern
    data object NichtGefunden : ZutatSpeichern
    data object Weg : ZutatSpeichern
    data object Abgelehnt : ZutatSpeichern
}

/**
 * Schreibt Zutaten aus dem Browser – nur über `SyncService.push`, auf dem gespeicherten Roh-JSON (Strichcode und Quelle
 * bleiben). Namen werden wie in der App vereinheitlicht; eine Dublette wird nicht geschrieben, sonst führte der Server
 * die neue Zutat still mit der vorhandenen zusammen.
 */
internal class ZutatSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    fun speichern(household: String, f: ZutatFormular): ZutatSpeichern {
        val gespeichert = f.id?.let { id -> deps.db.tx { c -> store.get(c, household, RecordType.INGREDIENT, id) } ?: return ZutatSpeichern.NichtGefunden }
        if (gespeichert != null && (gespeichert.deleted || gespeichert.payload == null)) return ZutatSpeichern.Weg
        f.pruefen().takeIf { it.isNotEmpty() }?.let { return ZutatSpeichern.Fehler(it) }
        val name = IngredientCatalog.canonicalName(f.name.trim())
        daten.fuer(household).zutatenRoh.values.firstOrNull { it.id != f.id && it.payload.name.equals(name, ignoreCase = true) }
            ?.let { return ZutatSpeichern.GibtEsSchon(it.id, it.payload.name) }
        val basis = f.basisWert()
        // Ohne Basis keine Nährwerte (wie die App); Dichte und Stückgewicht gelten unabhängig davon
        fun zahl(schluessel: String, text: String): JsonElement =
            if (basis == null && schluessel !in setOf("density", "pieceWeight")) JsonNull
            else Eingaben.zahl(text)?.let { JsonPrimitive(it.toPlainString()) } ?: JsonNull
        val kategorie = f.kategorie.trim().ifEmpty { null } ?: if (gespeichert == null) IngredientCatalog.guessCategory(name) else null
        val felder: Map<String, JsonElement> = mapOf(
            "name" to JsonPrimitive(name),
            "category" to (kategorie?.let { JsonPrimitive(it) } ?: JsonNull),
            "basis" to (basis?.let { JsonPrimitive(it) } ?: JsonNull),
        ) + f.zahlen.associate { (schluessel, _, text) -> schluessel to zahl(schluessel, text) }
        val id = f.id ?: UUID.randomUUID().toString()
        val basisJson = gespeichert?.payload ?: (Protocol.json.encodeToJsonElement(IngredientPayload.serializer(), IngredientPayload(name = name)) as JsonObject)
        val ergebnis = deps.sync.push(household, listOf(SyncRecord(
            id = id, type = RecordType.INGREDIENT, updatedAt = deps.clock.millis(), baseRev = gespeichert?.rev, payload = JsonObject(basisJson + felder),
        ))).results.single()
        return when {
            ergebnis.status == PushStatus.REJECTED -> ZutatSpeichern.Abgelehnt
            // Gleichzeitig am Handy angelegt: der Server führt zusammen – dann gibt es sie eben schon
            ergebnis.status == PushStatus.MERGED && ergebnis.canonicalId != null && ergebnis.canonicalId != id ->
                ZutatSpeichern.GibtEsSchon(ergebnis.canonicalId!!, name)
            else -> ZutatSpeichern.Ok(id)
        }
    }

    /** Wo die Zutat noch verwendet wird: Rezeptnamen, „Vorrat“, Namen von Einkaufslisten. */
    fun verwendungen(household: String, id: String): List<String> {
        val h = daten.fuer(household)
        val rezepte = h.rezepte.values.filter { r -> r.domain.ingredients.any { it.ingredientId == id } }.map { it.payload.name }.sorted()
        val vorrat = listOfNotNull("Vorrat".takeIf { h.vorrat.any { it.zutatId == id } })
        val listenIds = h.eintraege.filter { it.payload.ingredientId == id }.map { it.listId }.toSet()
        val listen = h.listen.filter { it.id in listenIds }.map { it.name }.sorted()
        return rezepte + vorrat + listen
    }

    /** Nur aufrufen, wenn [verwendungen] leer ist – der Server prüft beim Löschen keine Verweise. */
    fun loeschen(household: String, id: String): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.INGREDIENT, id) } ?: return Schreiben.NichtGefunden
        if (roh.deleted) return Schreiben.Weg
        val r = deps.sync.push(household, listOf(SyncRecord(id = id, type = RecordType.INGREDIENT, deleted = true, updatedAt = deps.clock.millis(), baseRev = roh.rev)))
        return if (r.results.single().status == PushStatus.REJECTED) Schreiben.Abgelehnt else Schreiben.Ok
    }
}
