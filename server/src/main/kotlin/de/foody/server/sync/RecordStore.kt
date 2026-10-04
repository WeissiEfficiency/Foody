package de.foody.server.sync

import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import kotlinx.serialization.json.JsonObject

/** Zugriff auf die Tabelle `sync_record`; arbeitet immer auf der übergebenen Verbindung (also in der Transaktion des Aufrufers). */
class RecordStore {
    private val columns = "id, type, rev, deleted, payload, updated_at"

    fun get(c: Connection, household: String, type: RecordType, id: String): SyncRecord? =
        c.prepareStatement("SELECT $columns FROM sync_record WHERE household_id = ? AND type = ? AND id = ?").use { st ->
            st.setString(1, household)
            st.setString(2, type.wire)
            st.setString(3, id)
            st.executeQuery().use { if (it.next()) it.toRecord() else null }
        }

    /** `true`, wenn der Datensatz existiert und nicht gelöscht ist. */
    fun exists(c: Connection, household: String, type: RecordType, id: String): Boolean =
        c.prepareStatement("SELECT 1 FROM sync_record WHERE household_id = ? AND type = ? AND id = ? AND deleted = 0").use { st ->
            st.setString(1, household)
            st.setString(2, type.wire)
            st.setString(3, id)
            st.executeQuery().use { it.next() }
        }

    /** Lebende Zutat mit dem Eindeutigkeitsschlüssel [name] (siehe `PayloadValidator.canonicalName`). */
    fun findLiveByCanonicalName(c: Connection, household: String, name: String): SyncRecord? =
        c.prepareStatement(
            "SELECT $columns FROM sync_record WHERE household_id = ? AND type = ? AND canonical_name = ? AND deleted = 0 ORDER BY rev LIMIT 1",
        ).use { st ->
            st.setString(1, household)
            st.setString(2, RecordType.INGREDIENT.wire)
            st.setString(3, name)
            st.executeQuery().use { if (it.next()) it.toRecord() else null }
        }

    /**
     * Schreibt [record] mit der nächsten Revision des Haushalts und liefert diese. [rawPayload] wird unverändert abgelegt
     * (`null` bei Löschung); die Löschzeit [deletedAt] gilt nur für gelöschte Datensätze.
     */
    fun put(
        c: Connection,
        household: String,
        record: SyncRecord,
        rawPayload: String?,
        canonicalName: String?,
        deletedAt: Long? = null,
    ): Long {
        c.prepareStatement("UPDATE household SET last_rev = last_rev + 1 WHERE id = ?").use { st ->
            st.setString(1, household)
            check(st.executeUpdate() == 1) { "Haushalt $household unbekannt" }
        }
        val rev = c.prepareStatement("SELECT last_rev FROM household WHERE id = ?").use { st ->
            st.setString(1, household)
            st.executeQuery().use { it.next(); it.getLong(1) }
        }
        c.prepareStatement(
            """
            INSERT INTO sync_record (household_id, type, id, rev, deleted, payload, updated_at, deleted_at, canonical_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (household_id, type, id) DO UPDATE SET
                rev = excluded.rev, deleted = excluded.deleted, payload = excluded.payload,
                updated_at = excluded.updated_at, deleted_at = excluded.deleted_at, canonical_name = excluded.canonical_name
            """.trimIndent(),
        ).use { st ->
            st.setString(1, household)
            st.setString(2, record.type.wire)
            st.setString(3, record.id)
            st.setLong(4, rev)
            st.setInt(5, if (record.deleted) 1 else 0)
            st.setString(6, rawPayload)
            st.setLong(7, record.updatedAt)
            if (record.deleted && deletedAt != null) st.setLong(8, deletedAt) else st.setNull(8, Types.INTEGER)
            st.setString(9, canonicalName)
            st.executeUpdate()
        }
        return rev
    }

    /** Datensätze mit `rev > since`, nach Revision aufsteigend, höchstens [limit]. */
    fun page(c: Connection, household: String, since: Long, limit: Int): List<SyncRecord> =
        c.prepareStatement("SELECT $columns FROM sync_record WHERE household_id = ? AND rev > ? ORDER BY rev LIMIT ?").use { st ->
            st.setString(1, household)
            st.setLong(2, since)
            st.setInt(3, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRecord()) } }
        }

    private fun ResultSet.toRecord(): SyncRecord {
        val raw = getString("payload")
        return SyncRecord(
            id = getString("id"),
            type = RecordType.entries.first { it.wire == getString("type") },
            deleted = getInt("deleted") != 0,
            updatedAt = getLong("updated_at"),
            rev = getLong("rev"),
            payload = raw?.let { Protocol.json.decodeFromString(JsonObject.serializer(), it) },
        )
    }
}
