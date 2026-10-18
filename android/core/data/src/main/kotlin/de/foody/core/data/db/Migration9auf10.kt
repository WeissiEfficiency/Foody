package de.foody.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Datensatztyp und Tabelle des Vorrats der v1; in Version 10 entfällt beides. */
private const val VORRAT = "pantry_item"

/**
 * Migration 9 → 10: wandelt die v1-Datenbank auf dem Handy in die V3-Datenbank um (gleiche App-ID, kein Neuinstallieren).
 * Läuft komplett in Rooms Migrationstransaktion – scheitert ein Schritt, bleibt die v9-Datenbank unverändert.
 *
 * 1. Alle Sync-Trigger (`sync_*`, auch die des Vorrats und veraltete Namen) und die Tabelle `pantry_item` entfernen.
 *    Die Trigger gehen vor Schritt 3, weil sie `sync_state` lesen: Ab SQLite 3.25 prüft `ALTER TABLE … RENAME` alle
 *    Trigger und scheitert an Verweisen auf eine gerade gelöschte Tabelle.
 * 2. Vorrats-Zeilen aus `sync_outbox`, `sync_record_rev` und `sync_problem` löschen; alle anderen ungesendeten
 *    Änderungen bleiben in der Outbox.
 * 3. `sync_state` mit `kontoId` statt `householdId` neu anlegen (Spalten exakt wie Schema 10), Werte übernehmen,
 *    `kontoId = NULL` (Haushalts- ≠ Konto-ID). Cursor und Aktivierung bleiben → kein Voll-Abgleich.
 * 4. Die Sync-Trigger von Version 10 anlegen.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1.
        val alteTrigger = db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync\\_%' ESCAPE '\\'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        (alteTrigger + listOf("sync_${VORRAT}_ai", "sync_${VORRAT}_au", "sync_${VORRAT}_ad") + SyncTrigger.namen)
            .distinct()
            .forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }
        db.execSQL("DROP TABLE IF EXISTS `$VORRAT`")

        // 2.
        for (tabelle in listOf("sync_outbox", "sync_record_rev", "sync_problem")) {
            db.execSQL("DELETE FROM `$tabelle` WHERE type = '$VORRAT'")
        }

        // 3. Spaltendefinition wie createSql von sync_state in schemas/…/10.json
        db.execSQL(
            "CREATE TABLE `sync_state_neu` (`id` INTEGER NOT NULL, `active` INTEGER NOT NULL DEFAULT 0, " +
                "`applyingRemote` INTEGER NOT NULL DEFAULT 0, `serverUrl` TEXT, `kontoId` TEXT, " +
                "`cursor` INTEGER NOT NULL DEFAULT 0, `lastSyncAt` INTEGER, `lastError` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `sync_state_neu` (id, active, applyingRemote, serverUrl, kontoId, cursor, lastSyncAt, lastError) " +
                "SELECT id, active, applyingRemote, serverUrl, NULL, cursor, lastSyncAt, lastError FROM `sync_state`",
        )
        db.execSQL("DROP TABLE `sync_state`")
        db.execSQL("ALTER TABLE `sync_state_neu` RENAME TO `sync_state`")
        // Fehlte die Zeile (sollte es in v9 nie), entsteht sie wie bei einer neuen Datenbank – Sync inaktiv.
        db.execSQL("INSERT OR IGNORE INTO sync_state(id) VALUES (1)")

        // 4.
        SyncTrigger.anlegen(db)
    }
}
