package de.foody.core.data.einkauf

import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufAbgleichTest {
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

    /** Liste aus dem Curry am Montag (200 g Zwiebeln, 300 g Reis). */
    private suspend fun curryListe(abgewaehlt: Set<String> = emptySet()): String {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        return assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, abgewaehlt))
    }

    private suspend fun zeilen(id: String) = repo.abgleich(id).first()
    private suspend fun artikel(id: String, name: String) = db.einkaufsartikelDao().nachListe(id).single { it.name == name }

    @Test
    fun unveraendertLeer() = runTest {
        val id = curryListe()
        assertTrue(zeilen(id).isEmpty())
    }

    @Test
    fun neuerEintragNeu() = runTest {
        val id = curryListe()
        db.rezeptDao().upsert(rezept("m", "Milchreis"))
        db.zeile("m1", "m", "z-milch", "500", MeasureUnit.MILLILITER, 0)
        db.planen("s2", MITTWOCH, "m")
        val z = zeilen(id).single()
        assertEquals(AbgleichArt.NEU, z.art)
        assertEquals("Milch", z.name)
        assertEquals("500 ml", z.neu)
    }

    @Test
    fun mehrUndWeniger() = runTest {
        val id = curryListe()
        db.planen("s1", MONTAG, "c", portionen = 4)
        val mehr = zeilen(id).single { it.name == "Zwiebeln" }
        assertEquals(AbgleichArt.MEHR, mehr.art)
        assertEquals("200 g", mehr.alt)
        assertEquals("400 g", mehr.neu)
        db.planen("s1", MONTAG, "c", portionen = 1)
        assertEquals(AbgleichArt.WENIGER, zeilen(id).single { it.name == "Zwiebeln" }.art)
    }

    @Test
    fun gekauftWenigerKeineZeile() = runTest {
        val id = curryListe()
        repo.abhaken(artikel(id, "Zwiebeln").id, true)
        db.planen("s1", MONTAG, "c", portionen = 1)
        assertEquals(listOf("Reis"), zeilen(id).map { it.name })
    }

    @Test
    fun gekauftMehrWiederOffen() = runTest {
        val id = curryListe()
        repo.abhaken(artikel(id, "Zwiebeln").id, true)
        db.planen("s1", MONTAG, "c", portionen = 4)
        assertEquals(AbgleichArt.WIEDER_OFFEN, zeilen(id).single { it.name == "Zwiebeln" }.art)
    }

    @Test
    fun entfaelltOffen() = runTest {
        val id = curryListe()
        db.rezeptZutatDao().loeschen("c2")
        val z = zeilen(id).single()
        assertEquals(AbgleichArt.ENTFAELLT, z.art)
        assertEquals("Reis", z.name)
        assertEquals("300 g", z.alt)
    }

    @Test
    fun entfaelltGekauftKeineZeile() = runTest {
        val id = curryListe()
        repo.abhaken(artikel(id, "Reis").id, true)
        db.planEintragDao().loeschen("s1")
        assertEquals(listOf("Zwiebeln"), zeilen(id).map { it.name })
    }

    @Test
    fun ignoriertKeineZeile() = runTest {
        val id = curryListe(abgewaehlt = setOf("z-reis|MASS"))
        repo.loeschen(artikel(id, "Zwiebeln").id)
        db.planen("s1", MONTAG, "c", portionen = 4)
        assertTrue(zeilen(id).isEmpty())
    }

    @Test
    fun aufgerundeteStueckzahlKeineZeile() = runTest {
        db.einkaufBestand()
        db.zutatDao().upsert(ZutatEntity(id = "z-ei", canonicalName = "Eier", createdAt = 1, updatedAt = 1))
        db.zeile("c5", "c", "z-ei", "1", MeasureUnit.PIECE, 4)
        db.planen("s1", MONTAG, "c", portionen = 1)
        val id = assertNotNull(repo.ausPlanAnlegen(MONTAG, sonntag, emptySet()))
        assertEquals("1 Stk.", db.einkaufsartikelDao().nachListe(id).single { it.name == "Eier" }.let { mengeText(it.amount, it.unit) })
        db.zeile("c5", "c", "z-ei", "1.4", MeasureUnit.PIECE, 4)
        assertTrue(zeilen(id).isEmpty())
    }

    @Test
    fun mahlzeitGeloeschtEntfaellt() = runTest {
        val id = curryListe()
        db.planEintragDao().loeschen("s1")
        assertEquals(setOf(AbgleichArt.ENTFAELLT), zeilen(id).map { it.art }.toSet())
        assertEquals(2, zeilen(id).size)
    }

    @Test
    fun rezeptmengeGeaendert() = runTest {
        val id = curryListe()
        db.zeile("c2", "c", "z-reis", "600", MeasureUnit.GRAM, 1)
        val z = zeilen(id).single()
        assertEquals(AbgleichArt.MEHR, z.art)
        assertEquals("300 g", z.alt)
        assertEquals("600 g", z.neu)
    }

    @Test
    fun ohneZeitraumLeer() = runTest {
        db.einkaufBestand()
        db.planen("s1", MONTAG, "c")
        val id = repo.antippen(null, "Milch", "Milch & Käse")
        assertTrue(zeilen(id).isEmpty())
    }

    @Test
    fun abgleichenWendetAn() = runTest {
        val id = curryListe()
        db.planen("s1", MONTAG, "c", portionen = 4)
        assertEquals(2, repo.abgleichen(id, emptySet()))
        assertTrue(zeilen(id).isEmpty())
        val z = artikel(id, "Zwiebeln")
        assertEquals(0, BigDecimal("400").compareTo(z.amount))
        val q = db.einkaufsartikelQuelleDao().nachArtikel(z.id).single()
        assertEquals(0, BigDecimal("400").compareTo(q.contributedAmount))
    }

    @Test
    fun abgleichenNeuUndEntfaellt() = runTest {
        val id = curryListe()
        db.rezeptDao().upsert(rezept("m", "Milchreis"))
        db.zeile("m1", "m", "z-milch", "500", MeasureUnit.MILLILITER, 0)
        db.planen("s2", MITTWOCH, "m")
        db.rezeptZutatDao().loeschen("c2")
        repo.abgleichen(id, emptySet())
        assertEquals(setOf("Zwiebeln", "Milch"), db.einkaufsartikelDao().nachListe(id).map { it.name }.toSet())
        assertEquals("Milchreis", db.einkaufsartikelQuelleDao().nachArtikel(artikel(id, "Milch").id).single().recipeName)
    }

    @Test
    fun abgleichenAbgewaehltIgnoriert() = runTest {
        val id = curryListe()
        db.planen("s1", MONTAG, "c", portionen = 4)
        assertEquals(1, repo.abgleichen(id, setOf("z-reis|MASS")))
        assertEquals(0, BigDecimal("300").compareTo(artikel(id, "Reis").amount))
        assertEquals(0, BigDecimal("400").compareTo(artikel(id, "Zwiebeln").amount))
        assertTrue(zeilen(id).isEmpty())
    }

    @Test
    fun flussMeldetPlanaenderung() = runTest {
        val id = curryListe()
        val werte = CopyOnWriteArrayList<List<AbgleichZeile>>()
        val sammler = backgroundScope.launch(Dispatchers.IO) { repo.abgleich(id).collect { werte += it } }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (werte.isEmpty()) delay(10) }
            db.planen("s1", MONTAG, "c", portionen = 4)
            withTimeout(5_000) { while (werte.last().isEmpty()) delay(10) }
        }
        assertEquals(setOf("Zwiebeln", "Reis"), werte.last().map { it.name }.toSet())
        sammler.cancel()
    }
}
