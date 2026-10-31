package de.foody.core.data.woche

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.heute.plan
import de.foody.core.data.heute.rezept
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.Mahlzeit
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class WocheVorschlagTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: WocheRepository
    private val montag = LocalDate.of(2026, 10, 26)
    private val mittwoch = montag.plusDays(2)
    private val hauptspeisen = listOf("h1" to "Gemüsecurry", "h2" to "Linsenpasta", "h3" to "Pilzrisotto", "h4" to "Bohneneintopf", "h5" to "Kartoffelauflauf")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        val rezepte = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1L }
        repo = WocheRepository(db, rezepte, { 5_000L }, EmptyCoroutineContext)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun hauptspeisen(archiviert: String? = null) = hauptspeisen.forEach { (id, name) ->
        db.rezeptDao().upsert(
            rezept(id, name, archiviert = if (id == archiviert) 5L else null).copy(mahlzeiten = "ABENDESSEN", gaenge = "HAUPTSPEISE"),
        )
    }

    private val wocheAbMittwoch get() = (2L..6L).map { montag.plusDays(it) }

    @Test
    fun leereTageNurAbHeute() = runTest {
        hauptspeisen()
        assertEquals(wocheAbMittwoch, repo.leereTage(montag, Mahlzeit.ABENDESSEN, mittwoch))
    }

    @Test
    fun belegterTagFehlt() = runTest {
        hauptspeisen()
        // Eigener Name in anderer Schreibweise zählt als Abendessen
        db.planEintragDao().upsert(plan("p1", mittwoch, "abendessen", "h1"))
        assertFalse(mittwoch in repo.leereTage(montag, Mahlzeit.ABENDESSEN, mittwoch))
        assertTrue(mittwoch in repo.leereTage(montag, Mahlzeit.MITTAGESSEN, mittwoch))
    }

    @Test
    fun gleicherSeedGleichesErgebnis() = runTest {
        hauptspeisen()
        val a = repo.vorschlagen(wocheAbMittwoch, Mahlzeit.ABENDESSEN, 42)
        assertEquals(5, a.size)
        assertEquals(a, repo.vorschlagen(wocheAbMittwoch, Mahlzeit.ABENDESSEN, 42))
    }

    @Test
    fun vierzehnTageSperre() = runTest {
        hauptspeisen()
        db.planEintragDao().upsert(plan("p1", mittwoch.minusDays(10), "ABENDESSEN", "h1"))
        val v = repo.vorschlagen(wocheAbMittwoch, Mahlzeit.ABENDESSEN, 7)
        assertFalse("h1" in v.values)
        assertEquals(4, v.size)
    }

    @Test
    fun vorgemerkteZaehlenInDieAbwechslung() = runTest {
        // Am Folgetag sind Spaghetti vorgeschlagen (noch nicht angelegt): Penne fallen weg, solange es Curry gibt
        listOf("n1" to "Spaghetti Bolognese", "n2" to "Penne Arrabbiata", "c1" to "Gemüsecurry").forEach { (id, name) ->
            db.rezeptDao().upsert(rezept(id, name).copy(mahlzeiten = "ABENDESSEN", gaenge = "HAUPTSPEISE"))
        }
        val vorgemerkt = mapOf(mittwoch.plusDays(1) to "n1")
        val ohne = (0L..19L).map { repo.vorschlagen(listOf(mittwoch), Mahlzeit.ABENDESSEN, it, setOf("n1"))[mittwoch] }.toSet()
        assertEquals(setOf("n2", "c1"), ohne)
        (0L..19L).forEach { seed ->
            assertEquals("c1", repo.vorschlagen(listOf(mittwoch), Mahlzeit.ABENDESSEN, seed, setOf("n1"), vorgemerkt)[mittwoch])
        }
    }

    @Test
    fun archivierteNie() = runTest {
        hauptspeisen(archiviert = "h5")
        val v = repo.vorschlagen(wocheAbMittwoch, Mahlzeit.ABENDESSEN, 7)
        assertFalse("h5" in v.values)
    }

    @Test
    fun ohnePassendeLeer() = runTest {
        db.rezeptDao().upsert(rezept("n1", "Grießpudding").copy(mahlzeiten = "ABENDESSEN", gaenge = "NACHSPEISE"))
        assertEquals(emptyMap(), repo.vorschlagen(wocheAbMittwoch, Mahlzeit.ABENDESSEN, 7))
    }

    @Test
    fun uebernehmenInEinerTransaktion() = runTest {
        hauptspeisen()
        val n = repo.uebernehmen(mapOf(mittwoch to "h1", mittwoch.plusDays(1) to "h2", mittwoch.plusDays(2) to "h3"), Mahlzeit.ABENDESSEN)
        assertEquals(3, n)
        val alle = db.planEintragDao().alle()
        assertEquals(3, alle.size)
        assertTrue(alle.all { it.slotType == "ABENDESSEN" && it.servings == 2 })
    }

    @Test
    fun uebernehmenUeberspringtGeloeschte() = runTest {
        hauptspeisen()
        db.rezeptDao().loeschen("h2")
        val n = repo.uebernehmen(mapOf(mittwoch to "h1", mittwoch.plusDays(1) to "h2", mittwoch.plusDays(2) to "h3"), Mahlzeit.ABENDESSEN)
        assertEquals(2, n)
        assertEquals(setOf("h1", "h3"), db.planEintragDao().alle().map { it.recipeId }.toSet())
    }
}
