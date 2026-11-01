package de.foody.core.data.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.core.data.baueDatenbank
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Migration 9 → 10 mit einer echten v1-Datenbank (Kopie vom Handy). Läuft nur mit Instrumentation-Argumenten, sonst
 * kehrt er sofort zurück (gilt dann als bestanden). Ist nur eines der beiden gesetzt oder die Datei nicht lesbar, scheitert er:
 * - `echteDb`: absoluter Pfad der `foody.db` auf dem Testgerät, für die Test-App lesbar (z. B. unter
 *   `/sdcard/Android/data/de.foody.core.data.test/files/`); ein danebenliegendes `foody.db-wal`/`-shm` wird mitkopiert.
 * - `echteDbZahlen`: erwartete Zeilenzahlen nach der Migration, `tabelle=zahl+…` (`+` oder `,`; über Gradle nur `+`: die Instrumentierung schneidet Kommas ab, `;` trennt in der Geräte-Shell). `pantry_item` wird ignoriert; für
 *   `sync_outbox`, `sync_record_rev` und `sync_problem` die Zahl ohne `type = 'pantry_item'` angeben. Die Tabellen
 *   müssen genau die der migrierten Datenbank sein (ohne Verwaltungstabellen) plus `pantry_item`.
 *
 * Zusätzlich: `sync_state` (`active`, `serverUrl`, `cursor`, `lastSyncAt`) wie in der Quelle, `kontoId` NULL. Ein echter
 * Lauf schreibt `geprüft: …` ins Log (Tag `EchteDaten`).
 *
 * Die Kopie liegt im Datenbankordner der Test-App und wird danach gelöscht; echte Daten nie ins Repo.
 */
@RunWith(AndroidJUnit4::class)
class EchteDatenMigrationTest {
    private val kontext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ziel get() = kontext.getDatabasePath(DB)
    private val vorlage get() = kontext.getDatabasePath(VORLAGE)

    @After
    fun aufraeumen() {
        kontext.deleteDatabase(DB)
        kontext.deleteDatabase(VORLAGE)
    }

    @Test
    fun echteV9DatenbankMigriert() {
        val argumente = InstrumentationRegistry.getArguments()
        val quelle = argumente.getString(ARG_DB)
        val zahlenText = argumente.getString(ARG_ZAHLEN)
        if (quelle == null && zahlenText == null) {
            // Kein assumeTrue: Der Managed-Device-Lauf (UTP) wertet eine verletzte Annahme als Fehlschlag.
            Log.i(TAG, "keine Echtdaten-Argumente – Test übersprungen")
            return
        }
        // Ab hier ist ein Echtdaten-Lauf gemeint: Jedes fehlende Stück ist ein Fehler, nie ein stilles Bestehen.
        assertNotNull(quelle, "Argument $ARG_DB fehlt, obwohl $ARG_ZAHLEN gesetzt ist")
        assertNotNull(zahlenText, "Argument $ARG_ZAHLEN fehlt, obwohl $ARG_DB gesetzt ist")
        val datei = File(quelle)
        assertTrue(datei.isFile && datei.canRead(), "$ARG_DB ist keine lesbare Datei: ${datei.path}")
        val erwartet = zahlenAus(zahlenText)

        kopiere(datei, ziel)
        kopiere(datei, vorlage)
        val zustandVorher = syncZustandV9(vorlage)

        val room = baueDatenbank(kontext, DB)
        try {
            val db = room.openHelper.writableDatabase // läuft die Migration
            val tabellen = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata')",
            ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            assertTrue("pantry_item" !in tabellen, "pantry_item existiert nach der Migration noch")
            // Vollständigkeit: genau die Tabellen der migrierten Datenbank plus pantry_item.
            assertEquals(tabellen + "pantry_item", erwartet.keys, "Tabellen in $ARG_ZAHLEN")

            var zeilen = 0L
            for ((tabelle, zahl) in erwartet - "pantry_item") {
                val ist = db.query("SELECT count(*) FROM `$tabelle`").use { c -> c.moveToFirst(); c.getInt(0) }
                assertEquals(zahl, ist, "Zeilenzahl von $tabelle")
                zeilen += ist
            }

            val zustandNachher = db.query("SELECT active, serverUrl, cursor, lastSyncAt, kontoId FROM sync_state WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst(), "sync_state ohne Zeile id = 1")
                assertTrue(c.isNull(4), "kontoId muss nach der Migration NULL sein")
                listOf(c.getInt(0), c.getString(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3))
            }
            assertEquals(zustandVorher, zustandNachher, "sync_state (active, serverUrl, cursor, lastSyncAt)")

            val trigger = db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertEquals(SyncTrigger.namen.toSet(), trigger)

            Log.i(TAG, "geprüft: ${tabellen.size} Tabellen, $zeilen Zeilen, sync_state $zustandNachher, ${trigger.size} Trigger")
        } finally {
            room.close()
        }
    }

    /** `active, serverUrl, cursor, lastSyncAt` aus der unmigrierten v9-Kopie (SQLite direkt, mit WAL). */
    private fun syncZustandV9(datei: File): List<Any?> =
        SQLiteDatabase.openDatabase(datei.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); assertEquals(9, c.getInt(0), "Version der Quelle") }
            db.rawQuery("SELECT active, serverUrl, cursor, lastSyncAt FROM sync_state WHERE id = 1", null).use { c ->
                assertTrue(c.moveToFirst(), "Quelle: sync_state ohne Zeile id = 1")
                listOf(c.getInt(0), c.getString(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3))
            }
        }

    /** Kopiert die Datenbank samt WAL: ohne `-wal` fehlten noch nicht übertragene Änderungen. */
    private fun kopiere(quelle: File, ziel: File) {
        kontext.deleteDatabase(ziel.name)
        ziel.parentFile!!.mkdirs()
        for (endung in listOf("", "-wal", "-shm")) {
            val datei = File(quelle.path + endung)
            if (datei.isFile) datei.copyTo(File(ziel.path + endung), overwrite = true)
        }
    }

    private fun zahlenAus(text: String): Map<String, Int> =
        text.split(',', '+').map { it.trim() }.filter { it.isNotEmpty() }.associate { eintrag ->
            val teile = eintrag.split('=').map { it.trim() }
            assertEquals(2, teile.size, "$ARG_ZAHLEN: Eintrag „$eintrag“ nicht im Format tabelle=zahl")
            teile[0] to assertNotNull(teile[1].toIntOrNull(), "$ARG_ZAHLEN: „${teile[1]}“ ist keine Zahl")
        }

    private companion object {
        const val DB = "echte-migration.db"
        const val VORLAGE = "echte-vorlage.db"
        const val ARG_DB = "echteDb"
        const val ARG_ZAHLEN = "echteDbZahlen"
        const val TAG = "EchteDaten"
    }
}
