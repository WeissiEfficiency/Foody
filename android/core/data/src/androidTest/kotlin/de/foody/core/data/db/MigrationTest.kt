package de.foody.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.core.data.baueDatenbank
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 9 → 10 gegen die exportierten Schemata (9.json als Ausgangsstand, 10.json als Ziel). Die v9-Datenbank
 * bekommt Daten in allen Tabellen und Sync-Trigger wie auf dem Handy – samt Vorrats-Triggern und einem veralteten.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FoodyDatenbank::class.java)

    private val kontext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun neunAufZehn() {
        legeV9An()
        helper.runMigrationsAndValidate(DB, 10, true, MIGRATION_9_10).use { db ->
            assertFalse(tabelleExistiert(db, "pantry_item"))
            assertEquals(zeilenV9OhneVorrat, zeilenzahlen(db))
            assertEquals(0, zahl(db, "SELECT count(*) FROM sync_outbox WHERE type = 'pantry_item'"))
            assertEquals(
                listOf(listOf("ingredient", "i2", 1L, 2000L), listOf("recipe", "r1", 0L, 1000L)),
                zeilen(db, "SELECT type, recordId, deleted, queuedAt FROM sync_outbox ORDER BY type"),
            )
            assertEquals(listOf(listOf("recipe", "r1", 7L)), zeilen(db, "SELECT type, recordId, rev FROM sync_record_rev"))
            assertEquals(listOf(listOf("ingredient", "i1")), zeilen(db, "SELECT type, recordId FROM sync_problem"))
            // active, serverUrl, kontoId, cursor
            assertEquals(listOf(1, "https://foody.homeserver.lan", null, 413L), syncState(db))
            assertEquals(
                // v1-Kennung `unauthorized` wird zur V3-Kennung (MigrationLetzterFehlerTest prüft alle Werte).
                listOf(listOf(1L, 0L, 1_700_000_000_000L, "anmeldung_abgelaufen")),
                zeilen(db, "SELECT id, applyingRemote, lastSyncAt, lastError FROM sync_state"),
            )
            assertEquals(SyncTrigger.namen.toSet(), triggerNamen(db))
            // Alle Trigger tragen die Fassung von Version 10, keiner mehr die alte (CREATE TRIGGER IF NOT EXISTS!).
            assertEquals(0, zahl(db, "SELECT count(*) FROM sqlite_master WHERE type = 'trigger' AND sql LIKE '%v9alt%'"))
            // Nutzdaten unverändert
            assertEquals(
                listOf(listOf("r1", "Pfannkuchen", 4L, 1L, "https://example.org/pfannkuchen")),
                zeilen(db, "SELECT id, name, defaultServings, favorite, sourceUrl FROM recipe"),
            )
        }
    }

    @Test
    fun triggerWirkenNachMigration() {
        legeV9An()
        helper.runMigrationsAndValidate(DB, 10, true, MIGRATION_9_10).use { db ->
            db.execSQL("DELETE FROM sync_outbox")
            db.execSQL("UPDATE ingredient SET category = 'Backen' WHERE id = 'i1'")
            db.execSQL("INSERT INTO instruction_step (id, recipeId, position, text) VALUES ('s3', 'r1', 3, 'Servieren')")
            db.execSQL("DELETE FROM tagebuch_eintrag WHERE id = 't1'")
            assertEquals(
                listOf(listOf("ingredient", "i1", 0L), listOf("recipe", "r1", 0L), listOf("tagebuch_eintrag", "t1", 1L)),
                zeilen(db, "SELECT type, recordId, deleted FROM sync_outbox ORDER BY type"),
            )
            // Beim Anwenden von Server-Daten merkt kein Trigger etwas vor.
            db.execSQL("DELETE FROM sync_outbox")
            db.execSQL("UPDATE sync_state SET applyingRemote = 1")
            db.execSQL("UPDATE ingredient SET category = 'Milch' WHERE id = 'i2'")
            assertEquals(0, zahl(db, "SELECT count(*) FROM sync_outbox"))
        }
    }

    @Test
    fun aeltereVersionScheitert() {
        // Wie auf dem Handy: Die v1 (Room, JournalMode.AUTOMATIC) hat foody.db im WAL-Modus angelegt. Sonst stellt
        // Room beim Öffnen den Dateikopf auf WAL um – eine Änderung, die mit den Daten nichts zu tun hat.
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL("INSERT INTO recipe (id, name, defaultServings, tags, createdAt, updatedAt, version, favorite) VALUES ('r1', 'Suppe', 2, '', 1, 1, 1, 0)")
            db.query("PRAGMA journal_mode = WAL").use { c -> c.moveToFirst(); assertEquals("wal", c.getString(0)) }
        }
        val datei = kontext.getDatabasePath(DB)
        val vorher = pruefsummen(datei)

        val room = baueDatenbank(kontext, DB)
        try {
            val fehler = assertFailsWith<IllegalStateException> { room.openHelper.writableDatabase }
            assertTrue(fehler.message.orEmpty().contains("8"), fehler.message)
        } finally {
            room.close()
        }
        assertEquals(vorher, pruefsummen(datei))
    }

    /** v9-Datenbank wie auf dem Handy: Daten in allen Tabellen, Sync aktiv, Trigger der v1 (hier markiert). */
    private fun legeV9An() {
        helper.createDatabase(DB, 9).use { db ->
            listOf(
                "INSERT INTO ingredient (id, canonicalName, createdAt, updatedAt, version) VALUES ('i1', 'Mehl', 1, 1, 1)",
                "INSERT INTO ingredient (id, canonicalName, createdAt, updatedAt, version, barcode) VALUES ('i2', 'Milch', 1, 1, 1, '4001')",
                "INSERT INTO recipe (id, name, defaultServings, tags, createdAt, updatedAt, version, favorite, sourceUrl) " +
                    "VALUES ('r1', 'Pfannkuchen', 4, '', 1, 1, 1, 1, 'https://example.org/pfannkuchen')",
                "INSERT INTO recipe_ingredient (id, recipeId, ingredientId, amount, unit, sortOrder, optional) VALUES ('ri1', 'r1', 'i1', '250', 'G', 0, 0)",
                "INSERT INTO instruction_step (id, recipeId, position, text) VALUES ('s1', 'r1', 1, 'Verrühren')",
                "INSERT INTO instruction_step (id, recipeId, position, text) VALUES ('s2', 'r1', 2, 'Backen')",
                "INSERT INTO meal_slot (id, date, slotType, recipeId, servings, createdAt, updatedAt) VALUES ('m1', 20379, 'ABENDESSEN', 'r1', 2, 1, 1)",
                "INSERT INTO pantry_item (id, ingredientId, amount, unit, bestBeforeDate, updatedAt) VALUES ('p1', 'i1', '1000', 'G', NULL, 1)",
                "INSERT INTO pantry_item (id, ingredientId, amount, unit, bestBeforeDate, updatedAt) VALUES ('p2', 'i2', '1', 'L', 20400, 1)",
                "INSERT INTO shopping_list (id, name, generationVersion, createdAt, updatedAt) VALUES ('l1', 'Woche', 1, 1, 1)",
                "INSERT INTO shopping_item (id, listId, ingredientId, name, checked, manual, sortOrder, updatedAt, checkedChangedAt) " +
                    "VALUES ('e1', 'l1', 'i1', 'Mehl', 0, 0, 0, 5, 0)",
                "INSERT INTO shopping_item (id, listId, name, checked, manual, sortOrder, updatedAt, checkedChangedAt) " +
                    "VALUES ('e2', 'l1', 'Kerzen', 1, 1, 1, 6, 6)",
                "INSERT INTO shopping_item_source (id, shoppingItemId, mealSlotId, recipeIngredientId, recipeName, date, contributedAmount, unit) " +
                    "VALUES ('q1', 'e1', 'm1', 'ri1', 'Pfannkuchen', 20379, '250', 'G')",
                "INSERT INTO tagebuch_eintrag (id, datum, mahlzeit, art, name, vollstaendig, createdAt, updatedAt) " +
                    "VALUES ('t1', 20379, 'ABENDESSEN', 'REZEPT', 'Pfannkuchen', 1, 1, 1)",
                "INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('pantry_item', 'p1', 0, 500)",
                "INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('recipe', 'r1', 0, 1000)",
                "INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('ingredient', 'i2', 1, 2000)",
                "INSERT INTO sync_record_rev (type, recordId, rev) VALUES ('pantry_item', 'p1', 3)",
                "INSERT INTO sync_record_rev (type, recordId, rev) VALUES ('pantry_item', 'p2', 4)",
                "INSERT INTO sync_record_rev (type, recordId, rev) VALUES ('recipe', 'r1', 7)",
                "INSERT INTO sync_problem (type, recordId, code, at) VALUES ('pantry_item', 'p2', 'ungueltig', 9)",
                "INSERT INTO sync_problem (type, recordId, code, at) VALUES ('ingredient', 'i1', 'konflikt', 9)",
                "INSERT INTO sync_photo_local (uri, sha256, size, modifiedAt) VALUES ('file:///r1.jpg', 'abc', 3, 4)",
                "INSERT INTO sync_photo_wanted (recipeId, sha256) VALUES ('r1', 'def')",
                "INSERT INTO sync_state (id, active, applyingRemote, serverUrl, householdId, cursor, lastSyncAt, lastError) " +
                    "VALUES (1, 1, 0, 'https://foody.homeserver.lan', 'h1', 413, 1700000000000, 'unauthorized')",
            ).forEach(db::execSQL)
            v9Trigger().forEach(db::execSQL)
        }
    }

    /**
     * Trigger einer v9-Datenbank: dieselben Namen wie Version 10, dazu die Vorrats-Trigger und ein veralteter Name.
     * Jeder trägt die Markierung `v9alt`, damit der Test sieht, ob die Migration ihn wirklich ersetzt hat.
     */
    private fun v9Trigger(): List<String> {
        val markiert = SyncTrigger.anweisungen.map { it.replace(" BEGIN\n", " BEGIN\nSELECT 'v9alt';\n") }
        val vorrat = markiert.filter { Regex("""EXISTS sync_ingredient_a[iud] """).containsMatchIn(it) }
            .map { it.replace("ingredient", "pantry_item") }
        check(vorrat.size == 3)
        val veraltet = "CREATE TRIGGER sync_recipe_veraltet AFTER UPDATE ON recipe BEGIN\nSELECT 'v9alt';\nEND"
        return markiert + vorrat + veraltet
    }

    private val zeilenV9OhneVorrat = mapOf(
        "ingredient" to 2,
        "recipe" to 1,
        "recipe_ingredient" to 1,
        "instruction_step" to 2,
        "meal_slot" to 1,
        "shopping_list" to 1,
        "shopping_item" to 2,
        "shopping_item_source" to 1,
        "tagebuch_eintrag" to 1,
        "sync_outbox" to 2,
        "sync_record_rev" to 1,
        "sync_state" to 1,
        "sync_problem" to 1,
        "sync_photo_local" to 1,
        "sync_photo_wanted" to 1,
    )

    private fun tabelleExistiert(db: SupportSQLiteDatabase, name: String) =
        zahl(db, "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'") > 0

    /** Zeilenzahl je Nutzer- und Sync-Tabelle (ohne Verwaltungstabellen von Room/SQLite/Android). */
    private fun zeilenzahlen(db: SupportSQLiteDatabase): Map<String, Int> {
        val tabellen = zeilen(
            db,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata')",
        ).map { it.single() as String }
        return tabellen.associateWith { zahl(db, "SELECT count(*) FROM `$it`") }
    }

    private fun zahl(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

    private fun syncState(db: SupportSQLiteDatabase): List<Any?> =
        db.query("SELECT active, serverUrl, kontoId, cursor FROM sync_state WHERE id = 1").use { c ->
            c.moveToFirst()
            listOf(c.getInt(0), c.getString(1), if (c.isNull(2)) null else c.getString(2), c.getLong(3))
        }

    private fun triggerNamen(db: SupportSQLiteDatabase): Set<String> =
        zeilen(db, "SELECT name FROM sqlite_master WHERE type = 'trigger'").map { it.single() as String }.toSet()

    /** Alle Zeilen; Zahlen als Long, Text als String, NULL als null. */
    private fun zeilen(db: SupportSQLiteDatabase, sql: String): List<List<Any?>> =
        db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        (0 until c.columnCount).map { i ->
                            when (c.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> null
                                android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                else -> c.getString(i)
                            }
                        },
                    )
                }
            }
        }

    /** SHA-256 der Datenbankdatei und ihres WAL (falls vorhanden); `-shm` ist nur ein flüchtiger Index. */
    private fun pruefsummen(datei: File): Map<String, String> =
        listOf("", "-wal").map { File(datei.path + it) }.filter { it.exists() }.associate { f ->
            f.name to MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        }

    private companion object {
        const val DB = "migration-test"
    }
}
