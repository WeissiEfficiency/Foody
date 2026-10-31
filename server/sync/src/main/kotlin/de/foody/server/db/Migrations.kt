package de.foody.server.db

import java.sql.Connection

/**
 * Migrationsliste über `PRAGMA user_version` (wie Room): Eintrag *n* hebt die Version von *n* auf *n+1*.
 * Jeder Eintrag enthält mehrere durch `;` getrennte Anweisungen (deshalb kein `;` in Literalen oder Triggern).
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
        // 1 → 2: Konto statt Haushalt. Voraussetzung (siehe vorpruefenKonto): je Haushalt genau ein Mitglied.
        // Zähler und Datensätze gehen aufs Konto, Vorrat (pantry_item) entfällt, Geräte verlieren den Haushalt.
        // haushalt_umzug merkt sich die Fotoordner, die noch umbenannt werden müssen.
        """
        ALTER TABLE user ADD COLUMN last_rev INTEGER NOT NULL DEFAULT 0;
        ALTER TABLE user ADD COLUMN compacted_before_rev INTEGER NOT NULL DEFAULT 0;
        UPDATE user SET
            last_rev = (SELECT h.last_rev FROM household h JOIN membership m ON m.household_id = h.id WHERE m.user_id = user.id),
            compacted_before_rev = (SELECT h.compacted_before_rev FROM household h JOIN membership m ON m.household_id = h.id WHERE m.user_id = user.id)
            WHERE id IN (SELECT m.user_id FROM membership m JOIN household h ON h.id = m.household_id);
        CREATE TABLE sync_record_neu (
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
        INSERT INTO sync_record_neu (konto_id, type, id, rev, deleted, payload, updated_at, deleted_at, canonical_name)
            SELECT m.user_id, r.type, r.id, r.rev, r.deleted, r.payload, r.updated_at, r.deleted_at, r.canonical_name
            FROM sync_record r
            JOIN household h ON h.id = r.household_id
            JOIN membership m ON m.household_id = r.household_id
            WHERE r.type <> 'pantry_item';
        DROP TABLE sync_record;
        ALTER TABLE sync_record_neu RENAME TO sync_record;
        CREATE INDEX idx_sync_record_rev ON sync_record (konto_id, rev);
        CREATE INDEX idx_sync_record_name ON sync_record (konto_id, type, canonical_name);
        CREATE TABLE device_neu (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            revoked_at INTEGER
        );
        INSERT INTO device_neu (id, user_id, name, token_hash, created_at, last_seen_at, revoked_at)
            SELECT id, user_id, name, token_hash, created_at, last_seen_at, revoked_at FROM device;
        DROP TABLE device;
        ALTER TABLE device_neu RENAME TO device;
        CREATE TABLE haushalt_umzug (
            household_id TEXT PRIMARY KEY,
            konto_id TEXT NOT NULL
        );
        INSERT INTO haushalt_umzug (household_id, konto_id)
            SELECT m.household_id, m.user_id FROM membership m JOIN household h ON h.id = m.household_id;
        DROP TABLE invite;
        DROP TABLE membership;
        DROP TABLE household;
        ALTER TABLE user DROP COLUMN is_admin
        """.trimIndent(),
        // 2 → 3: Bereich verlorener Revisionen nach dem Einspielen einer Sicherung (`foody-admin wiederhergestellt`)
        """
        ALTER TABLE user ADD COLUMN verloren_nach INTEGER;
        ALTER TABLE user ADD COLUMN verloren_vor INTEGER
        """.trimIndent(),
    )

    /**
     * Vorprüfung für Migration 1 → 2: Jedes Mitglied zeigt auf ein existierendes Konto, jeder Haushalt hat genau ein
     * Mitglied, kein Konto ist Mitglied mehrerer Haushalte, und jeder Datensatz gehört zu einem existierenden Haushalt.
     * Sonst [MigrationAbgebrochen] – vor jeder Änderung, die Datenbank bleibt auf Version 1.
     */
    fun vorpruefenKonto(c: Connection) {
        c.createStatement().use { s ->
            s.executeQuery(
                "SELECT m.household_id, m.user_id FROM membership m LEFT JOIN user u ON u.id = m.user_id " +
                    "WHERE u.id IS NULL ORDER BY m.household_id, m.user_id LIMIT 1",
            ).use { rs ->
                if (rs.next()) {
                    throw MigrationAbgebrochen(
                        "Haushalt ${rs.getString(1)} hat ein Mitglied ohne Konto (${rs.getString(2)}) – Umwandlung auf Konten abgebrochen",
                    )
                }
            }
            s.executeQuery(
                "SELECT h.id, (SELECT COUNT(*) FROM membership m JOIN user u ON u.id = m.user_id WHERE m.household_id = h.id) " +
                    "FROM household h " +
                    "WHERE (SELECT COUNT(*) FROM membership m JOIN user u ON u.id = m.user_id WHERE m.household_id = h.id) <> 1 " +
                    "ORDER BY h.id LIMIT 1",
            ).use { rs ->
                if (rs.next()) {
                    throw MigrationAbgebrochen("Haushalt ${rs.getString(1)} hat ${rs.getInt(2)} Mitglieder – Umwandlung auf Konten abgebrochen")
                }
            }
            s.executeQuery(
                "SELECT m.user_id, COUNT(*) FROM membership m JOIN household h ON h.id = m.household_id " +
                    "JOIN user u ON u.id = m.user_id GROUP BY m.user_id HAVING COUNT(*) > 1 ORDER BY m.user_id LIMIT 1",
            ).use { rs ->
                if (rs.next()) {
                    throw MigrationAbgebrochen(
                        "Konto ${rs.getString(1)} ist Mitglied von ${rs.getInt(2)} Haushalten – Umwandlung auf Konten abgebrochen",
                    )
                }
            }
            val verwaist = s.executeQuery(
                "SELECT household_id, COUNT(*) FROM sync_record WHERE household_id NOT IN (SELECT id FROM household) " +
                    "GROUP BY household_id ORDER BY household_id",
            ).use { rs -> buildList { while (rs.next()) add(rs.getString(1) to rs.getLong(2)) } }
            if (verwaist.isNotEmpty()) {
                val ids = verwaist.take(MAX_GENANNT).joinToString(", ") { it.first } + if (verwaist.size > MAX_GENANNT) ", …" else ""
                throw MigrationAbgebrochen(
                    "${verwaist.sumOf { it.second }} Datensätze ohne existierenden Haushalt ($ids) – Umwandlung auf Konten abgebrochen",
                )
            }
        }
    }

    /** Höchstens so viele Haushalts-IDs nennt die Meldung der Vorprüfung. */
    private const val MAX_GENANNT = 5

    /** Datensätze, die Migration 1 → 2 übernehmen muss (alle außer Vorrat); Grundlage des Gegenchecks. */
    private fun zuUebernehmen(c: Connection): Long = c.createStatement().use { s ->
        s.executeQuery("SELECT COUNT(*) FROM sync_record WHERE type <> 'pantry_item'").use { it.next(); it.getLong(1) }
    }

    /** Gegencheck nach Migration 1 → 2, noch in der Transaktion: keine Datensätze verloren oder hinzugekommen. */
    private fun gegenpruefenKonto(c: Connection, vorher: Long) {
        val nachher = c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM sync_record").use { it.next(); it.getLong(1) } }
        if (nachher != vorher) {
            throw MigrationAbgebrochen("$vorher Datensätze vorher, $nachher nachher – Umwandlung abgebrochen")
        }
    }

    /** Aktuelle Schemaversion (`PRAGMA user_version`). */
    fun aktuell(c: Connection): Int = c.createStatement().use { s ->
        s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
    }

    /**
     * Wendet alle noch fehlenden Migrationen aus [liste] an, jede in einer eigenen Transaktion. Ist die Datenbank
     * neuer, als [liste] reicht (Rückweg auf einen älteren Server), bricht der Start ab, statt mit einem Schema zu
     * arbeiten, das dieser Server nicht kennt.
     */
    fun apply(c: Connection, liste: List<String> = all) {
        val current = aktuell(c)
        if (current > liste.size) {
            throw MigrationAbgebrochen("Datenbank hat Version $current, dieser Server kennt nur ${liste.size} – neueren Server starten")
        }
        for (index in current until liste.size) {
            val wasAuto = c.autoCommit
            c.autoCommit = false
            try {
                val vorher = if (index == 1) {
                    vorpruefenKonto(c)
                    zuUebernehmen(c)
                } else {
                    null
                }
                c.createStatement().use { s ->
                    liste[index].split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { s.execute(it) }
                    s.execute("PRAGMA user_version = ${index + 1}")
                }
                if (vorher != null) gegenpruefenKonto(c, vorher)
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

/** Eine Migration wurde vor jeder Änderung abgebrochen (Sicherung fehlgeschlagen oder Vorprüfung nicht bestanden). */
class MigrationAbgebrochen(message: String) : IllegalStateException(message)
