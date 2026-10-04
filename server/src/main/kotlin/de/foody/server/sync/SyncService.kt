package de.foody.server.sync

import de.foody.server.db.Database
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.PushResult
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.sql.Connection
import java.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Sync-Kern: nimmt Datensätze eines Haushalts an und liefert Änderungen seit einer Revision. */
class SyncService(private val db: Database, private val store: RecordStore, private val clock: Clock) {

    /** Verarbeitet [records] der Reihe nach; jeder Datensatz läuft in einer eigenen Transaktion. */
    fun push(household: String, records: List<SyncRecord>): PushResponse =
        PushResponse(records.map { record -> db.tx { c -> applyOne(c, household, record) } })

    /** Liefert bis zu [limit] Datensätze mit `rev > since`; `hasMore` zeigt weitere Seiten an. */
    fun pull(household: String, since: Long, limit: Int): PullResponse {
        val fetched = db.tx { c -> store.page(c, household, since, limit + 1) }
        val hasMore = fetched.size > limit
        val records = if (hasMore) fetched.take(limit) else fetched
        return PullResponse(records, records.lastOrNull()?.rev ?: since, hasMore)
    }

    /**
     * Gesamte Logik für einen einzelnen Datensatz. Reihenfolge: Validierung, Zutaten-Merge, Löschen vs. Bearbeiten,
     * Referenzprüfung, Einkaufseintrag-Merge, sonst Last-Writer-Wins. Jeder Pfad, der einen lebenden Datensatz speichert,
     * läuft durch die Referenzprüfung.
     */
    private fun applyOne(c: Connection, household: String, record: SyncRecord): PushResult {
        PayloadValidator.validate(record)?.let { return rejected(record, it) }
        val stored = store.get(c, household, record.type, record.id)
        if (!record.deleted) {
            val canonicalName = PayloadValidator.canonicalName(record)
            if (canonicalName != null) {
                val canonical = store.findLiveByCanonicalName(c, household, canonicalName)
                if (canonical != null && canonical.id != record.id) {
                    return PushResult(record.id, record.type, PushStatus.MERGED, rev = canonical.rev, canonicalId = canonical.id, current = canonical)
                }
            }
            if (stored != null && stored.deleted) {
                val seenDeletion = record.baseRev != null && record.baseRev!! >= stored.rev!!
                if (!seenDeletion) return PushResult(record.id, record.type, PushStatus.MERGED, rev = stored.rev, current = stored)
            }
            if (PayloadValidator.references(record).any { (type, id) -> !store.exists(c, household, type, id) }) {
                return rejected(record, ErrorCode.MISSING_REFERENCE)
            }
        }
        val payload: JsonObject? = record.payload
        if (!record.deleted && record.type == RecordType.SHOPPING_ITEM && stored != null && !stored.deleted && isConflict(record, stored)) {
            return mergeShoppingItem(c, household, record, payload!!, stored)
        }
        val raw = payload?.let { Protocol.json.encodeToString(JsonObject.serializer(), it) }
        val rev = if (record.deleted) {
            store.put(c, household, record, null, null, deletedAt = clock.millis())
        } else {
            store.put(c, household, record, raw, PayloadValidator.canonicalName(record))
        }
        return PushResult(record.id, record.type, PushStatus.ACCEPTED, rev = rev)
    }

    /** Konflikt: der Datensatz existiert, und der Absender kannte seinen aktuellen Stand nicht. */
    private fun isConflict(incoming: SyncRecord, stored: SyncRecord): Boolean =
        incoming.baseRev == null || incoming.baseRev!! < stored.rev!!

    /**
     * Gleichzeitige Änderung eines Einkaufseintrags: der eingehende Payload gewinnt, nur `checked` und
     * `checkedChangedAt` kommen vom Stand mit dem späteren Zeitstempel (bei Gleichstand gilt „abgehakt“).
     * Es wird nur im rohen [JsonObject] ersetzt, damit unbekannte Felder erhalten bleiben.
     */
    private fun mergeShoppingItem(c: Connection, household: String, record: SyncRecord, incoming: JsonObject, stored: SyncRecord): PushResult {
        val storedPayload = stored.payload!!
        val incomingAt = incoming.timestamp()
        val storedAt = storedPayload.timestamp()
        val checked = when {
            incomingAt > storedAt -> incoming.checked()
            incomingAt < storedAt -> storedPayload.checked()
            else -> incoming.checked() || storedPayload.checked()
        }
        val winnerAt = maxOf(incomingAt, storedAt)
        val merged = JsonObject(incoming + mapOf("checked" to JsonPrimitive(checked), "checkedChangedAt" to JsonPrimitive(winnerAt)))
        val rev = store.put(c, household, record, Protocol.json.encodeToString(JsonObject.serializer(), merged), null)
        val current = SyncRecord(record.id, record.type, deleted = false, updatedAt = record.updatedAt, rev = rev, payload = merged)
        return PushResult(record.id, record.type, PushStatus.MERGED, rev = rev, current = current)
    }

    private fun JsonObject.timestamp(): Long = this["checkedChangedAt"]!!.jsonPrimitive.long

    private fun JsonObject.checked(): Boolean = this["checked"]!!.jsonPrimitive.boolean

    private fun rejected(record: SyncRecord, code: ErrorCode) =
        PushResult(record.id, record.type, PushStatus.REJECTED, code = code)
}
