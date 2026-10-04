package de.foody.app

import kotlin.test.assertTrue
import de.foody.app.data.db.MIGRATION_2_3
import de.foody.app.data.db.MIGRATION_3_4
import de.foody.app.data.db.MIGRATION_4_5
import de.foody.app.data.db.MIGRATION_5_6
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.MIGRATION_1_2
import de.foody.app.data.db.SyncTriggers
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

    @Test fun migrate2To3AddsItemNoteAndRecipeRatingWithoutTouchingData() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO recipe (id, name, defaultServings, prepMinutes, cookMinutes, imageUri, notes, tags, archivedAt, createdAt, updatedAt, version, favorite, sourceUrl) " +
                    "VALUES ('r', 'Suppe', 4, NULL, NULL, NULL, NULL, '', NULL, 0, 0, 1, 1, NULL)",
            )
            db.execSQL("INSERT INTO shopping_list (id, name, rangeStart, rangeEnd, generationVersion, createdAt, updatedAt) VALUES ('l', 'Liste', NULL, NULL, 1, 0, 0)")
            db.execSQL(
                "INSERT INTO shopping_item (id, listId, ingredientId, name, amount, unit, checked, manual, category, sortOrder) " +
                    "VALUES ('i', 'l', NULL, 'Milch', NULL, NULL, 0, 1, NULL, 0)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT favorite, rating FROM recipe WHERE id = 'r'").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0)); assertTrue(c.isNull(1))
            }
            db.query("SELECT name, note FROM shopping_item WHERE id = 'i'").use { c ->
                c.moveToFirst(); assertEquals("Milch", c.getString(0)); assertTrue(c.isNull(1))
            }
        }
    }

    @Test fun migrate3To4KeepsShoppingItems() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO shopping_list (id, name, rangeStart, rangeEnd, generationVersion, createdAt, updatedAt) VALUES ('l', 'Liste', NULL, NULL, 1, 0, 0)")
            for ((id, checked) in listOf("a" to 0, "b" to 1)) {
                db.execSQL(
                    "INSERT INTO shopping_item (id, listId, ingredientId, name, amount, unit, checked, manual, category, sortOrder, note) " +
                        "VALUES ('$id', 'l', NULL, 'Artikel $id', NULL, NULL, $checked, 1, NULL, 0, NULL)",
                )
            }
        }
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT id, checked, updatedAt, checkedChangedAt FROM shopping_item ORDER BY id").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst(); assertEquals("a", c.getString(0)); assertEquals(0, c.getInt(1)); assertEquals(0L, c.getLong(2)); assertEquals(0L, c.getLong(3))
                c.moveToNext(); assertEquals("b", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(0L, c.getLong(2)); assertEquals(0L, c.getLong(3))
            }
            db.query("SELECT id, active, applyingRemote FROM sync_state").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst(); assertEquals(1, c.getInt(0)); assertEquals(0, c.getInt(1)); assertEquals(0, c.getInt(2))
            }
            db.query("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").use { c ->
                c.moveToFirst(); assertTrue(c.getInt(0) > 0)
            }
        }
    }

    @Test fun migrate4To5RecreatesTriggers() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL("INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('recipe', 'r', 0, 1234)")
        }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT type, recordId, deleted, queuedAt FROM sync_outbox").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst(); assertEquals("recipe", c.getString(0)); assertEquals("r", c.getString(1)); assertEquals(0, c.getInt(2)); assertEquals(1234L, c.getLong(3))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").use { c ->
                val names = buildSet { while (c.moveToNext()) add(c.getString(0)) }
                assertEquals(SyncTriggers.names.toSet(), names)
            }
            db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name = 'sync_ingredient_au'").use { c ->
                c.moveToFirst(); assertTrue(c.getString(0).contains("queuedAt + 1"))
            }
        }
    }

    @Test fun migrate5To6AddsPhotoTables() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL("INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('recipe', 'r', 0, 1234)")
        }
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT count(*) FROM sync_outbox").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.execSQL("INSERT INTO sync_photo_local (uri, sha256, size, modifiedAt) VALUES ('file:///a.jpg', 'abc', 3, 4)")
            db.execSQL("INSERT INTO sync_photo_wanted (recipeId, sha256) VALUES ('r', 'abc')")
            db.query("SELECT count(*) FROM sqlite_master WHERE type='index' AND name = 'index_sync_photo_local_sha256'").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").use { c ->
                val names = buildSet { while (c.moveToNext()) add(c.getString(0)) }
                assertEquals(SyncTriggers.names.toSet(), names)
                assertTrue("sync_recipe_image_wish" in names)
            }
        }
    }
}
