package de.foody.core.data.einkauf

import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncZustandEntity
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufSchreibenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var ign: IgnorierteSchluessel
    private lateinit var repo: EinkaufRepository

    @Before
    fun setUp() {
        db = testDatenbank()
        ign = ignoriert()
        repo = EinkaufRepository(db, { JETZT }, ign)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun artikelIn(listeId: String) = db.einkaufsartikelDao().nachListe(listeId)
    private suspend fun eins(id: String) = assertNotNull(db.einkaufsartikelDao().nachId(id))
    private suspend fun neueListe(id: String = "l") = db.einkaufslisteDao().upsert(liste(id, 1))

    @Test
    fun abhakenUndZurueck() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        repo.abhaken("a1", true)
        assertTrue(eins("a1").checked)
        repo.abhaken("a1", false)
        assertEquals(false, eins("a1").checked)
    }

    @Test
    fun antippenNeuOffenGekauft() = runTest {
        neueListe()
        repo.antippen("l", "Milch", "Milch & Käse")
        val m = artikelIn("l").single()
        assertEquals("Milch", m.name)
        assertEquals("Milch & Käse", m.category)
        assertTrue(m.manual)
        assertEquals(false, m.checked)
        repo.antippen("l", "Milch", "Milch & Käse")
        assertTrue(artikelIn("l").single().checked)
        repo.antippen("l", "Milch", "Milch & Käse")
        assertEquals(false, artikelIn("l").single().checked)
    }

    @Test
    fun neuAnsEnde() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch", reihenfolge = 4))
        repo.antippen("l", "Eier", "Milch & Käse")
        assertEquals(5, artikelIn("l").single { it.name == "Eier" }.sortOrder)
    }

    @Test
    fun antippenOhneListeLegtEinkaufAn() = runTest {
        val id = repo.antippen(null, "Milch", "Milch & Käse")
        val l = assertNotNull(db.einkaufslisteDao().nachId(id))
        assertEquals("Einkauf", l.name)
        assertNull(l.rangeStart)
        assertEquals(JETZT, l.createdAt)
        assertEquals(1, artikelIn(id).size)
    }

    @Test
    fun zuletztAntippen() = runTest {
        db.einkaufslisteDao().upsert(liste("alt", 1))
        neueListe("l")
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch", gekauft = true))
        db.einkaufsartikelDao().upsert(artikel("a2", "l", "Eier"))
        db.einkaufsartikelDao().upsert(artikel("b1", "alt", "Reis", "500", MeasureUnit.GRAM, gekauft = true, zutatId = "z-reis"))
        repo.zuletztAntippen("l", ZuletztArtikel("Milch", "🥛", "Milch & Käse", null))
        assertEquals(false, eins("a1").checked)
        repo.zuletztAntippen("l", ZuletztArtikel("Eier", "🥚", "Milch & Käse", null))
        assertEquals(false, eins("a2").checked)
        repo.zuletztAntippen("l", ZuletztArtikel("Reis", "🍚", "Getreideprodukte", "z-reis"))
        val reis = artikelIn("l").single { it.name == "Reis" }
        assertNull(reis.amount)
        assertEquals("z-reis", reis.ingredientId)
        assertEquals("Getreideprodukte", reis.category)
        assertEquals(3, artikelIn("l").size)
    }

    @Test
    fun eingabeAndereSchreibweise() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        repo.eingeben("l", "  milch ")
        assertTrue(eins("a1").checked)
        assertEquals(1, artikelIn("l").size)
    }

    @Test
    fun eingabeUnbekanntEigenerArtikel() = runTest {
        neueListe()
        repo.eingeben("l", "Grillkohle")
        val g = artikelIn("l").single()
        assertEquals("Grillkohle", g.name)
        assertEquals("Eigene Artikel", g.category)
    }

    @Test
    fun eingabeLeerNichts() = runTest {
        assertNull(repo.eingeben(null, "   "))
        assertTrue(db.einkaufslisteDao().alle().isEmpty())
    }

    @Test
    fun detailsAendern() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        repo.details("a1", " Hafermilch ", "Bio", "Getränke")
        val a = eins("a1")
        assertEquals("Hafermilch", a.name)
        assertEquals("Bio", a.note)
        assertEquals("Getränke", a.category)
        repo.details("a1", "Hafermilch", "  ", "Getränke")
        assertNull(eins("a1").note)
    }

    @Test
    fun detailsNameLeerAbgelehnt() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        assertFailsWith<IllegalArgumentException> { repo.details("a1", "  ", "", "Getränke") }
        assertFailsWith<IllegalArgumentException> { repo.details("a1", "x".repeat(61), "", "Getränke") }
        assertEquals("Milch", eins("a1").name)
    }

    @Test
    fun loeschenUndWiederherstellenMitHerkunft() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1, MONTAG, MONTAG.plusDays(6)))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Zwiebeln", "300", MeasureUnit.GRAM, manuell = false, zutatId = "z-zwiebel"))
        db.einkaufsartikelQuelleDao().upsert(quelle("q1", "a1", "Curry", MONTAG, "200"))
        db.einkaufsartikelQuelleDao().upsert(quelle("q2", "a1", "Reispfanne", MITTWOCH, "100"))
        val weg = assertNotNull(repo.loeschen("a1"))
        assertNull(db.einkaufsartikelDao().nachId("a1"))
        assertTrue(db.einkaufsartikelQuelleDao().alle().isEmpty())
        repo.wiederherstellen(weg)
        assertEquals("a1", eins("a1").id)
        assertEquals(2, db.einkaufsartikelQuelleDao().nachArtikel("a1").size)
    }

    @Test
    fun loeschenPlanArtikelIgnoriert() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1, MONTAG, MONTAG.plusDays(6)))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Zwiebeln", "300", MeasureUnit.GRAM, manuell = false, zutatId = "z-zwiebel"))
        db.einkaufsartikelDao().upsert(artikel("a2", "l", "Milch"))
        val weg = assertNotNull(repo.loeschen("a1"))
        repo.loeschen("a2")
        assertEquals(setOf("z-zwiebel|MASS"), ign.lesen("l"))
        repo.wiederherstellen(weg)
        assertEquals(emptySet(), ign.lesen("l"))
    }

    @Test
    fun wiederherstellenOhneListeStill() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        val weg = assertNotNull(repo.loeschen("a1"))
        db.einkaufslisteDao().loeschen("l")
        repo.wiederherstellen(weg)
        assertNull(db.einkaufsartikelDao().nachId("a1"))
    }

    @Test
    fun abhakenGeloeschtStill() = runTest {
        repo.abhaken("gibt-es-nicht", true)
        assertNull(repo.loeschen("gibt-es-nicht"))
        repo.details("gibt-es-nicht", "Milch", "", "Getränke")
        assertTrue(db.einkaufsartikelDao().alle().isEmpty())
    }

    @Test
    fun listeLoeschenVergisstSchluessel() = runTest {
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        ign.hinzufuegen("l", listOf("z|MASS"))
        repo.listeLoeschen("l")
        assertNull(db.einkaufslisteDao().nachId("l"))
        assertTrue(db.einkaufsartikelDao().alle().isEmpty())
        assertEquals(emptySet(), ign.lesen("l"))
    }

    @Test
    fun aenderungenInDerOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        neueListe()
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        db.syncDao().outboxLeeren()
        repo.details("a1", "Milch", "Bio", "Milch & Käse")
        assertTrue(db.syncDao().outbox().any { it.type == "shopping_item" && it.recordId == "a1" })
    }

    @Test
    fun updatedAtGesetzt() = runTest {
        neueListe()
        repo.antippen("l", "Milch", "Milch & Käse")
        assertEquals(JETZT, artikelIn("l").single().updatedAt)
    }
}
