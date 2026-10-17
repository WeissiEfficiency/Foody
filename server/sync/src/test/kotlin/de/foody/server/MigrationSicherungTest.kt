package de.foody.server

import de.foody.server.db.Database
import de.foody.server.db.MigrationAbgebrochen
import de.foody.server.db.Migrations
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationSicherungTest {
    private val uhr = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC)
    private val probe = "CREATE TABLE probe (x INTEGER)"
    private val ersteMigration = listOf(Migrations.all[0])
    private val zweiMigrationen = listOf(Migrations.all[0], probe)

    private fun neueDb(): Pair<Path, String> {
        val ordner = Files.createTempDirectory("foody-sicherung")
        return ordner to "jdbc:sqlite:${ordner.resolve("foody.db")}"
    }

    private fun userVersion(datei: Path): Int =
        DriverManager.getConnection("jdbc:sqlite:$datei").use { c ->
            c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
        }

    private fun sicherungen(ordner: Path): List<Path> = ordner.listDirectoryEntries("vor-migration-*.db")

    @Test
    fun sicherungVorAusstehenderMigration() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        Database(url, uhr, zweiMigrationen).close()
        val sicherung = ordner.resolve("vor-migration-2-2026-10-17-1200.db")
        assertTrue(sicherung.exists())
        assertEquals(1, userVersion(sicherung))
    }

    @Test
    fun keineSicherungOhneMigration() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        Database(url, uhr, zweiMigrationen).close()
        val vorher = sicherungen(ordner).size
        Database(url, uhr, zweiMigrationen).close()
        assertEquals(vorher, sicherungen(ordner).size)
    }

    @Test
    fun frischeInstallationOhneSicherung() {
        val (ordner, url) = neueDb()
        Database(url, uhr, zweiMigrationen).close()
        assertEquals(0, sicherungen(ordner).size)
    }

    @Test
    fun sicherungScheitertKeineMigration() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        Files.createDirectory(ordner.resolve("vor-migration-2-2026-10-17-1200.db"))
        assertFailsWith<MigrationAbgebrochen> { Database(url, uhr, zweiMigrationen) }
        assertEquals(1, userVersion(ordner.resolve("foody.db")))
    }

    @Test
    fun keineZweiteSicherungNachGescheiterterMigration() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        val kaputt = listOf(Migrations.all[0], "SELECT * FROM gibt_es_nicht")
        val e1 = assertFailsWith<Exception> { Database(url, uhr, kaputt) }
        assertTrue(e1 !is MigrationAbgebrochen)
        assertEquals(1, sicherungen(ordner).size)
        val spaeter = Clock.fixed(Instant.parse("2026-10-17T10:05:00Z"), ZoneOffset.UTC)
        val e2 = assertFailsWith<Exception> { Database(url, spaeter, kaputt) }
        assertTrue(e2 !is MigrationAbgebrochen)
        assertEquals(1, sicherungen(ordner).size)
    }

    @Test
    fun speicherDatenbankOhneSicherung() {
        Database("jdbc:sqlite::memory:", uhr, zweiMigrationen).close()
    }
}
