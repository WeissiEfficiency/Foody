package de.foody.server

import de.foody.server.db.Database
import de.foody.server.photos.FotoUmzug
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FotoUmzugTest {
    private val db = Database("jdbc:sqlite::memory:")
    private val root: Path = Files.createTempDirectory("foody-umzug")

    private fun eintrag(haushalt: String, konto: String) = db.tx { c ->
        c.prepareStatement("INSERT INTO haushalt_umzug (household_id, konto_id) VALUES (?, ?)").use {
            it.setString(1, haushalt); it.setString(2, konto); it.executeUpdate()
        }
    }

    private fun offen(): Int = db.tx { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM haushalt_umzug").use { it.next(); it.getInt(1) } }
    }

    private fun datei(ordner: String, name: String, inhalt: String = name) {
        Files.createDirectories(root.resolve(ordner))
        Files.writeString(root.resolve(ordner).resolve(name), inhalt)
    }

    @Test fun ordnerWirdUmbenannt() {
        eintrag("h1", "k1")
        datei("h1", "a.jpg")
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertTrue(root.resolve("k1/a.jpg").exists())
        assertFalse(root.resolve("h1").exists())
        assertEquals(0, offen())
    }

    @Test fun fehlenderOrdnerWirdErledigt() {
        eintrag("h1", "k1")
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertEquals(0, offen())
    }

    @Test fun beideOrdnerWerdenVereint() {
        eintrag("h1", "k1")
        datei("h1", "a.jpg")
        datei("h1", "b.jpg", "alt")
        datei("k1", "b.jpg", "neu")
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertTrue(root.resolve("k1/a.jpg").exists())
        assertEquals("neu", Files.readString(root.resolve("k1/b.jpg")))
        assertFalse(root.resolve("h1").exists())
        assertEquals(0, offen())
    }

    @Test fun abbruchWirdFortgesetzt() {
        eintrag("h1", "k1")
        eintrag("h2", "k2")
        datei("h1", "a.jpg")
        datei("h2", "b.jpg")
        Files.writeString(root.resolve("k2"), "Datei statt Ordner")
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertEquals(1, offen())
        assertTrue(root.resolve("h2/b.jpg").exists())
        Files.delete(root.resolve("k2"))
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertEquals(0, offen())
        assertTrue(root.resolve("k2/b.jpg").exists())
    }

    @Test fun ungueltigerNameWirdUebersprungen() {
        eintrag("../x", "k1")
        assertEquals(0, FotoUmzug(db, root).ausfuehren())
        assertEquals(1, offen())
    }

    @Test fun gleicheIdsBehaltenFotos() {
        eintrag("x", "x")
        datei("x", "a.jpg")
        assertEquals(1, FotoUmzug(db, root).ausfuehren())
        assertTrue(root.resolve("x/a.jpg").exists())
        assertEquals(0, offen())
    }

    @Test fun ohneEintraegeNichtsZuTun() {
        assertEquals(0, FotoUmzug(db, root).ausfuehren())
    }
}
