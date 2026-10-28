package de.foody.server.db

import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * SQLite-Datenbank mit einer einzigen, synchronisierten Verbindung.
 * Beim Öffnen werden die Pragmas gesetzt und die Migrationen angewendet;
 * steht eine Migration aus, wird vorher eine Sicherung neben die Datenbankdatei geschrieben.
 * Mit `migrieren = false` (Admin-Befehle) bleibt das Schema unverändert und es entsteht keine Sicherung.
 */
class Database(
    url: String,
    clock: Clock = Clock.systemUTC(),
    migrationen: List<String> = Migrations.all,
    migrieren: Boolean = true,
) : AutoCloseable {
    private val connection: Connection = oeffne(url, clock, migrationen, migrieren)

    private companion object {
        private val ZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        private val BERLIN = ZoneId.of("Europe/Berlin")

        fun oeffne(url: String, clock: Clock, migrationen: List<String>, migrieren: Boolean): Connection {
            // Vor dem Verbinden prüfen: sqlite-jdbc legt eine fehlende Datei beim Verbinden leer an.
            val datei: Path? = if (url.contains(":memory:")) null else Path.of(url.removePrefix("jdbc:sqlite:"))
            val bestand = datei != null && Files.exists(datei) && Files.size(datei) > 0
            val c = DriverManager.getConnection(url)
            try {
                c.createStatement().use { s ->
                    if (datei != null) s.execute("PRAGMA journal_mode=WAL")
                    // Mit WAL sicher: Ein Absturz verliert höchstens die letzten Transaktionen, nie die Datei; spart
                    // den fsync je Transaktion (ein Push schreibt je Datensatz)
                    s.execute("PRAGMA synchronous=NORMAL")
                    s.execute("PRAGMA foreign_keys=ON")
                    s.execute("PRAGMA busy_timeout=5000")
                }
                if (migrieren) {
                    if (bestand && Migrations.aktuell(c) < migrationen.size && !hatSicherung(datei, migrationen.size, clock)) sichere(c, datei, clock, migrationen.size)
                    Migrations.apply(c, migrationen)
                }
            } catch (e: Throwable) {
                c.close()
                throw e
            }
            return c
        }

        /** So lange gilt eine vorhandene Sicherung als aktuell (Neustarts nach einem gescheiterten Versuch liegen darin). */
        private val FRISCH = Duration.ofHours(24)

        /**
         * Gibt es für [ziel] schon eine vollständige Sicherung (reguläre, nicht leere Datei `vor-migration-<ziel>-*.db`),
         * die jünger als 24 Stunden ist, ist die Datenbank seit dem gescheiterten Versuch unverändert; eine
         * Neustart-Schleife soll keine weitere Sicherung anhäufen. Ältere Sicherungen (z. B. vor einem Rückweg auf eine
         * alte Version) zählen nicht.
         */
        private fun hatSicherung(datei: Path, ziel: Int, clock: Clock): Boolean {
            val ordner = datei.toAbsolutePath().parent ?: return false
            val grenze = clock.instant().minus(FRISCH)
            return Files.newDirectoryStream(ordner, "vor-migration-$ziel-*.db").use { strom ->
                strom.any { Files.isRegularFile(it) && Files.size(it) > 0 && Files.getLastModifiedTime(it).toInstant().isAfter(grenze) }
            }
        }

        /** Schreibt die Sicherung erst nach `<name>.tmp` und benennt sie danach um, damit nie eine Teildatei als Sicherung zählt. */
        private fun sichere(c: Connection, datei: Path, clock: Clock, ziel: Int) {
            val name = "vor-migration-$ziel-${ZEIT.format(clock.instant().atZone(BERLIN))}.db"
            val sicherung = (datei.toAbsolutePath().parent ?: Path.of(".")).resolve(name)
            val tmp = sicherung.resolveSibling("$name.tmp")
            try {
                Files.deleteIfExists(tmp)
                c.prepareStatement("VACUUM INTO ?").use { it.setString(1, tmp.toString()); it.execute() }
                try {
                    Files.move(tmp, sicherung, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp, sicherung, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                runCatching { Files.deleteIfExists(tmp) }
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
