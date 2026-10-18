package de.foody.core.data.db

import androidx.room.Room
import de.foody.domain.MeasureUnit
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Die Sync-Trigger merken lokale Änderungen in `sync_outbox` vor – nur bei aktivem Sync und nie beim Anwenden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SyncTriggerTest {
    private lateinit var db: FoodyDatenbank

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
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
        densityGPerMl = null,
        nutrientBasis = de.foody.domain.NutrientBasis.PER_100_G,
        energyKj = null,
        createdAt = 1,
        updatedAt = 2,
    )

    private val rezept = RezeptEntity(
        id = "r1",
        name = "Brot",
        defaultServings = 4,
        prepMinutes = null,
        cookMinutes = null,
        imageUri = null,
        notes = null,
        tags = "",
        archivedAt = null,
        createdAt = 1,
        updatedAt = 2,
    )

    private val liste = EinkaufslisteEntity(id = "l1", name = "Woche", rangeStart = tag, rangeEnd = tag.plusDays(6), createdAt = 1, updatedAt = 2)

    private val artikel = EinkaufsartikelEntity(id = "a1", listId = "l1", name = "Mehl", sortOrder = 0, updatedAt = 5, checkedChangedAt = 5)

    private suspend fun syncAktiv(active: Boolean = true, applyingRemote: Boolean = false) =
        db.syncDao().upsertZustand(SyncZustandEntity(active = active, applyingRemote = applyingRemote))

    private fun outboxLeeren() = db.openHelper.writableDatabase.execSQL("DELETE FROM sync_outbox")

    private suspend fun outbox() = db.syncDao().outbox().map { Triple(it.type, it.recordId, it.deleted) }

    @Test
    fun onCreateLegtZustandUndAlleTriggerAn() = runTest {
        assertEquals(SyncZustandEntity(), db.syncDao().zustand())
        val vorhanden = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals(SyncTrigger.namen.toSet(), vorhanden)
        assertEquals(SyncTrigger.anweisungen.size, SyncTrigger.namen.size)
    }

    @Test
    fun inaktiverSyncSchreibtKeineOutbox() = runTest {
        db.rezeptDao().upsert(rezept)
        db.zutatDao().upsert(zutat)
        db.rezeptDao().loeschen("r1")
        assertEquals(emptyList(), outbox())
    }

    @Test
    fun aenderungMerktWurzelVor() = runTest {
        db.zutatDao().upsert(zutat)
        syncAktiv()
        db.zutatDao().upsert(zutat.copy(canonicalName = "Weizenmehl"))
        assertEquals(listOf(Triple("ingredient", "z1", false)), outbox())
    }

    @Test
    fun jedeWurzelWirdVorgemerkt() = runTest {
        syncAktiv()
        db.zutatDao().upsert(zutat)
        db.rezeptDao().upsert(rezept)
        db.planEintragDao().upsert(PlanEintragEntity(id = "p1", date = tag, slotType = "MITTAG", recipeId = "r1", servings = 2, createdAt = 1, updatedAt = 2))
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        db.tagebuchDao().upsert(TagebuchEintragEntity(id = "t1", datum = tag, mahlzeit = "MITTAG", art = de.foody.domain.TagebuchArt.entries.first(), name = "Brot", createdAt = 1, updatedAt = 2))
        assertEquals(
            setOf("ingredient" to "z1", "recipe" to "r1", "meal_slot" to "p1", "shopping_list" to "l1", "shopping_item" to "a1", "tagebuch_eintrag" to "t1"),
            outbox().map { it.first to it.second }.toSet(),
        )
    }

    @Test
    fun loeschenMerktLoeschungVor() = runTest {
        db.zutatDao().upsert(zutat)
        syncAktiv()
        db.zutatDao().upsert(zutat.copy(canonicalName = "Weizenmehl"))
        db.zutatDao().loeschen("z1")
        assertEquals(listOf(Triple("ingredient", "z1", true)), outbox())
    }

    @Test
    fun erneutesVormerkenErhoehtQueuedAtStreng() = runTest {
        db.zutatDao().upsert(zutat)
        syncAktiv()
        db.zutatDao().upsert(zutat.copy(canonicalName = "A"))
        val erst = db.syncDao().outboxEintrag("ingredient", "z1")!!.queuedAt
        db.zutatDao().upsert(zutat.copy(canonicalName = "B"))
        val dann = db.syncDao().outboxEintrag("ingredient", "z1")!!.queuedAt
        assertTrue(dann > erst, "queuedAt $erst → $dann")
    }

    @Test
    fun kindMerktElternRezeptVor() = runTest {
        db.zutatDao().upsert(zutat)
        db.rezeptDao().upsert(rezept)
        syncAktiv()
        db.rezeptZutatDao().upsert(RezeptZutatEntity("ri1", "r1", "z1", BigDecimal("250"), MeasureUnit.entries.first(), 0, null, optional = false))
        assertEquals(listOf(Triple("recipe", "r1", false)), outbox())
        outboxLeeren()
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity(id = "s1", recipeId = "r1", position = 1, text = "Kneten"))
        assertEquals(listOf(Triple("recipe", "r1", false)), outbox())
        outboxLeeren()
        db.arbeitsschrittDao().loeschen("s1")
        assertEquals(listOf(Triple("recipe", "r1", false)), outbox())
    }

    @Test
    fun quelleMerktEinkaufsartikelVor() = runTest {
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        syncAktiv()
        db.einkaufsartikelQuelleDao().upsert(
            EinkaufsartikelQuelleEntity("q1", "a1", "p1", "ri1", "Brot", tag, BigDecimal("1"), MeasureUnit.entries.first()),
        )
        assertEquals(listOf(Triple("shopping_item", "a1", false)), outbox())
    }

    @Test
    fun kaskadeBeimRezeptLoeschenMerktKeinGeisterRezeptVor() = runTest {
        db.rezeptDao().upsert(rezept)
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity(id = "s1", recipeId = "r1", position = 1, text = "Kneten"))
        syncAktiv()
        db.rezeptDao().loeschen("r1")
        // Die Kaskade auf instruction_step darf die Löschung nicht mit deleted = 0 überschreiben (SQLite führt die
        // Kaskade vor dem Eltern-Trigger aus; der EXISTS-Wächter der Kind-Trigger sichert die andere Reihenfolge ab).
        assertEquals(listOf(Triple("recipe", "r1", true)), outbox())
    }

    @Test
    fun anwendenVomServerSchreibtKeineOutbox() = runTest {
        syncAktiv(applyingRemote = true)
        db.zutatDao().upsert(zutat)
        db.rezeptDao().upsert(rezept)
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        db.einkaufsartikelDao().upsert(artikel.copy(checked = true))
        db.zutatDao().loeschen("z1")
        assertEquals(emptyList(), outbox())
        // Server-Daten behalten ihre Zeitstempel (Pflege-Trigger aus).
        assertEquals(artikel.copy(checked = true), db.einkaufsartikelDao().nachId("a1"))
    }

    @Test
    fun keinEndlosTrigger() = runTest {
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel)
        syncAktiv()
        db.einkaufsartikelDao().upsert(artikel.copy(checked = true))
        assertEquals(listOf(Triple("shopping_item", "a1", false)), outbox())
        val neu = db.einkaufsartikelDao().nachId("a1")!!
        assertTrue(neu.checked)
        assertNotEquals(artikel.updatedAt, neu.updatedAt)
        assertNotEquals(artikel.checkedChangedAt, neu.checkedChangedAt)
    }

    @Test
    fun neuerEinkaufsartikelBekommtZeitstempel() = runTest {
        db.einkaufslisteDao().upsert(liste)
        db.einkaufsartikelDao().upsert(artikel.copy(updatedAt = 0))
        assertTrue(db.einkaufsartikelDao().nachId("a1")!!.updatedAt > 0)
    }

    @Test
    fun neuesRezeptfotoErledigtFotoWunsch() = runTest {
        db.rezeptDao().upsert(rezept)
        db.syncDao().upsertFotoGewuenscht(SyncFotoGewuenschtEntity("r1", "abc"))
        db.rezeptDao().upsert(rezept.copy(imageUri = "file:///neu.jpg"))
        assertNull(db.syncDao().fotoGewuenscht("r1"))
    }

    @Test
    fun keinVorratsTrigger() {
        assertTrue(SyncTrigger.namen.none { "pantry" in it }, SyncTrigger.namen.toString())
        assertTrue(SyncTrigger.anweisungen.none { "pantry" in it })
    }

    @Test
    fun triggernamenFolgenDemSchema() {
        val tabellen = listOf(
            "ingredient", "recipe", "meal_slot", "shopping_list", "shopping_item", "tagebuch_eintrag",
            "recipe_ingredient", "instruction_step", "shopping_item_source",
        )
        for (t in tabellen) for (e in listOf("ai", "au", "ad")) assertTrue("sync_${t}_$e" in SyncTrigger.namen, "sync_${t}_$e fehlt")
    }
}
