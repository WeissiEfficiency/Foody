package de.foody.core.data.tagebuch

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.bestand
import de.foody.core.data.heute.plan
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TagebuchRepositoryTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: TagebuchRepository
    private val heute = LocalDate.of(2026, 10, 24)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        repo = TagebuchRepository(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun kj(id: String) = db.tagebuchDao().nachId(id)?.energieKj

    @Test
    fun ausRezeptHaeltWerteFest() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        val vorher = kj(id)
        assertEquals(0, BigDecimal("3000").compareTo(vorher))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l1", "r1", "z-reis", BigDecimal("800"), MeasureUnit.GRAM, 0, null, false))
        assertEquals(vorher, kj(id))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.REZEPT, e.art)
        assertEquals("Reispfanne", e.name)
        assertEquals(1_000L, e.createdAt)
    }

    @Test
    fun ausZutatOhneNaehrwerteUnvollstaendig() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "z-x", canonicalName = "Unbekannt", createdAt = 1, updatedAt = 1))
        val id = assertNotNull(repo.ausZutat(heute, Mahlzeit.SNACK, "z-x", BigDecimal("50"), MeasureUnit.GRAM))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.ZUTAT, e.art)
        assertNull(e.energieKj)
        assertFalse(e.vollstaendig)
    }

    @Test
    fun ausPlanEinePortionUndNurEinmal() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", heute, "Abendessen", "r1", portionen = 4))
        val id = assertNotNull(repo.ausPlan("p1"))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals("p1", e.planEintragId)
        assertEquals("r1", e.rezeptId)
        assertEquals(0, BigDecimal.ONE.compareTo(e.portionen))
        assertEquals(Mahlzeit.ABENDESSEN.name, e.mahlzeit)
        assertEquals(heute, e.datum)
        assertNull(repo.ausPlan("p1"), "zweites Übernehmen legt nichts an")
        assertEquals(1, db.tagebuchDao().alle().size)
    }

    @Test
    fun ausPlanOhnePlanEintragGibtNull() = runTest {
        assertNull(repo.ausPlan("fehlt"))
    }

    @Test
    fun freiMitKcal() = runTest {
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.FREI, e.art)
        assertEquals(52, de.foody.domain.Naehrwerte(e.energieKj, null, null, null, true).kcal)
        assertTrue(e.vollstaendig)
    }

    @Test
    fun aendernSkaliert() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        repo.aendern(id, BigDecimal(2), Mahlzeit.MITTAGESSEN, heute.plusDays(1), null)
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(0, BigDecimal("6000").compareTo(e.energieKj))
        assertEquals(0, BigDecimal(2).compareTo(e.portionen))
        assertEquals(Mahlzeit.MITTAGESSEN.name, e.mahlzeit)
        assertEquals(heute.plusDays(1), e.datum)
    }

    @Test
    fun loeschenUndWiederherstellen() = runTest {
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        val weg = assertNotNull(repo.loeschen(id))
        assertNull(db.tagebuchDao().nachId(id))
        repo.wiederherstellen(weg)
        assertEquals(weg, db.tagebuchDao().nachId(id))
        assertEquals(listOf(id), repo.amTag(heute).first().map { it.id })
    }

    @Test
    fun beiAktivemSyncOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        assertTrue(db.syncDao().outbox().any { it.type == "tagebuch_eintrag" && it.recordId == id })
    }

    @Test
    fun geloeschtesRezeptBehaeltEintrag() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        db.rezeptDao().loeschen("r1")
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals("Reispfanne", e.name)
        assertEquals(0, BigDecimal("3000").compareTo(e.energieKj))
    }
}
