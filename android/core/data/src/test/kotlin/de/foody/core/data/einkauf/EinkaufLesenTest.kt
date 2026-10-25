package de.foody.core.data.einkauf

import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufLesenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: EinkaufRepository

    @Before
    fun setUp() {
        db = testDatenbank()
        repo = EinkaufRepository(db, { JETZT }, ignoriert())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ansicht(id: String) = assertNotNull(repo.liste(id).first())

    @Test
    fun aktuelleNachAngelegt() = runTest {
        db.einkaufslisteDao().upsert(liste("b", angelegt = 20))
        db.einkaufslisteDao().upsert(liste("a", angelegt = 10).copy(updatedAt = 99))
        assertEquals("b", repo.aktuelleListeId().first())
    }

    @Test
    fun ohneListeNull() = runTest {
        assertNull(repo.aktuelleListeId().first())
        assertNull(repo.liste("gibt-es-nicht").first())
    }

    @Test
    fun offenNachAbteilungInLaufreihenfolge() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Grillkohle", reihenfolge = 0))
        db.einkaufsartikelDao().upsert(artikel("a2", "l", "Reis", reihenfolge = 1))
        db.einkaufsartikelDao().upsert(artikel("a3", "l", "Milch", reihenfolge = 2))
        db.einkaufsartikelDao().upsert(artikel("a4", "l", "Zwiebeln", reihenfolge = 4))
        db.einkaufsartikelDao().upsert(artikel("a5", "l", "Tomaten", reihenfolge = 3))
        val offen = ansicht("l").offen
        assertEquals(listOf("Obst & Gemüse", "Milch & Käse", "Getreideprodukte", "Eigene Artikel"), offen.map { it.name })
        assertEquals(listOf("Tomaten", "Zwiebeln"), offen.first().artikel.map { it.name })
        assertEquals("🛒", offen.last().emoji)
        assertEquals("🧅", offen.first().artikel.last().emoji)
    }

    @Test
    fun gekaufteNichtOffen() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        db.einkaufsartikelDao().upsert(artikel("a2", "l", "Reis", gekauft = true))
        val a = ansicht("l")
        assertEquals(listOf("Milch"), a.offen.flatMap { it.artikel }.map { it.name })
        assertEquals(1, a.anzahlOffen)
        assertEquals(2, a.alle.size)
        assertEquals(true, a.alle.single { it.name == "Reis" }.gekauft)
    }

    @Test
    fun mengeUndHerkunftText() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1, MONTAG, MONTAG.plusDays(6)))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Zwiebeln", "300", MeasureUnit.GRAM, manuell = false, zutatId = "z-zwiebel"))
        db.einkaufsartikelQuelleDao().upsert(quelle("q2", "a1", "Reispfanne", MITTWOCH, "100"))
        db.einkaufsartikelQuelleDao().upsert(quelle("q1", "a1", "Curry", MONTAG, "200"))
        val z = ansicht("l").alle.single()
        assertEquals("300 g", z.menge)
        assertEquals("200 g Curry (Mo) + 100 g Reispfanne (Mi)", z.herkunft)
        assertEquals(true, z.plan)
        assertEquals("z-zwiebel", z.ingredientId)
    }

    @Test
    fun ohneMengeKeinText() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        val m = ansicht("l").alle.single()
        assertNull(m.menge)
        assertNull(m.herkunft)
    }

    @Test
    fun zuletztUeberAlleListen() = runTest {
        db.einkaufslisteDao().upsert(liste("alt", 1))
        db.einkaufslisteDao().upsert(liste("neu", 2))
        db.einkaufsartikelDao().upsert(artikel("a1", "alt", "Milch", gekauft = true, gekauftUm = 10))
        db.einkaufsartikelDao().upsert(artikel("a2", "alt", "Reis", gekauft = true, gekauftUm = 30))
        db.einkaufsartikelDao().upsert(artikel("b1", "neu", "Milch", gekauft = true, gekauftUm = 20))
        db.einkaufsartikelDao().upsert(artikel("b2", "neu", "Zwiebeln"))
        assertEquals(listOf("Reis", "Milch"), repo.zuletzt().first().map { it.name })
    }

    @Test
    fun zuletztHoechstens18() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1))
        repeat(20) { i -> db.einkaufsartikelDao().upsert(artikel("a$i", "l", "Artikel $i", gekauft = true, gekauftUm = i.toLong())) }
        val z = repo.zuletzt().first()
        assertEquals(18, z.size)
        assertEquals("Artikel 19", z.first().name)
    }

    @Test
    fun fruehereOhneAktuelle() = runTest {
        db.einkaufslisteDao().upsert(liste("eins", 1, name = "12.10.–18.10."))
        db.einkaufslisteDao().upsert(liste("zwei", 2))
        db.einkaufslisteDao().upsert(liste("drei", 3))
        db.einkaufsartikelDao().upsert(artikel("a1", "eins", "Milch"))
        db.einkaufsartikelDao().upsert(artikel("a2", "eins", "Reis", gekauft = true))
        val f = repo.fruehere().first()
        assertEquals(listOf("zwei", "eins"), f.map { it.id })
        assertEquals(2, f.last().artikel)
        assertEquals("12.10.–18.10.", f.last().name)
    }

    @Test
    fun listenNameOhneNullen() {
        assertEquals("26.10.–1.11.", listenName(MONTAG, MONTAG.plusDays(6)))
        assertEquals("1.1.–7.1.", listenName(java.time.LocalDate.of(2027, 1, 1), java.time.LocalDate.of(2027, 1, 7)))
    }

    @Test
    fun grammUndMilliliterGanzzahlig() {
        // Im Laden kauft niemand 217,71 g Butter
        assertEquals("218 g", mengeText(java.math.BigDecimal("217.71"), MeasureUnit.GRAM))
        assertEquals("1 g", mengeText(java.math.BigDecimal("0.4"), MeasureUnit.GRAM))
        assertEquals("333 ml", mengeText(java.math.BigDecimal("333.333"), MeasureUnit.MILLILITER))
        assertEquals("1,8 kg", mengeText(java.math.BigDecimal("1800"), MeasureUnit.GRAM))
        assertEquals("1,5 l", mengeText(java.math.BigDecimal("1500"), MeasureUnit.MILLILITER))
        assertEquals("3 Stk.", mengeText(java.math.BigDecimal("3"), MeasureUnit.PIECE))
    }

    @Test
    fun zuletztBleibtBeiLangerHistorieSchnell() = runTest {
        // Ein Jahr Einkauf: viele gekaufte Artikel mit vielen Namen – „Zuletzt“ darf im Laden nicht ruckeln
        db.einkaufslisteDao().upsert(liste("l", 1))
        repeat(3000) { i ->
            db.einkaufsartikelDao().upsert(artikel("a$i", "l", "Artikel ${i % 600}", gekauft = true, gekauftUm = i.toLong()))
        }
        val start = System.nanoTime()
        val z = repo.zuletzt().first()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(18, z.size)
        assertEquals("Artikel 599", z.first().name)
        kotlin.test.assertTrue(ms < 1_000, "zuletzt dauerte $ms ms")
    }
}
