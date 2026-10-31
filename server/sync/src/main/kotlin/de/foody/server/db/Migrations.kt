package de.foody.server.db

import java.sql.Connection

/**
 * Migrationsliste über `PRAGMA user_version` (wie Room): Eintrag *n* hebt die Version von *n* auf *n+1*.
 * Jeder Eintrag enthält mehrere durch `;` getrennte Anweisungen (deshalb kein `;` in Literalen oder Triggern).
 *
 * Eine neue Datenbank bekommt in einem Schritt das [GRUNDSCHEMA] in Version [GRUNDVERSION] (Konten). Die Umwandlung
 * der Version 1 (Haushalte, v1-Server) gibt es seit dem Schließen des Rückwegs nicht mehr; ihr Platz in der Liste
 * bleibt leer, damit die Nummern stimmen.
 */
object Migrations {
    /** Version, mit der neue Datenbanken beginnen. */
    const val GRUNDVERSION = 2

    /** Schema in Version [GRUNDVERSION]: Konto = Zeile in `user`, Datensätze und Geräte hängen am Konto. */
    private val GRUNDSCHEMA = """
        CREATE TABLE user (
            id TEXT PRIMARY KEY,
            username TEXT NOT NULL,
            username_lower TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            display_name TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_rev INTEGER NOT NULL DEFAULT 0,
            compacted_before_rev INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE device (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            revoked_at INTEGER
        );
        CREATE TABLE sync_record (
            konto_id TEXT NOT NULL,
            type TEXT NOT NULL,
            id TEXT NOT NULL,
            rev INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            payload TEXT,
            updated_at INTEGER NOT NULL,
            deleted_at INTEGER,
            canonical_name TEXT,
            PRIMARY KEY (konto_id, type, id)
        );
        CREATE INDEX idx_sync_record_rev ON sync_record (konto_id, rev);
        CREATE INDEX idx_sync_record_name ON sync_record (konto_id, type, canonical_name)
    """.trimIndent()

    val all: List<String> = listOf(
        // 0 → 2
        GRUNDSCHEMA,
        // 1 → 2: Haushalt → Konto, entfallen
        "",
        // 2 → 3: Bereich verlorener Revisionen nach dem Einspielen einer Sicherung (`foody-admin wiederhergestellt`)
        """
        ALTER TABLE user ADD COLUMN verloren_nach INTEGER;
        ALTER TABLE user ADD COLUMN verloren_vor INTEGER
        """.trimIndent(),
        // 3 → 4: Rest der Umwandlung auf Konten; die Fotoordner sind umbenannt (Vorprüfung in [vorpruefen])
        "DROP TABLE IF EXISTS haushalt_umzug",
    )

    /** Aktuelle Schemaversion (`PRAGMA user_version`). */
    fun aktuell(c: Connection): Int = c.createStatement().use { s ->
        s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
    }

    /**
     * Wendet alle noch fehlenden Migrationen aus [liste] an, jede in einer eigenen Transaktion. Ist die Datenbank
     * neuer, als [liste] reicht (Rückweg auf einen älteren Server), bricht der Start ab, statt mit einem Schema zu
     * arbeiten, das dieser Server nicht kennt; ebenso bei einer Datenbank des v1-Servers (Version 1).
     */
    fun apply(c: Connection, liste: List<String> = all) {
        val current = aktuell(c)
        if (current > liste.size) {
            throw MigrationAbgebrochen("Datenbank hat Version $current, dieser Server kennt nur ${liste.size} – neueren Server starten")
        }
        if (current in 1 until GRUNDVERSION) {
            throw MigrationAbgebrochen(
                "Datenbank hat Version $current (Haushalte, v1-Server) – die Umwandlung auf Konten gibt es nicht mehr " +
                    "(Stand vor dem Tag v1-final nehmen)",
            )
        }
        var index = current
        while (index < liste.size) {
            // Das Grundschema springt direkt auf GRUNDVERSION
            val ziel = if (index == 0) GRUNDVERSION else index + 1
            val wasAuto = c.autoCommit
            c.autoCommit = false
            try {
                vorpruefen(c, index)
                c.createStatement().use { s ->
                    liste[index].split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { s.execute(it) }
                    s.execute("PRAGMA user_version = $ziel")
                }
                c.commit()
            } catch (e: Throwable) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = wasAuto
            }
            index = ziel
        }
    }

    /**
     * Vor Migration 3 → 4: Stehen in `haushalt_umzug` noch Einträge, sind Fotoordner nicht umbenannt; dann
     * [MigrationAbgebrochen] statt die Tabelle zu löschen.
     */
    private fun vorpruefen(c: Connection, index: Int) {
        if (index != 3) return
        val offen = c.createStatement().use { s ->
            val da = s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'haushalt_umzug'")
                .use { it.next(); it.getInt(1) > 0 }
            if (da) s.executeQuery("SELECT COUNT(*) FROM haushalt_umzug").use { it.next(); it.getInt(1) } else 0
        }
        if (offen > 0) throw MigrationAbgebrochen("$offen Fotoordner aus der Umwandlung auf Konten noch nicht umbenannt – abgebrochen")
    }
}

/** Eine Migration wurde vor jeder Änderung abgebrochen (Sicherung fehlgeschlagen oder Vorprüfung nicht bestanden). */
class MigrationAbgebrochen(message: String) : IllegalStateException(message)
