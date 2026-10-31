package de.foody.core.data.einkauf

import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.MeasureUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufPlanTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var ign: IgnorierteSchluessel
    private lateinit var repo: EinkaufRepository
    private val sonntag = MONTAG.plusDays(6)

    @Before
    fun setUp() {
        db = testDatenbank()
        ign = ignoriert()
        repo = EinkaufRepository(db, { JETZT }, ign, EmptyCoroutineContext)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun vorschauOhneWasser() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        val namen = repo.planVorschau(MONTAG, sonntag).map { it.name }
        assertEquals(listOf("Zwiebeln", "Reis"), namen)
    }

    @Test
    fun zweiRezepteEinArtikel() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        db.planen("s2", MITTWOCH, "p")
        val z = repo.planVorschau(MONTAG, sonntag).single { it.name == "Zwiebeln" }
        assertEquals("300 g", z.menge)
        assertEquals("200 g Curry (Mo) + 100 g Reispfanne (Mi)", z.herkunft)
        assertEquals("Obst & Gemüse", z.abteilung)
        assertEquals("z-zwiebel|MASS", z.schluessel)
    }

    @Test
    fun zweiDimensionenZweiArtikel() = runTest {
        // Ohne Stückgewicht lassen sich Gramm und Stück nicht zusammenrechnen (Zwiebeln mit 80 g/Stück schon)
        db.einkaufBestand()
        db.zutatDao().upsert(de.foody.core.data.db.ZutatEntity(id = "z-knobi", canonicalName = "Knoblauch", createdAt = 1, updatedAt = 1))
        db.zeile("c4", "c", "z-knobi", "10", MeasureUnit.GRAM, 3)
        db.zeile("p3", "p", "z-knobi", "2", MeasureUnit.PIECE, 2)
        db.zeile("p4", "p", "z-zwiebel", "1", MeasureUnit.PIECE, 3)
        db.planen("s1", MONTAG, "c")
        db.planen("s2", MITTWOCH, "p")
        val v = repo.planVorschau(MONTAG, sonntag)
        assertEquals(setOf("z-knobi|MASS", "z-knobi|COUNT"), v.filter { it.name == "Knoblauch" }.map { it.schluessel }.toSet())
        // 300 g + 1 Stk. bei 80 g/Stück: gekauft wird in Stück (Regel 10), egal welche Planzeile zuerst kommt
        assertEquals("5 Stk.", v.single { it.name == "Zwiebeln" }.menge)
    }

    @Test
    fun zeitraumGrenzen() = runTest {
        db.einkaufBestand()
        db.planen("s1", sonntag, "c")
        db.planen("s2", sonntag.plusDays(1), "p")
        val z = repo.planVorschau(MONTAG, sonntag).single { it.name == "Zwiebeln" }
        assertEquals("200 g", z.menge)
    }

    @Test
    fun anlegenMitHerkunft() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        db.planen("s2", MITTWOCH, "p")
        val id = assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, emptySet()))
        val l = assertNotNull(db.einkaufslisteDao().nachId(id))
        assertEquals("26.10.–1.11.", l.name)
        assertEquals(MONTAG, l.rangeStart)
        assertEquals(sonntag, l.rangeEnd)
        val z = db.einkaufsartikelDao().nachListe(id).single { it.name == "Zwiebeln" }
        assertEquals(false, z.manual)
        assertEquals("z-zwiebel", z.ingredientId)
        assertEquals(0, BigDecimal("300").compareTo(z.amount))
        assertEquals(MeasureUnit.GRAM, z.unit)
        val quellen = db.einkaufsartikelQuelleDao().nachArtikel(z.id)
        assertEquals(setOf("Curry" to MONTAG, "Reispfanne" to MITTWOCH), quellen.map { it.recipeName to it.date }.toSet())
        assertEquals(setOf("s1", "s2"), quellen.map { it.mealSlotId }.toSet())
    }

    @Test
    fun anlegenAbgewaehltIgnoriert() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        val id = assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, setOf("z-reis|MASS")))
        assertEquals(listOf("Zwiebeln"), db.einkaufsartikelDao().nachListe(id).map { it.name })
        assertEquals(setOf("z-reis|MASS"), ign.lesen(id))
    }

    @Test
    fun manuelleWandernMit() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        db.einkaufslisteDao().upsert(liste("alt", 1))
        db.einkaufsartikelDao().upsert(artikel("m1", "alt", "Grillkohle"))
        db.einkaufsartikelDao().upsert(artikel("m2", "alt", "Milch", gekauft = true))
        db.einkaufsartikelDao().upsert(artikel("p1", "alt", "Reis", "100", MeasureUnit.GRAM, manuell = false, zutatId = "z-reis"))
        val id = assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, emptySet()))
        assertEquals(id, db.einkaufsartikelDao().nachId("m1")?.listId)
        assertEquals("alt", db.einkaufsartikelDao().nachId("m2")?.listId)
        assertEquals("alt", db.einkaufsartikelDao().nachId("p1")?.listId)
        val neu = db.einkaufsartikelDao().nachListe(id)
        assertTrue(neu.single { it.id == "m1" }.sortOrder > neu.filter { !it.manual }.maxOf { it.sortOrder })
    }

    @Test
    fun allesAbgewaehltNull() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "p")
        assertNull(repo.ausPlanAnlegen(MONTAG, sonntag, setOf("z-reis|MASS", "z-zwiebel|MASS")))
        assertTrue(db.einkaufslisteDao().alle().isEmpty())
    }

    @Test
    fun nichtsGeplantLeer() = runTest {
        db.einkaufBestand()
        assertTrue(repo.planVorschau(MONTAG, sonntag).isEmpty())
        assertNull(repo.ausPlanAnlegen(MONTAG, sonntag, emptySet()))
    }

    @Test
    fun neueListeIstAktuell() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        db.einkaufslisteDao().upsert(liste("zukunft", JETZT + 1_000))
        val id = assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, emptySet()))
        assertEquals(id, repo.aktuelleListeId().first())
    }
}
