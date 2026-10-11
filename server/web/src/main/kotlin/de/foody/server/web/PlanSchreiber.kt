package de.foody.server.web

import de.foody.domain.Kochen
import de.foody.domain.Mahlzeit
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Ergebnis einer Schreibaktion aus dem Browser. */
internal sealed interface Schreiben {
    data object Ok : Schreiben

    /** Gibt es im Haushalt nicht (fremde oder unbekannte ID) → 404. */
    data object NichtGefunden : Schreiben

    /** Inzwischen gelöscht (z. B. am Handy) → kein Fehler, nur ein Hinweis. */
    data object Weg : Schreiben

    /** Server hat einen Datensatz abgelehnt (z. B. fehlende Referenz). */
    data object Abgelehnt : Schreiben
}

/**
 * Sperre je Haushalt für Schreibaktionen, die erst lesen und dann mehrere Datensätze schreiben („gekocht“, Neu
 * berechnen): Zwei gleichzeitige Anfragen (Doppelklick) sähen sonst beide den alten Stand und schrieben doppelt.
 */
internal object Haushaltssperre {
    private val sperren = ConcurrentHashMap<String, Any>()

    fun <T> mit(household: String, block: () -> T): T = synchronized(sperren.computeIfAbsent(household) { Any() }) { block() }
}

/**
 * Schreibt Planeinträge aus dem Browser – nur über `SyncService.push`, jeweils auf dem gerade gespeicherten Stand
 * (unbekannte Felder neuerer App-Versionen bleiben erhalten). „Gekocht“ zieht den Vorrat ab wie die App ([Kochen]).
 */
internal class PlanSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    fun einplanen(household: String, datum: LocalDate, mahlzeit: Mahlzeit, rezeptId: String, portionen: Int): Schreiben {
        val rezept = daten.fuer(household).rezepte[rezeptId] ?: return Schreiben.NichtGefunden
        if (rezept.payload.archivedAt != null) return Schreiben.Weg
        val payload = MealSlotPayload(datum.toString(), mahlzeit.name, rezeptId, portionen)
        return push(household, SyncRecord(
            id = UUID.randomUUID().toString(), type = RecordType.MEAL_SLOT, updatedAt = deps.clock.millis(),
            payload = Protocol.json.encodeToJsonElement(MealSlotPayload.serializer(), payload) as JsonObject,
        ))
    }

    /**
     * Übernimmt genau die angezeigten Paare (Tag → Rezept) mit den Portionen des Rezepts. Erst wird jedes Paar geprüft
     * (aktives Rezept des Haushalts, Tag in [von]..[bis]); ist eines ungültig, wird nichts geschrieben → [Schreiben.NichtGefunden].
     */
    fun vorschlagUebernehmen(household: String, mahlzeit: Mahlzeit, paare: List<Pair<LocalDate, String>>, von: LocalDate, bis: LocalDate): Schreiben {
        val rezepte = daten.fuer(household).rezepte
        val gueltig = paare.all { (tag, id) -> tag in von..bis && rezepte[id]?.payload?.archivedAt == null && id in rezepte }
        if (!gueltig) return Schreiben.NichtGefunden
        for ((tag, id) in paare) {
            val r = einplanen(household, tag, mahlzeit, id, rezepte.getValue(id).domain.defaultServings)
            if (r != Schreiben.Ok) return r
        }
        return Schreiben.Ok
    }

    /** [aktion]: `minus`, `plus` (Portionen 1–99), `frueher`, `spaeter` (± 1 Tag), `loeschen`. */
    fun aendern(household: String, id: String, aktion: String): Schreiben {
        val gespeichert = gespeichert(household, id) ?: return Schreiben.NichtGefunden
        if (gespeichert.deleted || gespeichert.payload == null) return Schreiben.Weg
        val jetzt = deps.clock.millis()
        if (aktion == "loeschen") {
            return push(household, SyncRecord(id = id, type = RecordType.MEAL_SLOT, deleted = true, updatedAt = jetzt, baseRev = gespeichert.rev))
        }
        val p = Protocol.json.decodeFromJsonElement(MealSlotPayload.serializer(), gespeichert.payload!!)
        val datum = LocalDate.parse(p.date)
        val felder: Map<String, JsonPrimitive> = when (aktion) {
            "minus" -> mapOf("servings" to JsonPrimitive((p.servings - 1).coerceIn(1, 99)))
            "plus" -> mapOf("servings" to JsonPrimitive((p.servings + 1).coerceIn(1, 99)))
            "frueher" -> mapOf("date" to JsonPrimitive(datum.minusDays(1).toString()))
            "spaeter" -> mapOf("date" to JsonPrimitive(datum.plusDays(1).toString()))
            else -> return Schreiben.Ok
        }
        return push(household, SyncRecord(
            id = id, type = RecordType.MEAL_SLOT, updatedAt = jetzt, baseRev = gespeichert.rev, payload = JsonObject(gespeichert.payload!! + felder),
        ))
    }

    /**
     * Schon gekocht → nichts tun (kein zweiter Abzug). Erst wird der Eintrag als gekocht gespeichert, dann der Vorrat
     * abgezogen (Reihenfolge laut Spec): Scheitert der Abzug, zieht ein erneuter Klick nicht noch einmal ab.
     */
    fun gekocht(household: String, id: String): Schreiben = Haushaltssperre.mit(household) {
        val gespeichert = gespeichert(household, id) ?: return@mit Schreiben.NichtGefunden
        if (gespeichert.deleted || gespeichert.payload == null) return@mit Schreiben.Weg
        val p = Protocol.json.decodeFromJsonElement(MealSlotPayload.serializer(), gespeichert.payload!!)
        if (p.cookedAt != null) return@mit Schreiben.Ok
        val h = daten.fuer(household)
        val jetzt = deps.clock.millis()
        val slot = push(household, SyncRecord(
            id = id, type = RecordType.MEAL_SLOT, updatedAt = jetzt, baseRev = gespeichert.rev,
            payload = JsonObject(gespeichert.payload!! + mapOf("cookedAt" to JsonPrimitive(jetzt))),
        ))
        if (slot != Schreiben.Ok) return@mit slot
        val rezept = h.rezepte[p.recipeId] ?: return@mit Schreiben.Ok
        val neu = Kochen.vorratAbziehen(
            rezept.domain, p.servings, h.zutaten.mapValues { it.value.conversion },
            h.vorrat.map { Kochen.Vorrat(it.id, it.zutatId, it.menge, it.einheit) },
        )
        for ((vorratId, menge) in neu) {
            val v = gespeichert(household, vorratId, RecordType.PANTRY_ITEM)?.takeIf { !it.deleted && it.payload != null } ?: continue
            val r = push(household, SyncRecord(
                id = vorratId, type = RecordType.PANTRY_ITEM, updatedAt = jetzt, baseRev = v.rev,
                payload = JsonObject(v.payload!! + mapOf("amount" to JsonPrimitive(menge.toPlainString()))),
            ))
            if (r != Schreiben.Ok) return@mit r
        }
        Schreiben.Ok
    }

    private fun gespeichert(household: String, id: String, typ: RecordType = RecordType.MEAL_SLOT) =
        deps.db.tx { c -> store.get(c, household, typ, id) }

    private fun push(household: String, record: SyncRecord): Schreiben =
        if (deps.sync.push(household, listOf(record)).results.single().status == PushStatus.REJECTED) Schreiben.Abgelehnt else Schreiben.Ok
}
