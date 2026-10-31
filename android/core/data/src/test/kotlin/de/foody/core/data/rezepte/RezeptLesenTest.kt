package de.foody.core.data.rezepte

import androidx.room.Room
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.bestand
import de.foody.core.data.heute.plan
import de.foody.core.data.heute.rezept
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import kotlin.coroutines.EmptyCoroutineContext
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezeptLesenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: RezeptRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        repo = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun karteMitKcalUndGekocht() = runTest {
        db.bestand()
        val tag = LocalDate.of(2026, 10, 24)
        db.planEintragDao().upsert(plan("p1", tag, "ABENDESSEN", "r1").copy(cookedAt = 5))
        db.planEintragDao().upsert(plan("p2", tag.plusDays(1), "ABENDESSEN", "r1").copy(cookedAt = 6))
        db.planEintragDao().upsert(plan("p3", tag.plusDays(2), "ABENDESSEN", "r1"))
        val k = repo.karten().first().single()
        assertEquals(717, k.kcalJePortion)
        assertEquals(2, k.gekocht)
        assertEquals(30, k.zeitMinuten)
    }

    @Test
    fun gleicheKcalWieHeute() = runTest {
        db.bestand()
        assertEquals(HeuteRepository(db, EmptyCoroutineContext).rezeptVorschlaege().first().single().kcalJePortion, repo.karten().first().single().kcalJePortion)
    }

    @Test
    fun vollMitZeilenSchrittenUndNaehrwerten() = runTest {
        db.bestand()
        db.zutatDao().upsert(ZutatEntity(id = "z-salz", canonicalName = "Salz", createdAt = 1, updatedAt = 1))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l0", "r1", "z-salz", BigDecimal.ZERO, MeasureUnit.PIECE, -1, "1 Prise", false))
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s2", "r1", 1, "Braten"))
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 0, "Kochen"))
        val v = repo.voll("r1").first()!!
        assertEquals(listOf("Salz", "Reis"), v.zeilen.map { it.name })
        assertEquals(listOf(false, true), v.zeilen.map { it.hatNaehrwerte })
        assertEquals(listOf("Kochen", "Braten"), v.schritte)
        assertEquals(0, BigDecimal("3000").compareTo(v.naehrwerte.perServing(Nutrient.ENERGY_KJ)))
    }

    @Test
    fun vollUnbekanntGibtNull() = runTest {
        assertNull(repo.voll("gibt-es-nicht").first())
    }

    @Test
    fun tagsUndZutatNamen() = runTest {
        db.bestand()
        db.rezeptDao().upsert(rezept("r1", "Reispfanne").copy(tags = " schnell, ,vegan "))
        val k = repo.karten().first().single()
        assertEquals(listOf("schnell", "vegan"), k.tags)
        assertEquals(listOf("Reis"), k.zutatNamen)
    }

    @Test
    fun archiviertBleibtInKarten() = runTest {
        db.bestand()
        db.rezeptDao().upsert(rezept("r2", "Alt", archiviert = 9))
        assertTrue(repo.karten().first().single { it.id == "r2" }.archiviert)
    }
}
