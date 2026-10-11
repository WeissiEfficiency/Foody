package de.foody.server.web

import de.foody.domain.Gang
import de.foody.domain.ImportedRecipe
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.RezeptImport
import de.foody.domain.alsText
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface Speichern {
    data class Ok(val id: String) : Speichern
    /** Gespeicherter Stand hat eine andere Revision als die gesehene ([geloescht]: inzwischen gelöscht); nichts geschrieben. */
    data class Konflikt(val aktuelleRev: Long?, val geloescht: Boolean = false) : Speichern
    data class Fehler(val meldungen: Map<String, String>) : Speichern
    /** Die ID gab es im eigenen Haushalt nie. */
    data object NichtGefunden : Speichern
}

/**
 * Schreibt Rezepte aus dem Browser – nur über `SyncService.push`, damit Validierung, Revisionen und der Abgleich mit
 * den Handys genau wie bei einem Handy laufen. Das Rezept wird auf dem gespeicherten rohen JSON aufgebaut: Felder, die
 * der Browser nicht kennt (Foto, Favorit, Bewertung, Felder neuerer App-Versionen), bleiben erhalten.
 */
internal class RezeptSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    /** Prüfen und Schreiben unter der [Haushaltssperre]: zwei gleichzeitige Speichervorgänge sehen nie denselben Stand. */
    fun speichern(household: String, f: RezeptFormular): Speichern = Haushaltssperre.mit(household) { speichernGesperrt(household, f) }

    private fun speichernGesperrt(household: String, f: RezeptFormular): Speichern {
        val fehler = f.pruefen()
        if (fehler.isNotEmpty()) return Speichern.Fehler(fehler)
        val gespeichert = f.id?.let { id -> deps.db.tx { c -> store.get(c, household, RecordType.RECIPE, id) } }
        if (f.id != null && gespeichert == null) return Speichern.NichtGefunden
        // Gleichzeitige Änderung (z. B. am Handy) oder Löschung: nichts überschreiben, außer der Nutzer will es
        // Gelöscht: nie unter der alten ID wiederbeleben – der Nutzer kann als neues Rezept speichern
        if (gespeichert != null && gespeichert.deleted) return Speichern.Konflikt(gespeichert.rev, geloescht = true)
        if (gespeichert != null && gespeichert.rev != f.rev && !(f.erzwingen && gespeichert.rev == f.rev)) {
            return Speichern.Konflikt(gespeichert.rev)
        }
        val ids = zutatenAufloesen(household, f.zeilen.map { it.zutat })
        val id = f.id ?: UUID.randomUUID().toString()
        val basis = gespeichert?.payload?.takeIf { !gespeichert.deleted } ?: JsonObject(mapOf("favorite" to JsonPrimitive(false)))
        val zeilen = f.zeilen.mapIndexed { i, z ->
            RecipePayload.Line(UUID.randomUUID().toString(), ids[i], z.mengeZahl()!!.stripTrailingZeros().toPlainString(), z.einheitWert()!!.name, i, z.hinweis.ifBlank { null }, z.optional)
        }
        val schritte = f.schrittListe().mapIndexed { i, text -> RecipePayload.Step(UUID.randomUUID().toString(), i, text) }
        val felder: Map<String, JsonElement> = mapOf(
            "name" to JsonPrimitive(f.name.trim()),
            "servings" to JsonPrimitive(f.portionenZahl()!!),
            "prep" to (f.minuten(f.vorbereitung)?.let(::JsonPrimitive) ?: JsonNull),
            "cook" to (f.minuten(f.kochzeit)?.let(::JsonPrimitive) ?: JsonNull),
            "notes" to (f.notizen.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull),
            "tags" to JsonPrimitive(f.tags.trim()),
            "sourceUrl" to (f.quelle.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull),
            "lines" to Protocol.json.encodeToJsonElement(ListSerializer(RecipePayload.Line.serializer()), zeilen),
            "steps" to Protocol.json.encodeToJsonElement(ListSerializer(RecipePayload.Step.serializer()), schritte),
            // „Vermuten“ = null; festgelegt: unbekannte Werte (neuere App) bleiben wie in der App erhalten
            "mahlzeiten" to einordnung(f.einordnungFest, alsText(f.mahlzeiten, basis.text("mahlzeiten"), Mahlzeit.entries)),
            "gaenge" to einordnung(f.einordnungFest, alsText(f.gaenge, basis.text("gaenge"), Gang.entries)),
        )
        val ergebnis = push(household, SyncRecord(
            id = id, type = RecordType.RECIPE, updatedAt = deps.clock.millis(), baseRev = gespeichert?.rev, payload = JsonObject(basis + felder),
        ))
        return when (ergebnis.status) {
            PushStatus.REJECTED -> Speichern.Fehler(mapOf("allgemein" to "Der Server hat das Rezept abgelehnt (${ergebnis.code ?: "unbekannt"})."))
            else -> Speichern.Ok(id)
        }
    }

    /** Setzt oder löscht `archivedAt`; `false`, wenn es das Rezept im Haushalt nicht (mehr) gibt. */
    fun archivieren(household: String, id: String, archiviert: Boolean): Boolean {
        val gespeichert = deps.db.tx { c -> store.get(c, household, RecordType.RECIPE, id) }
        if (gespeichert == null || gespeichert.deleted || gespeichert.payload == null) return false
        val jetzt = deps.clock.millis()
        val wert: JsonElement = if (archiviert) JsonPrimitive(jetzt) else JsonNull
        val ergebnis = push(household, SyncRecord(
            id = id, type = RecordType.RECIPE, updatedAt = jetzt, baseRev = gespeichert.rev, payload = JsonObject(gespeichert.payload!! + ("archivedAt" to wert)),
        ))
        return ergebnis.status != PushStatus.REJECTED
    }

    /**
     * Legt ein importiertes Rezept an (Rechnung wie in der App: `RezeptImport`); liefert die neue ID oder `null`, wenn der
     * Server es ablehnt.
     */
    fun importieren(household: String, rezept: ImportedRecipe, tag: String): String? {
        // Leerer Zutatenname (z. B. Zeile „, gehackt“) wäre für den Server ungültig: Datei gilt als nicht erkannt
        if (rezept.ingredients.any { it.name.isBlank() || it.name.trim().length > RezeptFormular.MAX_NAME }) return null
        val ids = zutatenAufloesen(household, rezept.ingredients.map { it.name })
        val zutaten = daten.fuer(household).zutaten
        // Vom Server abgelehnte neue Zutat: nicht anlegen statt Fehlerseite
        val aufgeloest = ids.map { zutaten[it] ?: return null }
        val ergebnis = RezeptImport.vorbereiten(rezept, aufgeloest, VORGABE_PORTIONEN)
        val id = UUID.randomUUID().toString()
        val payload = RecipePayload(
            name = rezept.name, servings = ergebnis.portionen, tags = tag, favorite = false, sourceUrl = rezept.sourceUrl,
            notes = RezeptImport.hinweis(rezept.sourceUrl, ergebnis.portionen, ergebnis.geschaetzt),
            lines = ergebnis.zeilen.mapIndexed { i, z ->
                RecipePayload.Line(UUID.randomUUID().toString(), z.zutatId, z.menge.stripTrailingZeros().toPlainString(), z.einheit.name, i, z.hinweis, z.optional)
            },
            steps = rezept.steps.mapIndexed { i, text -> RecipePayload.Step(UUID.randomUUID().toString(), i, text) },
        )
        val antwort = push(household, SyncRecord(
            id = id, type = RecordType.RECIPE, updatedAt = deps.clock.millis(),
            payload = Protocol.json.encodeToJsonElement(RecipePayload.serializer(), payload) as JsonObject,
        ))
        return id.takeIf { antwort.status != PushStatus.REJECTED }
    }

    /**
     * Zutaten-IDs zu [namen] (gleiche Reihenfolge): exakter Name (ohne Groß-/Kleinschreibung), sonst über den Katalog
     * vereinheitlicht („Mehl“ → „Weizenmehl“), sonst neue Zutat. Neue werden zuerst gesendet; führt der Server sie mit
     * einer vorhandenen zusammen, gilt deren ID.
     */
    fun zutatenAufloesen(household: String, namen: List<String>): List<String> {
        val vorhanden = daten.fuer(household).zutaten.values.associate { it.name.lowercase() to it.id }
        val neu = LinkedHashMap<String, Pair<String, String>>() // Schlüssel klein → (ID, Name)
        val ids = namen.map { roh ->
            val name = roh.trim()
            val kanonisch = IngredientCatalog.canonicalName(name)
            vorhanden[name.lowercase()] ?: vorhanden[kanonisch.lowercase()]
                ?: neu.getOrPut(kanonisch.lowercase()) { UUID.randomUUID().toString() to kanonisch }.first
        }
        if (neu.isEmpty()) return ids
        val jetzt = deps.clock.millis()
        val ergebnisse = deps.sync.push(household, neu.values.map { (id, name) ->
            SyncRecord(
                id = id, type = RecordType.INGREDIENT, updatedAt = jetzt,
                payload = Protocol.json.encodeToJsonElement(IngredientPayload.serializer(),
                    IngredientPayload(name = name, category = IngredientCatalog.guessCategory(name))) as JsonObject,
            )
        }).results.associateBy { it.id }
        val ersetzt = ergebnisse.values.filter { it.status == PushStatus.MERGED && it.canonicalId != null }.associate { it.id to it.canonicalId!! }
        return ids.map { ersetzt[it] ?: it }
    }

    private companion object {
        /** Wie `DEFAULT_IMPORT_SERVINGS` der App. */
        const val VORGABE_PORTIONEN = 4
    }

    private fun push(household: String, record: SyncRecord) = deps.sync.push(household, listOf(record)).results.single()

    private fun einordnung(fest: Boolean, text: String?): JsonElement = if (fest && text != null) JsonPrimitive(text) else JsonNull

    private fun JsonObject.text(feld: String): String? = (this[feld] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
}
