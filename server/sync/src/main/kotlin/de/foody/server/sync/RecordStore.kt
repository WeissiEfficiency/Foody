package de.foody.server.sync

import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import kotlinx.serialization.json.JsonObject

/** Zugriff auf die Tabelle `sync_record`; arbeitet immer auf der übergebenen Verbindung (also in der Transaktion des Aufrufers). */
class RecordStore {
    private val columns = "id, type, rev, deleted, payload, updated_at"

    fun get(c: Connection, konto: String, type: RecordType, id: String): SyncRecord? =
        c.prepareStatement("SELECT $columns FROM sync_record WHERE konto_id = ? AND type = ? AND id = ?").use { st ->
            st.setString(1, konto)
            st.setString(2, type.wire)
            st.setString(3, id)
            st.executeQuery().use { if (it.next()) it.toRecord() else null }
        }

    /** `true`, wenn der Datensatz existiert und nicht gelöscht ist. */
    fun exists(c: Connection, konto: String, type: RecordType, id: String): Boolean =
        c.prepareStatement("SELECT 1 FROM sync_record WHERE konto_id = ? AND type = ? AND id = ? AND deleted = 0").use { st ->
            st.setString(1, konto)
            st.setString(2, type.wire)
            st.setString(3, id)
            st.executeQuery().use { it.next() }
        }

    /** Lebende Zutat mit dem Eindeutigkeitsschlüssel [name] (siehe `PayloadValidator.canonicalName`). */
    fun findLiveByCanonicalName(c: Connection, konto: String, name: String): SyncRecord? =
        c.prepareStatement(
            "SELECT $columns FROM sync_record WHERE konto_id = ? AND type = ? AND canonical_name = ? AND deleted = 0 ORDER BY rev LIMIT 1",
        ).use { st ->
            st.setString(1, konto)
            st.setString(2, RecordType.INGREDIENT.wire)
            st.setString(3, name)
            st.executeQuery().use { if (it.next()) it.toRecord() else null }
        }

    /**
     * Schreibt [record] mit der nächsten Revision des Kontos und liefert diese. [rawPayload] wird unverändert abgelegt
     * (`null` bei Löschung); die Löschzeit [deletedAt] gilt nur für gelöschte Datensätze.
     */
    fun put(
        c: Connection,
        konto: String,
        record: SyncRecord,
        rawPayload: String?,
        canonicalName: String?,
        deletedAt: Long? = null,
    ): Long {
        require(!record.deleted || deletedAt != null) { "Löschung ohne Löschzeit würde nie kompaktiert" }
        c.prepareStatement("UPDATE user SET last_rev = last_rev + 1 WHERE id = ?").use { st ->
            st.setString(1, konto)
            check(st.executeUpdate() == 1) { "Konto $konto unbekannt" }
        }
        val rev = c.prepareStatement("SELECT last_rev FROM user WHERE id = ?").use { st ->
            st.setString(1, konto)
            st.executeQuery().use { it.next(); it.getLong(1) }
        }
        c.prepareStatement(
            """
            INSERT INTO sync_record (konto_id, type, id, rev, deleted, payload, updated_at, deleted_at, canonical_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (konto_id, type, id) DO UPDATE SET
                rev = excluded.rev, deleted = excluded.deleted, payload = excluded.payload,
                updated_at = excluded.updated_at, deleted_at = excluded.deleted_at, canonical_name = excluded.canonical_name
            """.trimIndent(),
        ).use { st ->
            st.setString(1, konto)
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
    fun page(c: Connection, konto: String, since: Long, limit: Int): List<SyncRecord> =
        c.prepareStatement("SELECT $columns FROM sync_record WHERE konto_id = ? AND rev > ? ORDER BY rev LIMIT ?").use { st ->
            st.setString(1, konto)
            st.setLong(2, since)
            st.setInt(3, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRecord()) } }
        }

    /**
     * Alle nicht gelöschten Datensätze des Kontos und seine höchste Revision (gelöschte zählen mit; 0 ohne Datensätze).
     */
    fun lebende(c: Connection, konto: String): Pair<List<SyncRecord>, Long> {
        val records = c.prepareStatement("SELECT $columns FROM sync_record WHERE konto_id = ? AND deleted = 0").use { st ->
            st.setString(1, konto)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRecord()) } }
        }
        return records to hoechsteRevision(c, konto)
    }

    /** Höchste Revision des Kontos (0 ohne Datensätze) – ändert sich bei jedem Schreiben. */
    fun hoechsteRevision(c: Connection, konto: String): Long =
        c.prepareStatement("SELECT COALESCE(MAX(rev), 0) FROM sync_record WHERE konto_id = ?").use { st ->
            st.setString(1, konto)
            st.executeQuery().use { if (it.next()) it.getLong(1) else 0L }
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
