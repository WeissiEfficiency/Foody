package de.foody.server

import de.foody.server.db.Database
import de.foody.server.db.MigrationAbgebrochen
import de.foody.server.db.Migrations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.sql.DriverManager
import java.time.Duration
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
    private val ersteMigration = Migrations.all.take(Migrations.GRUNDVERSION)
    private val zweiMigrationen = ersteMigration + probe

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
        val sicherung = ordner.resolve("vor-migration-3-2026-10-17-1200.db")
        assertTrue(sicherung.exists())
        assertEquals(2, userVersion(sicherung))
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
        Files.createDirectory(ordner.resolve("vor-migration-3-2026-10-17-1200.db"))
        assertFailsWith<MigrationAbgebrochen> { Database(url, uhr, zweiMigrationen) }
        assertEquals(2, userVersion(ordner.resolve("foody.db")))
    }

    @Test
    fun keineZweiteSicherungNachGescheiterterMigration() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        val kaputt = ersteMigration + "SELECT * FROM gibt_es_nicht"
        val e1 = assertFailsWith<Exception> { Database(url, uhr, kaputt) }
        assertTrue(e1 !is MigrationAbgebrochen)
        assertEquals(1, sicherungen(ordner).size)
        val spaeter = Clock.fixed(Instant.parse("2026-10-17T10:05:00Z"), ZoneOffset.UTC)
        val e2 = assertFailsWith<Exception> { Database(url, spaeter, kaputt) }
        assertTrue(e2 !is MigrationAbgebrochen)
        assertEquals(1, sicherungen(ordner).size)
    }

    @Test
    fun teilsicherungZaehltNicht() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        // Reste eines abgebrochenen Versuchs: leere Sicherung und halb geschriebene .tmp-Datei.
        Files.createFile(ordner.resolve("vor-migration-3-2026-10-17-1100.db"))
        Files.write(ordner.resolve("vor-migration-3-2026-10-17-1200.db.tmp"), byteArrayOf(1, 2, 3))
        Database(url, uhr, zweiMigrationen).close()
        val sicherung = ordner.resolve("vor-migration-3-2026-10-17-1200.db")
        assertTrue(sicherung.exists())
        assertEquals(2, userVersion(sicherung))
    }

    @Test
    fun sicherungWirdAtomarGeschrieben() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        Database(url, uhr, zweiMigrationen).close()
        assertEquals(1, sicherungen(ordner).size)
        assertEquals(emptyList(), ordner.listDirectoryEntries("*.tmp"))
    }

    @Test
    fun alteSicherungWirdErneuert() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        val kaputt = ersteMigration + "SELECT * FROM gibt_es_nicht"
        assertFailsWith<Exception> { Database(url, uhr, kaputt) }
        val alt = sicherungen(ordner).single()
        Files.setLastModifiedTime(alt, FileTime.from(uhr.instant().minus(Duration.ofHours(25))))
        Database(url, Clock.offset(uhr, Duration.ofMinutes(1)), zweiMigrationen).close()
        assertEquals(2, sicherungen(ordner).size)
    }

    @Test
    fun sicherungEnthaeltWalDaten() {
        val (ordner, url) = neueDb()
        Database(url, migrationen = ersteMigration).close()
        // Zweite Verbindung schreibt nur ins WAL (kein Checkpoint) und bleibt offen.
        DriverManager.getConnection(url).use { andere ->
            andere.createStatement().use { s ->
                s.execute("PRAGMA journal_mode=WAL")
                s.execute("PRAGMA wal_autocheckpoint=0")
                s.execute("INSERT INTO user (id, username, username_lower, password_hash, display_name, created_at) VALUES ('u1', 'nurwal', 'nurwal', 'x', 'Nur WAL', 0)")
            }
            assertTrue(Files.size(ordner.resolve("foody.db-wal")) > 0)
            Database(url, uhr, zweiMigrationen).close()
            val sicherung = ordner.resolve("vor-migration-3-2026-10-17-1200.db")
            val anzahl = DriverManager.getConnection("jdbc:sqlite:$sicherung").use { c ->
                c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM user WHERE username = 'nurwal'").use { it.next(); it.getInt(1) } }
            }
            assertEquals(1, anzahl)
        }
    }

    @Test
    fun speicherDatenbankOhneSicherung() {
        Database("jdbc:sqlite::memory:", uhr, zweiMigrationen).close()
    }
}
