package de.foody.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import de.foody.core.data.baueDatenbank
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

/**
 * Migration 9 → 10 unter Robolectric: `lastError` der v1 wird auf die festen Kennungen der V3 abgebildet, alles andere
 * wird `NULL` (der vollständige Migrationstest läuft auf dem Emulator, `MigrationTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class MigrationLetzterFehlerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    /** Leere v9-Datenbank aus dem exportierten Schema 9 mit `sync_state.lastError = [letzterFehler]`. */
    private fun legeV9An(letzterFehler: String?) {
        context.deleteDatabase(DB)
        val datenbank = Json.parseToJsonElement(File(SCHEMA_9).readText()).jsonObject["database"]!!.jsonObject
        val datei = context.getDatabasePath(DB).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(datei, null).use { db ->
            for (e in datenbank["entities"]!!.jsonArray.map { it.jsonObject }) {
                val tabelle = e["tableName"]!!.jsonPrimitive.content
                db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tabelle))
                for (i in (e["indices"] as? JsonArray).orEmpty()) {
                    db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tabelle))
                }
            }
            for (q in datenbank["setupQueries"]!!.jsonArray) db.execSQL(q.jsonPrimitive.content)
            db.execSQL(
                "INSERT INTO sync_state (id, active, applyingRemote, serverUrl, householdId, cursor, lastSyncAt, lastError) " +
                    "VALUES (1, 1, 0, 'https://foody.example', 'h1', 7, 1, ?)",
                arrayOf(letzterFehler),
            )
            db.version = 9
        }
    }

    @Test
    fun lastErrorWirdAbgebildet() = runTest {
        val erwartet = mapOf(
            "unauthorized" to "anmeldung_abgelaufen",
            "protocol_too_old" to "app_zu_alt",
            "protocol_server_too_old" to "server_zu_alt",
            "no_household" to null,
            "transient: Zeitüberschreitung" to null,
            "failed: IOException" to null,
            "cursor_stuck" to null,
            null to null,
        )
        for ((v1, v3) in erwartet) {
            legeV9An(v1)
            val db = baueDatenbank(context, DB)
            try {
                val z = db.syncDao().zustand()!!
                assertEquals(v3, z.lastError, "v1-lastError $v1")
                assertEquals(7, z.cursor, "Cursor bleibt")
            } finally {
                db.close()
            }
        }
    }

    private companion object {
        const val DB = "foody-v9-fehler-test.db"

        /** Exportiertes Schema 9 (Arbeitsverzeichnis der Tests ist das Modul `core/data`). */
        const val SCHEMA_9 = "schemas/de.foody.core.data.db.FoodyDatenbank/9.json"
    }
}
