package de.foody.server.sync

import de.foody.server.ApiException
import de.foody.server.db.Database
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.PayloadValidator
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.PushResponse
import de.foody.api.protocol.PushResult
import de.foody.api.protocol.PushStatus
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Sync-Kern: nimmt Datensätze eines Kontos an und liefert Änderungen seit einer Revision. */
class SyncService(private val db: Database, private val store: RecordStore, private val clock: Clock) {

    /** Verarbeitet [records] der Reihe nach; jeder Datensatz läuft in einer eigenen Transaktion. */
    fun push(konto: String, records: List<SyncRecord>): PushResponse =
        PushResponse(records.map { record -> db.tx { c -> applyOne(c, konto, record) } })

    /** Liefert bis zu [limit] Datensätze mit `rev > since`; `hasMore` zeigt weitere Seiten an. */
    fun pull(konto: String, since: Long, limit: Int): PullResponse {
        val fetched = db.tx { c ->
            val (lastRev, compactedBefore) = revisions(c, konto)
            // Ein Cursor vor der Kompaktierungsgrenze hat Löschmarkierungen verpasst, einer hinter dem Server-Stand stammt
            // von einer Datenbank, die es so nicht mehr gibt (z. B. nach dem Einspielen einer Sicherung): Neu synchronisieren.
            if (since != 0L && (since < compactedBefore || since > lastRev)) {
                throw ApiException(HttpStatusCode.Gone, ErrorCode.CURSOR_EXPIRED)
            }
            store.page(c, konto, since, limit + 1)
        }
        val hasMore = fetched.size > limit
        val records = if (hasMore) fetched.take(limit) else fetched
        return PullResponse(records, records.lastOrNull()?.rev ?: since, hasMore)
    }

    /** `last_rev` und `compacted_before_rev` des Kontos. */
    private fun revisions(c: Connection, konto: String): Pair<Long, Long> =
        c.prepareStatement("SELECT last_rev, compacted_before_rev FROM user WHERE id = ?").use { st ->
            st.setString(1, konto)
            st.executeQuery().use { if (it.next()) it.getLong(1) to it.getLong(2) else 0L to 0L }
        }

    /**
     * Gesamte Logik für einen einzelnen Datensatz. Reihenfolge: Validierung, Zutaten-Merge, Löschen vs. Bearbeiten,
     * Referenzprüfung, Einkaufseintrag-Merge, Erstsendung ohne `baseRev`, sonst Last-Writer-Wins. Jeder Pfad, der einen lebenden Datensatz speichert,
     * läuft durch die Referenzprüfung.
     */
    private fun applyOne(c: Connection, konto: String, record: SyncRecord): PushResult {
        PayloadValidator.validate(record)?.let { return rejected(record, it) }
        val stored = store.get(c, konto, record.type, record.id)
        if (!record.deleted) {
            val canonicalName = PayloadValidator.canonicalName(record)
            if (canonicalName != null) {
                val canonical = store.findLiveByCanonicalName(c, konto, canonicalName)
                if (canonical != null && canonical.id != record.id) {
                    return PushResult(record.id, record.type, PushStatus.MERGED, rev = canonical.rev, canonicalId = canonical.id, current = canonical)
                }
            }
            if (stored != null && stored.deleted) {
                val seenDeletion = record.baseRev != null && record.baseRev!! >= stored.rev!!
                if (!seenDeletion) return PushResult(record.id, record.type, PushStatus.MERGED, rev = stored.rev, current = stored)
            }
            if (PayloadValidator.references(record).any { (type, id) -> !store.exists(c, konto, type, id) }) {
                return rejected(record, ErrorCode.MISSING_REFERENCE)
            }
        }
        val payload: JsonObject? = record.payload
        if (!record.deleted && record.type == RecordType.SHOPPING_ITEM && stored != null && !stored.deleted && isConflict(record, stored)) {
            return mergeShoppingItem(c, konto, record, payload!!, stored)
        }
        // Erstes Senden eines Datensatzes, den der Server schon hat („Zusammenführen“, feste IDs der Startzutaten): Der
        // Absender kannte den Server-Stand nie. Nur ein jüngerer Stand gewinnt, sonst bleibt der des Servers.
        if (!record.deleted && record.baseRev == null && stored != null && !stored.deleted && record.updatedAt <= stored.updatedAt) {
            return PushResult(record.id, record.type, PushStatus.MERGED, rev = stored.rev, current = stored)
        }
        val raw = payload?.let { Protocol.json.encodeToString(JsonObject.serializer(), it) }
        val rev = if (record.deleted) {
            store.put(c, konto, record, null, null, deletedAt = clock.millis())
        } else {
            store.put(c, konto, record, raw, PayloadValidator.canonicalName(record))
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
    private fun mergeShoppingItem(c: Connection, konto: String, record: SyncRecord, incoming: JsonObject, stored: SyncRecord): PushResult {
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
        val rev = store.put(c, konto, record, Protocol.json.encodeToString(JsonObject.serializer(), merged), null)
        val current = SyncRecord(record.id, record.type, deleted = false, updatedAt = record.updatedAt, rev = rev, payload = merged)
        return PushResult(record.id, record.type, PushStatus.MERGED, rev = rev, current = current)
    }

    private fun JsonObject.timestamp(): Long = this["checkedChangedAt"]!!.jsonPrimitive.long

    private fun JsonObject.checked(): Boolean = this["checked"]!!.jsonPrimitive.boolean

    private fun rejected(record: SyncRecord, code: ErrorCode) =
        PushResult(record.id, record.type, PushStatus.REJECTED, code = code)
}
