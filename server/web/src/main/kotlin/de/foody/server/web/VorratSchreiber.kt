package de.foody.server.web

import de.foody.domain.MeasureUnit
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Schreibt Vorratseinträge aus dem Browser – nur über `SyncService.push`, auf dem gespeicherten Roh-JSON. */
internal class VorratSchreiber(private val deps: ServerDeps, private val daten: HaushaltsDaten) {
    private val store = RecordStore()

    /** Unbekannte Zutat wird wie im Rezept-Editor angelegt (Name vereinheitlicht, Abteilung geschätzt). */
    /** Unter der [Haushaltssperre]: Eine gleichzeitig gelöschte Zutat kann nicht zwischen Auflösen und Anlegen verschwinden. */
    fun anlegen(household: String, zutatName: String, menge: BigDecimal, einheit: MeasureUnit, haltbarBis: LocalDate?): Schreiben =
        Haushaltssperre.mit(household) { anlegenGesperrt(household, zutatName, menge, einheit, haltbarBis) }

    private fun anlegenGesperrt(household: String, zutatName: String, menge: BigDecimal, einheit: MeasureUnit, haltbarBis: LocalDate?): Schreiben {
        val zutatId = RezeptSchreiber(deps, daten).zutatenAufloesen(household, listOf(zutatName)).singleOrNull() ?: return Schreiben.Abgelehnt
        val p = PantryItemPayload(zutatId, menge.toPlainString(), einheit.name, haltbarBis?.toString())
        return push(household, SyncRecord(
            id = UUID.randomUUID().toString(), type = RecordType.PANTRY_ITEM, updatedAt = deps.clock.millis(),
            payload = Protocol.json.encodeToJsonElement(PantryItemPayload.serializer(), p) as JsonObject,
        ))
    }

    fun aendern(household: String, id: String, menge: BigDecimal, einheit: MeasureUnit, haltbarBis: LocalDate?): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.PANTRY_ITEM, id) } ?: return Schreiben.NichtGefunden
        if (roh.deleted || roh.payload == null) return Schreiben.Weg
        val felder = mapOf(
            "amount" to JsonPrimitive(menge.toPlainString()),
            "unit" to JsonPrimitive(einheit.name),
            "bestBefore" to (haltbarBis?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
        )
        return push(household, SyncRecord(id = id, type = RecordType.PANTRY_ITEM, updatedAt = deps.clock.millis(), baseRev = roh.rev, payload = JsonObject(roh.payload!! + felder)))
    }

    fun loeschen(household: String, id: String): Schreiben {
        val roh = deps.db.tx { c -> store.get(c, household, RecordType.PANTRY_ITEM, id) } ?: return Schreiben.NichtGefunden
        if (roh.deleted) return Schreiben.Weg
        return push(household, SyncRecord(id = id, type = RecordType.PANTRY_ITEM, deleted = true, updatedAt = deps.clock.millis(), baseRev = roh.rev))
    }

    private fun push(household: String, record: SyncRecord): Schreiben =
        if (deps.sync.push(household, listOf(record)).results.single().status == PushStatus.REJECTED) Schreiben.Abgelehnt else Schreiben.Ok
}
