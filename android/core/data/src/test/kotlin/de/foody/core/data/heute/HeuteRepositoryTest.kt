package de.foody.core.data.heute

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class HeuteRepositoryTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: HeuteRepository
    private val heute = LocalDate.of(2026, 10, 24)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        repo = HeuteRepository(db, EmptyCoroutineContext)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun planHeuteUndMorgenNurMitRezept() = runTest {
        db.bestand()
        db.rezeptDao().upsert(rezept("r-alt", "Archiviert", archiviert = 5))
        db.planEintragDao().upsert(plan("p1", heute, "Abendessen", "r1"))
        db.planEintragDao().upsert(plan("p2", heute, "Mittagessen", "r-alt"))
        db.planEintragDao().upsert(plan("p3", heute.plusDays(1), "Frühstück", "r1"))
        db.planEintragDao().upsert(plan("p4", heute.minusDays(1), "Frühstück", "r1"))
        val geplant = repo.planHeuteUndMorgen(heute).first()
        assertEquals(listOf("p1", "p3"), geplant.map { it.planEintragId }.sorted())
        assertEquals("Reispfanne", geplant.first { it.planEintragId == "p1" }.titel)
        assertEquals(30, geplant.first().zeitMinuten)
    }

    @Test
    fun geloeschtesRezeptFehlt() = runTest {
        db.bestand()
        db.rezeptDao().upsert(rezept("r2", "Weg"))
        db.planEintragDao().upsert(plan("p1", heute, "Abendessen", "r2"))
        db.rezeptDao().loeschen("r2")
        assertEquals(emptyList(), repo.planHeuteUndMorgen(heute).first())
    }

    @Test
    fun kcalJePortionWieDomain() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", heute, "Abendessen", "r1"))
        // 400 g Reis = 6000 kJ für 2 Portionen → 3000 kJ ≈ 717 kcal
        assertEquals(717, repo.planHeuteUndMorgen(heute).first().single().kcalJePortion)
        assertEquals(717, repo.rezeptVorschlaege().first().single().kcalJePortion)
    }

    @Test
    fun offenerEinkaufJuengsteListe() = runTest {
        // „alt“ wurde zuletzt geändert, „neu“ später angelegt: es zählt das Anlegen
        db.einkaufslisteDao().upsert(liste("alt", angelegt = 20, geaendert = 99))
        db.einkaufslisteDao().upsert(liste("neu", angelegt = 30))
        db.einkaufsartikelDao().upsert(artikel("a1", "alt", "Milch"))
        db.einkaufsartikelDao().upsert(artikel("b1", "neu", "Paprika", reihenfolge = 1))
        db.einkaufsartikelDao().upsert(artikel("b2", "neu", "Tomaten", reihenfolge = 0))
        db.einkaufsartikelDao().upsert(artikel("b3", "neu", "Salz", erledigt = true))
        val offen = assertNotNull(repo.offenerEinkauf().first())
        assertEquals("neu", offen.listeId)
        assertEquals(2, offen.anzahl)
        assertEquals(listOf("Tomaten", "Paprika"), offen.artikel.map { it.name })
        assertEquals("500 g", offen.artikel.first().menge)
    }

    @Test
    fun keinOffenerEinkaufGibtNull() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 10))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch", erledigt = true))
        assertNull(repo.offenerEinkauf().first())
    }
}
