package de.foody.core.data.einkauf

import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezeptAufListeRepoTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: EinkaufRepository

    @Before
    fun setUp() {
        db = testDatenbank()
        repo = EinkaufRepository(db, { JETZT }, ignoriert())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun alle() = db.einkaufsartikelDao().alle()

    @Test
    fun ohneListeAngelegt() = runTest {
        db.einkaufBestand()
        assertEquals(2, repo.rezeptAufListe("p", 2))
        val l = db.einkaufslisteDao().alle().single()
        assertEquals("Einkauf", l.name)
        assertEquals(setOf("Zwiebeln", "Reis"), alle().map { it.name }.toSet())
        assertTrue(alle().all { it.manual && it.listId == l.id })
    }

    @Test
    fun addiertAufManuelle() = runTest {
        db.einkaufBestand()
        db.einkaufslisteDao().upsert(liste("l", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Reis", "100", MeasureUnit.GRAM, zutatId = "z-reis"))
        repo.rezeptAufListe("p", 2)
        val reis = alle().filter { it.name == "Reis" }
        assertEquals(1, reis.size)
        assertEquals(0, BigDecimal("500").compareTo(reis.single().amount))
    }

    @Test
    fun planUndGekaufteUnberuehrt() = runTest {
        db.einkaufBestand()
        db.einkaufslisteDao().upsert(liste("l", 1, MONTAG, MONTAG.plusDays(6)))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Reis", "100", MeasureUnit.GRAM, manuell = false, zutatId = "z-reis"))
        db.einkaufsartikelDao().upsert(artikel("a2", "l", "Reis", "50", MeasureUnit.GRAM, gekauft = true, zutatId = "z-reis"))
        repo.rezeptAufListe("p", 2)
        assertEquals(0, BigDecimal("100").compareTo(db.einkaufsartikelDao().nachId("a1")!!.amount))
        assertEquals(0, BigDecimal("50").compareTo(db.einkaufsartikelDao().nachId("a2")!!.amount))
        val neu = alle().single { it.name == "Reis" && it.id !in setOf("a1", "a2") }
        assertEquals(0, BigDecimal("400").compareTo(neu.amount))
        assertTrue(neu.manual)
    }

    @Test
    fun ohneWasser() = runTest {
        db.einkaufBestand()
        assertEquals(2, repo.rezeptAufListe("c", 2))
        assertEquals(setOf("Zwiebeln", "Reis"), alle().map { it.name }.toSet())
    }

    @Test
    fun rezeptFehltNull() = runTest {
        assertEquals(0, repo.rezeptAufListe("gibt-es-nicht", 2))
        assertTrue(db.einkaufslisteDao().alle().isEmpty())
    }

    @Test
    fun nichtsEinzukaufenKeineListe() = runTest {
        db.einkaufBestand()
        db.rezeptDao().upsert(rezept("w", "Tee"))
        db.zeile("w1", "w", "z-wasser", "500", MeasureUnit.MILLILITER, 0)
        assertEquals(0, repo.rezeptAufListe("w", 2))
        assertTrue(db.einkaufslisteDao().alle().isEmpty())
    }

    @Test
    fun skaliertPortionen() = runTest {
        db.einkaufBestand()
        repo.rezeptAufListe("p", 4)
        assertEquals(0, BigDecimal("800").compareTo(alle().single { it.name == "Reis" }.amount))
    }
}
