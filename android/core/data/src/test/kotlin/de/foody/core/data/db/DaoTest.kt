package de.foody.core.data.db

import androidx.room.Room
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Jede Tabelle lässt sich über ihr DAO schreiben, lesen und löschen; `upsert` ersetzt nie per DELETE. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DaoTest {
    private lateinit var db: FoodyDatenbank

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private val tag = LocalDate.of(2026, 10, 18)

    private val zutat = ZutatEntity(
        id = "z1",
        canonicalName = "Mehl",
        category = "Backen",
        densityGPerMl = BigDecimal("0.59"),
        nutrientBasis = NutrientBasis.PER_100_G,
        energyKj = BigDecimal("1456.5"),
        createdAt = 1,
        updatedAt = 2,
        barcode = "4000000000001",
    )

    private val rezept = RezeptEntity(
        id = "r1",
        name = "Brot",
        defaultServings = 4,
        prepMinutes = 10,
        cookMinutes = 45,
        imageUri = null,
        notes = "Notiz",
        tags = "backen",
        archivedAt = null,
        createdAt = 1,
        updatedAt = 2,
        favorite = true,
        rating = 5,
        mahlzeiten = "MITTAG",
        gaenge = "",
    )

    private val schritt = ArbeitsschrittEntity(id = "s1", recipeId = "r1", position = 1, text = "Kneten")

    private val liste = EinkaufslisteEntity(id = "l1", name = "Woche", rangeStart = tag, rangeEnd = tag.plusDays(6), createdAt = 1, updatedAt = 2)

    private val artikel = EinkaufsartikelEntity(
        id = "a1",
        listId = "l1",
        ingredientId = "z1",
        name = "Mehl",
        amount = BigDecimal("500"),
        unit = MeasureUnit.entries.first(),
        sortOrder = 0,
        note = "Bio",
        updatedAt = 3,
        checkedChangedAt = 4,
    )

    private val planEintrag = PlanEintragEntity(id = "p1", date = tag, slotType = "MITTAG", recipeId = "r1", servings = 2, createdAt = 1, updatedAt = 2)

    @Test
    fun zutaten() = runTest {
        val dao = db.zutatDao()
        dao.upsert(zutat)
        assertEquals(listOf(zutat), dao.alle())
        assertEquals(zutat, dao.nachId("z1"))
        dao.loeschen("z1")
        assertNull(dao.nachId("z1"))
    }

    @Test
    fun rezepteUndKinder() = runTest {
        db.zutatDao().upsert(zutat)
        db.rezeptDao().upsert(rezept)
        val rz = RezeptZutatEntity("ri1", "r1", "z1", BigDecimal("250.5"), MeasureUnit.entries.last(), 0, "gesiebt", optional = true)
        db.rezeptZutatDao().upsert(rz)
        db.arbeitsschrittDao().upsert(schritt)
        assertEquals(listOf(rezept), db.rezeptDao().alle())
        assertEquals(rezept, db.rezeptDao().nachId("r1"))
        assertEquals(listOf(rz), db.rezeptZutatDao().alle())
        assertEquals(rz, db.rezeptZutatDao().nachId("ri1"))
        assertEquals(listOf(schritt), db.arbeitsschrittDao().alle())
        assertEquals(schritt, db.arbeitsschrittDao().nachId("s1"))
        db.rezeptZutatDao().loeschen("ri1")
        db.arbeitsschrittDao().loeschen("s1")
        db.rezeptDao().loeschen("r1")
        assertEquals(emptyList(), db.rezeptDao().alle())
        assertEquals(emptyList(), db.rezeptZutatDao().alle())
        assertEquals(emptyList(), db.arbeitsschrittDao().alle())
    }

    @Test
    fun upsertAufRezeptLoeschtNichts() = runTest {
        db.rezeptDao().upsert(rezept)
        db.arbeitsschrittDao().upsert(schritt)
        db.planEintragDao().upsert(planEintrag)
        val geaendert = rezept.copy(name = "Vollkornbrot", updatedAt = 3)
        db.rezeptDao().upsert(geaendert)
        assertEquals(listOf(geaendert), db.rezeptDao().alle())
        // REPLACE würde löschen und neu einfügen – die Kinder fielen per CASCADE weg.
        assertEquals(listOf(schritt), db.arbeitsschrittDao().alle())
        assertEquals(listOf(planEintrag), db.planEintragDao().alle())
    }

    @Test
    fun planEintraege() = runTest {
        db.rezeptDao().upsert(rezept)
        val dao = db.planEintragDao()
        dao.upsert(planEintrag)
        assertEquals(listOf(planEintrag), dao.alle())
        assertEquals(planEintrag, dao.nachId("p1"))
        dao.loeschen("p1")
        assertNull(dao.nachId("p1"))
    }

    @Test
    fun einkauf() = runTest {
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        val quelle = EinkaufsartikelQuelleEntity("q1", "a1", "p1", "ri1", "Brot", tag, BigDecimal("0.25"), MeasureUnit.entries.first())
        db.einkaufsartikelQuelleDao().upsert(quelle)
        assertEquals(listOf(liste), db.einkaufslisteDao().alle())
        assertEquals(liste, db.einkaufslisteDao().nachId("l1"))
        assertEquals(listOf(artikel), db.einkaufsartikelDao().alle())
        assertEquals(artikel, db.einkaufsartikelDao().nachId("a1"))
        assertEquals(listOf(quelle), db.einkaufsartikelQuelleDao().alle())
        assertEquals(quelle, db.einkaufsartikelQuelleDao().nachId("q1"))
        db.einkaufsartikelQuelleDao().loeschen("q1")
        db.einkaufsartikelDao().loeschen("a1")
        db.einkaufslisteDao().loeschen("l1")
        assertNull(db.einkaufsartikelQuelleDao().nachId("q1"))
        assertNull(db.einkaufsartikelDao().nachId("a1"))
        assertNull(db.einkaufslisteDao().nachId("l1"))
    }

    @Test
    fun tagebuch() = runTest {
        val eintrag = TagebuchEintragEntity(
            id = "t1",
            datum = tag,
            mahlzeit = "MITTAG",
            art = TagebuchArt.ZUTAT,
            name = "Apfel",
            zutatId = "z9",
            menge = BigDecimal("150"),
            einheit = MeasureUnit.entries.first(),
            energieKj = BigDecimal("330"),
            vollstaendig = false,
            createdAt = 1,
            updatedAt = 2,
        )
        val dao = db.tagebuchDao()
        dao.upsert(eintrag)
        assertEquals(listOf(eintrag), dao.alle())
        assertEquals(eintrag, dao.nachId("t1"))
        dao.loeschen("t1")
        assertNull(dao.nachId("t1"))
    }

    @Test
    fun syncTabellen() = runTest {
        val dao = db.syncDao()

        val outbox = SyncOutboxEntity(type = "recipe", recordId = "r1", deleted = false, queuedAt = 5)
        dao.upsertOutbox(outbox)
        dao.upsertOutbox(outbox.copy(deleted = true, queuedAt = 6))
        assertEquals(listOf(outbox.copy(deleted = true, queuedAt = 6)), dao.outbox())
        assertEquals(outbox.copy(deleted = true, queuedAt = 6), dao.outboxEintrag("recipe", "r1"))
        dao.loeschenOutbox("recipe", "r1")
        assertEquals(emptyList(), dao.outbox())

        val rev = SyncRevisionEntity(type = "recipe", recordId = "r1", rev = 7)
        dao.upsertRevision(rev)
        assertEquals(listOf(rev), dao.revisionen())
        assertEquals(rev, dao.revision("recipe", "r1"))
        dao.loeschenRevision("recipe", "r1")
        assertNull(dao.revision("recipe", "r1"))

        assertNull(dao.zustand())
        val zustand = SyncZustandEntity(active = true, serverUrl = "https://foody.example", kontoId = "k1", cursor = 42, lastSyncAt = 9, lastError = "offline")
        dao.upsertZustand(zustand)
        assertEquals(zustand, dao.zustand())

        val problem = SyncProblemEntity(type = "recipe", recordId = "r1", code = "invalid", at = 8)
        dao.upsertProblem(problem)
        assertEquals(listOf(problem), dao.probleme())
        assertEquals(problem, dao.problem("recipe", "r1"))
        dao.loeschenProblem("recipe", "r1")
        assertEquals(emptyList(), dao.probleme())

        val lokal = SyncFotoLokalEntity(uri = "file:///a.jpg", sha256 = "ab", size = 10, modifiedAt = 11)
        dao.upsertFotoLokal(lokal)
        assertEquals(listOf(lokal), dao.fotosLokal())
        assertEquals(lokal, dao.fotoLokal("file:///a.jpg"))
        dao.loeschenFotoLokal("file:///a.jpg")
        assertNull(dao.fotoLokal("file:///a.jpg"))

        val gewuenscht = SyncFotoGewuenschtEntity(recipeId = "r1", sha256 = "cd")
        dao.upsertFotoGewuenscht(gewuenscht)
        assertEquals(listOf(gewuenscht), dao.fotosGewuenscht())
        assertEquals(gewuenscht, dao.fotoGewuenscht("r1"))
        dao.loeschenFotoGewuenscht("r1")
        assertEquals(emptyList(), dao.fotosGewuenscht())
    }
}
