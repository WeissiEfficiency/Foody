package de.foody.server

import de.foody.server.db.Database
import de.foody.server.db.MigrationAbgebrochen
import de.foody.server.db.Migrations
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationGrundschemaTest {
    private fun tabellen(c: Connection): List<String> = c.createStatement().use { s ->
        s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }

    private fun mitDatei(block: (url: String) -> Unit) {
        val ordner = Files.createTempDirectory("foody-grundschema")
        try {
            block("jdbc:sqlite:${ordner.resolve("foody.db")}")
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun neueDatenbankHatNurKonten() {
        Database("jdbc:sqlite::memory:").use { db ->
            assertEquals(listOf("device", "sync_record", "user"), db.tx { tabellen(it) })
            assertEquals(Migrations.all.size, db.tx { Migrations.aktuell(it) })
        }
    }

    @Test
    fun datenbankDesV1ServersBrichtAb() = mitDatei { url ->
        DriverManager.getConnection(url).use { c -> c.createStatement().use { it.execute("PRAGMA user_version = 1") } }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertEquals(
            "Datenbank hat Version 1 (Haushalte, v1-Server) – die Umwandlung auf Konten gibt es nicht mehr (Stand vor dem Tag v1-final nehmen)",
            e.message,
        )
        DriverManager.getConnection(url).use { assertEquals(1, Migrations.aktuell(it)) }
    }

    /** Migration 3 → 4 räumt `haushalt_umzug` weg, aber nur, wenn alle Fotoordner umbenannt sind. */
    @Test
    fun haushaltUmzugWirdEntfernt() = mitDatei { url ->
        Database(url, migrationen = Migrations.all.take(3)).close()
        DriverManager.getConnection(url).use { c ->
            c.createStatement().use { it.execute("CREATE TABLE haushalt_umzug (household_id TEXT PRIMARY KEY, konto_id TEXT NOT NULL)") }
        }
        Database(url).use { db -> assertEquals(listOf("device", "sync_record", "user"), db.tx { tabellen(it) }) }
    }

    @Test
    fun offenerFotoumzugBrichtAb() = mitDatei { url ->
        Database(url, migrationen = Migrations.all.take(3)).close()
        DriverManager.getConnection(url).use { c ->
            c.createStatement().use {
                it.execute("CREATE TABLE haushalt_umzug (household_id TEXT PRIMARY KEY, konto_id TEXT NOT NULL)")
                it.execute("INSERT INTO haushalt_umzug VALUES ('h1', 'k1')")
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertEquals("1 Fotoordner aus der Umwandlung auf Konten noch nicht umbenannt – abgebrochen", e.message)
        DriverManager.getConnection(url).use { assertEquals(3, Migrations.aktuell(it)) }
    }
}
