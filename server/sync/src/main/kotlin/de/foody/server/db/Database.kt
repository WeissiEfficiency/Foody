package de.foody.server.db

import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.Path

/**
 * SQLite-Datenbank mit einer einzigen, synchronisierten Verbindung.
 * Beim Öffnen werden die Pragmas gesetzt und die Migrationen angewendet;
 * steht eine Migration aus, wird vorher eine Sicherung neben die Datenbankdatei geschrieben.
 */
class Database(
    url: String,
    clock: Clock = Clock.systemUTC(),
    migrationen: List<String> = Migrations.all,
) : AutoCloseable {
    private val connection: Connection = oeffne(url, clock, migrationen)

    private companion object {
        private val ZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        private val BERLIN = ZoneId.of("Europe/Berlin")

        fun oeffne(url: String, clock: Clock, migrationen: List<String>): Connection {
            // Vor dem Verbinden prüfen: sqlite-jdbc legt eine fehlende Datei beim Verbinden leer an.
            val datei: Path? = if (url.contains(":memory:")) null else Path.of(url.removePrefix("jdbc:sqlite:"))
            val bestand = datei != null && Files.exists(datei) && Files.size(datei) > 0
            val c = DriverManager.getConnection(url)
            try {
                c.createStatement().use { s ->
                    if (datei != null) s.execute("PRAGMA journal_mode=WAL")
                    s.execute("PRAGMA foreign_keys=ON")
                    s.execute("PRAGMA busy_timeout=5000")
                }
                if (bestand && Migrations.aktuell(c) < migrationen.size && !hatSicherung(datei, migrationen.size)) sichere(c, datei, clock, migrationen.size)
                Migrations.apply(c, migrationen)
            } catch (e: Throwable) {
                c.close()
                throw e
            }
            return c
        }

        /**
         * Gibt es für [ziel] schon eine Sicherung (Datei `vor-migration-<ziel>-*.db`), ist die Datenbank seit dem
         * gescheiterten Versuch unverändert; ein Neustart-Schleife soll keine weitere Sicherung anhäufen.
         */
        private fun hatSicherung(datei: Path, ziel: Int): Boolean {
            val ordner = datei.toAbsolutePath().parent ?: return false
            return Files.newDirectoryStream(ordner, "vor-migration-$ziel-*.db").use { strom ->
                strom.any { Files.isRegularFile(it) }
            }
        }

        private fun sichere(c: Connection, datei: Path, clock: Clock, ziel: Int) {
            try {
                val name = "vor-migration-$ziel-${ZEIT.format(clock.instant().atZone(BERLIN))}.db"
                val sicherung = (datei.toAbsolutePath().parent ?: Path.of(".")).resolve(name)
                c.prepareStatement("VACUUM INTO ?").use { it.setString(1, sicherung.toString()); it.execute() }
            } catch (e: Exception) {
                throw MigrationAbgebrochen("Sicherung vor Migration fehlgeschlagen: ${e.message}")
            }
        }
    }

    /** Führt [block] in einer Transaktion aus; bei einer Ausnahme wird zurückgerollt. */
    fun <T> tx(block: (Connection) -> T): T = synchronized(connection) {
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /** Führt [block] ohne Transaktion (Auto-Commit) aus, z. B. für `VACUUM INTO`, das in Transaktionen verboten ist. */
    fun <T> outsideTx(block: (Connection) -> T): T = synchronized(connection) { block(connection) }

    override fun close() {
        synchronized(connection) { connection.close() }
    }
}
