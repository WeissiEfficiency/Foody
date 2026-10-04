package de.foody.server.sync

import de.foody.server.db.Database
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.PushResult
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.SyncRecord
import java.sql.Connection
import java.time.Clock
import kotlinx.serialization.json.JsonObject

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

    /** Gesamte Logik für einen einzelnen Datensatz; Konfliktregeln (Task 6) kommen als Zweige hierher. */
    private fun applyOne(c: Connection, household: String, record: SyncRecord): PushResult {
        PayloadValidator.validate(record)?.let { return rejected(record, it) }
        if (PayloadValidator.references(record).any { (type, id) -> !store.exists(c, household, type, id) }) {
            return rejected(record, ErrorCode.MISSING_REFERENCE)
        }
        val payload: JsonObject? = record.payload
        val raw = payload?.let { Protocol.json.encodeToString(JsonObject.serializer(), it) }
        val rev = if (record.deleted) {
            store.put(c, household, record, null, null, deletedAt = clock.millis())
        } else {
            store.put(c, household, record, raw, PayloadValidator.canonicalName(record))
        }
        return PushResult(record.id, record.type, PushStatus.ACCEPTED, rev = rev)
    }

    private fun rejected(record: SyncRecord, code: ErrorCode) =
        PushResult(record.id, record.type, PushStatus.REJECTED, code = code)
}
