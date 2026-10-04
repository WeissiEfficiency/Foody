package de.foody.app.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.net.URLClassLoader
import java.sql.Driver
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prüft die Sync-Trigger gegen SQLite 3.18 (Stand von Android API 26, minSdk): Die Anweisungen müssen dort
 * parsen (kein UPSERT, kein TRUE) und unter `INSERT OR ABORT` (Rooms `@Insert`) ohne Konflikt laufen.
 */
class SyncTriggersSqliteCompatTest {
    private lateinit var conn: Connection
    private lateinit var loader: URLClassLoader

    private val tables = listOf(
        "ingredient", "recipe", "recipe_ingredient", "instruction_step", "meal_slot", "pantry_item",
        "shopping_list", "shopping_item", "shopping_item_source", "sync_outbox", "sync_record_rev", "sync_state", "sync_problem", "sync_photo_wanted",
    )

    @Before fun setUp() {
        // Eigener ClassLoader (Eltern: nur Plattform), damit die neuere sqlite-jdbc aus :server nicht dazwischenfunkt.
        loader = URLClassLoader(arrayOf(File(checkNotNull(System.getProperty("foody.legacySqliteJar"))).toURI().toURL()), Driver::class.java.classLoader)
        val driver = loader.loadClass("org.sqlite.JDBC").getDeclaredConstructor().newInstance() as Driver
        conn = driver.connect("jdbc:sqlite::memory:", Properties())
        val schema = Json.parseToJsonElement(
            File("schemas/de.foody.app.data.db.FoodyDatabase/6.json").readText(),
        ).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        exec("PRAGMA foreign_keys = ON")
        exec("PRAGMA recursive_triggers = 1")
        for (name in tables) {
            val entity = schema.first { it.str("tableName") == name }
            exec(entity.str("createSql").replace("\${TABLE_NAME}", name))
            entity["indices"]?.jsonArray?.forEach { exec(it.jsonObject.str("createSql").replace("\${TABLE_NAME}", name)) }
        }
        exec("INSERT INTO sync_state(id) VALUES (1)")
        SyncTriggers.statements.forEach(::exec)
        exec("UPDATE sync_state SET active = 1")
    }

    @After fun tearDown() {
        conn.close()
        loader.close()
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun exec(sql: String) = conn.createStatement().use { it.execute(sql) }

    private fun outbox(): Map<Pair<String, String>, Int> = conn.createStatement().use { st ->
        st.executeQuery("SELECT type, recordId, deleted FROM sync_outbox").use { rs ->
            buildMap { while (rs.next()) put(rs.getString(1) to rs.getString(2), rs.getInt(3)) }
        }
    }

    @Test fun runsOnLegacySqlite() {
        val version = conn.createStatement().use { it.executeQuery("select sqlite_version()").use { rs -> rs.next(); rs.getString(1) } }
        assertTrue(version.startsWith("3.18"), "SQLite-Version $version")
    }

    @Test fun triggersWorkUnderAbortPolicy() {
        exec("INSERT OR ABORT INTO ingredient(id, canonicalName, createdAt, updatedAt, version) VALUES ('i', 'Salz', 0, 0, 1)")
        exec("INSERT OR ABORT INTO recipe(id, name, defaultServings, tags, createdAt, updatedAt, version) VALUES ('r', 'R', 2, '', 0, 0, 1)")
        for (n in 1..2) {
            exec("INSERT OR ABORT INTO recipe_ingredient(id, recipeId, ingredientId, amount, unit, sortOrder, optional) VALUES ('l$n', 'r', 'i', '1', 'GRAM', $n, 0)")
        }
        exec("UPDATE recipe SET name = 'R2' WHERE id = 'r'")
        exec("INSERT OR ABORT INTO shopping_list(id, name, generationVersion, createdAt, updatedAt) VALUES ('sl', 'L', 1, 0, 0)")
        exec("INSERT OR ABORT INTO shopping_item(id, listId, name, checked, manual, sortOrder) VALUES ('si', 'sl', 'Milch', 0, 1, 0)")
        exec("UPDATE shopping_item SET checked = 1 WHERE id = 'si'")
        assertEquals(
            mapOf(("ingredient" to "i") to 0, ("recipe" to "r") to 0, ("shopping_list" to "sl") to 0, ("shopping_item" to "si") to 0),
            outbox(),
        )
        val stamps = conn.createStatement().use {
            it.executeQuery("SELECT updatedAt, checkedChangedAt FROM shopping_item WHERE id = 'si'").use { rs -> rs.next(); rs.getLong(1) to rs.getLong(2) }
        }
        assertTrue(stamps.first > 0 && stamps.second > 0, "Zeitstempel gepflegt: $stamps")

        exec("DELETE FROM recipe WHERE id = 'r'") // Kaskade auf recipe_ingredient: darf das Rezept nicht wiederbeleben
        assertEquals(1, outbox()[("recipe" to "r")])
    }

    @Test fun requeueAdvancesQueuedAtStrictly() {
        exec("INSERT OR ABORT INTO ingredient(id, canonicalName, createdAt, updatedAt, version) VALUES ('i', 'Salz', 0, 0, 1)")
        val future = System.currentTimeMillis() + 1_000_000_000L
        exec("UPDATE sync_outbox SET queuedAt = $future WHERE type = 'ingredient' AND recordId = 'i'")
        exec("UPDATE ingredient SET canonicalName = 'Salz2' WHERE id = 'i'")
        assertEquals(future + 1, queuedAt("i"))
        exec("UPDATE ingredient SET canonicalName = 'Salz3' WHERE id = 'i'")
        assertEquals(future + 2, queuedAt("i"))
        // Normalfall: liegt die Uhr vorn, gewinnt sie (kein Zurückfallen auf alt + 1)
        exec("UPDATE sync_outbox SET queuedAt = 5 WHERE type = 'ingredient' AND recordId = 'i'")
        exec("UPDATE ingredient SET canonicalName = 'Salz4' WHERE id = 'i'")
        assertTrue(queuedAt("i") > 1_000_000_000_000L, "Uhrzeit gewinnt: ${queuedAt("i")}")
    }

    @Test fun localImageChangeDropsWishRemoteDoesNot() {
        exec("INSERT OR ABORT INTO recipe(id, name, defaultServings, tags, createdAt, updatedAt, version, favorite) VALUES ('r', 'R', 1, '', 0, 0, 1, 0)")
        exec("INSERT INTO sync_photo_wanted(recipeId, sha256) VALUES ('r', 'h')")
        exec("UPDATE sync_state SET applyingRemote = 1")
        exec("UPDATE recipe SET imageUri = 'file:/a.jpg' WHERE id = 'r'")
        assertEquals(1, wishes())
        exec("UPDATE sync_state SET applyingRemote = 0")
        exec("UPDATE recipe SET name = 'X' WHERE id = 'r'") // anderes Feld: Wunsch bleibt
        assertEquals(1, wishes())
        exec("UPDATE recipe SET imageUri = 'file:/b.jpg' WHERE id = 'r'")
        assertEquals(0, wishes())
    }

    private fun wishes(): Int = conn.createStatement().use { st ->
        st.executeQuery("SELECT count(*) FROM sync_photo_wanted").use { rs -> rs.next(); rs.getInt(1) }
    }

    private fun queuedAt(id: String): Long = conn.createStatement().use { st ->
        st.executeQuery("SELECT queuedAt FROM sync_outbox WHERE type = 'ingredient' AND recordId = '$id'").use { rs -> rs.next(); rs.getLong(1) }
    }
}
