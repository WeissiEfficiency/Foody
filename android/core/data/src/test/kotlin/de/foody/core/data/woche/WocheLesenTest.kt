package de.foody.core.data.woche

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.heute.bestand
import de.foody.core.data.heute.plan
import de.foody.core.data.heute.rezept
import de.foody.core.data.rezepte.RezeptRepository
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
class WocheLesenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: WocheRepository
    private val montag = LocalDate.of(2026, 10, 26)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        val rezepte = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1_000L }
        repo = WocheRepository(db, rezepte, { 1_000L }, EmptyCoroutineContext)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun siebenTageAbMontag() = runTest {
        val w = repo.woche(montag).first()
        assertEquals(montag, w.montag)
        assertEquals((0L..6L).map { montag.plusDays(it) }, w.tage.map { it.datum })
    }

    @Test
    fun reihenfolgeDerMahlzeiten() = runTest {
        db.bestand()
        listOf("Abendessen", "FRUEHSTUECK", "Brotzeit am See", "SNACK").forEachIndexed { i, slot ->
            db.planEintragDao().upsert(plan("p$i", montag, slot, "r1"))
        }
        val eintraege = repo.woche(montag).first().tage[0].eintraege
        assertEquals(listOf("FRUEHSTUECK", "SNACK", "Abendessen", "Brotzeit am See"), eintraege.map { it.mahlzeit })
        assertEquals("Reispfanne", eintraege[0].rezeptName)
        assertEquals(717, eintraege[0].kcalJePortion)
    }

    @Test
    fun tagesKcalJePerson() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", montag, "MITTAGESSEN", "r1", portionen = 2))
        db.planEintragDao().upsert(plan("p2", montag, "ABENDESSEN", "r1", portionen = 4))
        val tag = repo.woche(montag).first().tage[0]
        assertEquals(1434, tag.naehrwerte?.kcal)
        assertNull(repo.woche(montag).first().tage[1].naehrwerte)
    }

    @Test
    fun geloeschtesRezeptVerschwindetMitSeinenEintraegen() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", montag, "ABENDESSEN", "r1"))
        db.rezeptDao().loeschen("r1")
        assertEquals(emptyList(), repo.woche(montag).first().tage[0].eintraege)
    }

    @Test
    fun durchschnittNurVolleTage() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", montag, "ABENDESSEN", "r1"))
        assertNull(repo.woche(montag).first().durchschnitt)
        db.planEintragDao().upsert(plan("p2", montag, "MITTAGESSEN", "r1"))
        val d = assertNotNull(repo.woche(montag).first().durchschnitt)
        assertEquals(1434, d.kcal)
        assertEquals(1, d.tage)
    }

    @Test
    fun auswahlOhneArchivierte() = runTest {
        db.bestand()
        db.rezeptDao().upsert(rezept("r2", "Alt", archiviert = 5))
        assertEquals(listOf("r1"), repo.rezeptAuswahl().first().map { it.id })
    }
}
