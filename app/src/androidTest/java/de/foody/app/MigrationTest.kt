package de.foody.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Schema-Migrationen gegen die exportierten Schemata in app/schemas (Pflicht laut docs/architecture.md). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FoodyDatabase::class.java)

    @Test fun migrate1To2AddsFavoriteAndBackfillsSourceUrlFromNotes() {
        helper.createDatabase(dbName, 1).use { db ->
            fun insert(id: String, notes: String?) = db.execSQL(
                "INSERT INTO recipe (id, name, defaultServings, prepMinutes, cookMinutes, imageUri, notes, tags, archivedAt, createdAt, updatedAt, version) " +
                    "VALUES (?, ?, 4, NULL, NULL, NULL, ?, '', NULL, 0, 0, 1)",
                arrayOf(id, "Rezept $id", notes),
            )
            insert("imported", "Quelle: https://example.org/r/1.html\nPortionenzahl war in der Datei nicht angegeben.")
            insert("single-line", "Quelle: https://example.org/r/2.html")
            insert("manual", "Meine Notiz")
            insert("empty", null)
        }

        // validateDroppedTables = true: Schema muss exakt dem exportierten 2.json entsprechen
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT id, favorite, sourceUrl FROM recipe ORDER BY id").use { c ->
                val rows = buildMap {
                    while (c.moveToNext()) put(c.getString(0), c.getInt(1) to (if (c.isNull(2)) null else c.getString(2)))
                }
                assertEquals(0 to null, rows["empty"])
                assertEquals(0 to "https://example.org/r/1.html", rows["imported"])
                assertEquals(0 to null, rows["manual"])
                assertEquals(0 to "https://example.org/r/2.html", rows["single-line"])
            }
            db.query("SELECT COUNT(*) FROM recipe WHERE favorite IS NULL").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
