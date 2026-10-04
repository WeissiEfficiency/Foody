package de.foody.server.db

import java.sql.Connection

/**
 * Migrationsliste über `PRAGMA user_version` (wie Room): Eintrag *n* hebt die Version von *n* auf *n+1*.
 * Jeder Eintrag enthält mehrere durch `;` getrennte Anweisungen.
 */
object Migrations {
    val all: List<String> = listOf(
        """
        CREATE TABLE user (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL,
            username_lower TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            display_name TEXT NOT NULL,
            is_admin INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );
        CREATE TABLE household (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            created_by TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_rev INTEGER NOT NULL DEFAULT 0,
            compacted_before_rev INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE membership (
            user_id TEXT NOT NULL,
            household_id TEXT NOT NULL,
            role TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (user_id, household_id)
        );
        CREATE TABLE device (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            household_id TEXT,
            name TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            revoked_at INTEGER
        );
        CREATE TABLE invite (
            id TEXT PRIMARY KEY,
            code_hash TEXT NOT NULL UNIQUE,
            household_id TEXT,
            created_by TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            used_at INTEGER
        );
        CREATE TABLE sync_record (
            household_id TEXT NOT NULL,
            type TEXT NOT NULL,
            id TEXT NOT NULL,
            rev INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            payload TEXT,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            canonical_name TEXT,
            PRIMARY KEY (household_id, type, id)
        );
        CREATE INDEX idx_sync_record_rev ON sync_record (household_id, rev);
        CREATE INDEX idx_sync_record_name ON sync_record (household_id, type, canonical_name)
        """.trimIndent(),
    )

    /** Wendet alle noch fehlenden Migrationen an, jede in einer eigenen Transaktion. */
    fun apply(c: Connection) {
        val current = c.createStatement().use { s ->
            s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
        }
        for (index in current until all.size) {
            val wasAuto = c.autoCommit
            c.autoCommit = false
            try {
                c.createStatement().use { s ->
                    all[index].split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { s.execute(it) }
                    s.execute("PRAGMA user_version = ${index + 1}")
                }
                c.commit()
            } catch (e: Throwable) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = wasAuto
            }
        }
    }
}
