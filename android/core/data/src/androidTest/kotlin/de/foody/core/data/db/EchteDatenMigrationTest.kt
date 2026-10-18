package de.foody.core.data.db

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.core.data.baueDatenbank
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 9 → 10 mit einer echten v1-Datenbank (Kopie vom Handy). Läuft nur mit Instrumentation-Argumenten, sonst
 * kehrt er sofort zurück (gilt dann als bestanden):
 * - `echteDb`: absoluter Pfad der `foody.db` auf dem Testgerät, für die Test-App lesbar (z. B. unter
 *   `/sdcard/Android/data/de.foody.core.data.test/files/`); ein danebenliegendes `foody.db-wal`/`-shm` wird mitkopiert.
 * - `echteDbZahlen`: erwartete Zeilenzahlen nach der Migration, `tabelle=zahl,…`. `pantry_item` wird ignoriert; für
 *   `sync_outbox`, `sync_record_rev` und `sync_problem` die Zahl ohne `type = 'pantry_item'` angeben.
 *
 * Die Kopie liegt im Datenbankordner der Test-App und wird danach gelöscht; echte Daten nie ins Repo.
 */
@RunWith(AndroidJUnit4::class)
class EchteDatenMigrationTest {
    private val kontext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ziel get() = kontext.getDatabasePath(DB)

    @After
    fun aufraeumen() {
        kontext.deleteDatabase(DB)
    }

    @Test
    fun echteV9DatenbankMigriert() {
        val argumente = InstrumentationRegistry.getArguments()
        val quelle = argumente.getString("echteDb")
        if (quelle == null) {
            // Kein assumeTrue: Der Managed-Device-Lauf (UTP) wertet eine verletzte Annahme als Fehlschlag.
            Log.i("EchteDatenMigrationTest", "Argument echteDb fehlt – Test übersprungen")
            return
        }
        val erwartet = zahlenAus(requireNotNull(argumente.getString("echteDbZahlen")) { "Argument echteDbZahlen fehlt" })

        kopiere(File(quelle))

        val room = baueDatenbank(kontext, DB)
        try {
            val db = room.openHelper.writableDatabase // läuft die Migration
            val tabellen = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertTrue("pantry_item" !in tabellen, "pantry_item existiert nach der Migration noch")
            for ((tabelle, zahl) in erwartet - "pantry_item") {
                assertTrue(tabelle in tabellen, "Tabelle $tabelle fehlt nach der Migration")
                val ist = db.query("SELECT count(*) FROM `$tabelle`").use { c -> c.moveToFirst(); c.getInt(0) }
                assertEquals(zahl, ist, "Zeilenzahl von $tabelle")
            }
            val trigger = db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertEquals(SyncTrigger.namen.toSet(), trigger)
        } finally {
            room.close()
        }
    }

    /** Kopiert die Datenbank samt WAL: ohne `-wal` fehlten noch nicht übertragene Änderungen. */
    private fun kopiere(quelle: File) {
        assertTrue(quelle.isFile, "echteDb nicht lesbar: ${quelle.path}")
        kontext.deleteDatabase(DB)
        ziel.parentFile!!.mkdirs()
        for (endung in listOf("", "-wal", "-shm")) {
            val datei = File(quelle.path + endung)
            if (datei.isFile) datei.copyTo(File(ziel.path + endung), overwrite = true)
        }
    }

    private fun zahlenAus(text: String): Map<String, Int> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { eintrag ->
            val (tabelle, zahl) = eintrag.split('=').map { it.trim() }
            tabelle to zahl.toInt()
        }

    private companion object {
        const val DB = "echte-migration.db"
    }
}
