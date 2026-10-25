package de.foody.core.data.kochen

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.SyncZustandEntity
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochenRepositoryTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: KochenRepository
    private val heute = LocalDate.of(2026, 10, 25)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        repo = KochenRepository(db) { 5_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun rezept(id: String) = db.rezeptDao().upsert(
        RezeptEntity(
            id = id, name = id, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
            notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
        ),
    )

    private suspend fun plan(id: String, rezeptId: String, datum: LocalDate = heute, slot: String = "DINNER") =
        db.planEintragDao().upsert(PlanEintragEntity(id, datum, slot, rezeptId, 2, null, 1, 1))

    @Test
    fun markiertHeutigenEintrag() = runTest {
        rezept("r1")
        plan("p1", "r1")
        assertTrue(repo.gekochtMarkieren("r1", heute))
        val p = db.planEintragDao().nachId("p1")!!
        assertEquals(5_000L, p.cookedAt)
        assertEquals(5_000L, p.updatedAt)
    }

    @Test
    fun nurHeuteUndDiesesRezept() = runTest {
        rezept("r1")
        rezept("r2")
        plan("gestern", "r1", heute.minusDays(1))
        plan("anderes", "r2")
        assertFalse(repo.gekochtMarkieren("r1", heute))
        assertNull(db.planEintragDao().nachId("gestern")?.cookedAt)
        assertNull(db.planEintragDao().nachId("anderes")?.cookedAt)
    }

    @Test
    fun zweiAufrufeZweiEintraege() = runTest {
        rezept("r1")
        plan("p1", "r1", slot = "LUNCH")
        plan("p2", "r1", slot = "DINNER")
        assertTrue(repo.gekochtMarkieren("r1", heute))
        assertEquals(1, db.planEintragDao().alle().count { it.cookedAt != null })
        assertTrue(repo.gekochtMarkieren("r1", heute))
        assertEquals(2, db.planEintragDao().alle().count { it.cookedAt != null })
        assertFalse(repo.gekochtMarkieren("r1", heute))
    }

    @Test
    fun ohneEintragFalse() = runTest {
        rezept("r1")
        assertFalse(repo.gekochtMarkieren("r1", heute))
    }

    @Test
    fun landetInOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        rezept("r1")
        plan("p1", "r1")
        db.syncDao().outboxLeeren()
        repo.gekochtMarkieren("r1", heute)
        assertTrue(db.syncDao().outbox().any { it.type == "meal_slot" && it.recordId == "p1" })
    }
}
