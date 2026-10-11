package de.foody.server.db

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite-Datenbank mit einer einzigen, synchronisierten Verbindung.
 * Beim Öffnen werden die Pragmas gesetzt und die Migrationen angewendet.
 */
class Database(url: String) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(url).also { c ->
        c.createStatement().use { s ->
            if (!url.contains(":memory:")) s.execute("PRAGMA journal_mode=WAL")
            s.execute("PRAGMA foreign_keys=ON")
            s.execute("PRAGMA busy_timeout=5000")
        }
        Migrations.apply(c)
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
