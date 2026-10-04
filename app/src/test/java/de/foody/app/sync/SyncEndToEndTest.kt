package de.foody.app.sync

import android.app.Application
import androidx.room.Room
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.ShoppingRepository
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ende-zu-Ende: zwei „Geräte“ (je eigene In-Memory-Room-DB) synchronisieren über den echten `:server`, der
 * in-process per `testApplication` läuft. „Offline“ heißt: auf diesem Gerät wird `run()` nicht aufgerufen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncEndToEndTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val opened = ArrayList<FoodyDatabase>()

    @After
    fun tearDown() = opened.forEach { it.close() }

    /** Ein Gerät mit eigener Datenbank, eigenem Token und eigener Engine. */
    private inner class Device(client: HttpClient, val name: String, clock: Clock) {
        val db: FoodyDatabase = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build().also { opened += it }
        var token: String? = null
        val api = KtorSyncApi("http://localhost", client) { token }
        val store = SyncLocalStore(db)
        val engine = SyncEngine(db, store, SyncApplier(db), api, clock)
        val recipes = RecipeRepository(db.recipeDao())
        val shopping = ShoppingRepository(db)
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        val ingredients = IngredientRepository(db, db.ingredientDao(), app)

        suspend fun run(): SyncOutcome = engine.run()

        suspend fun runOk() {
            val outcome = run()
            assertTrue(outcome is SyncOutcome.Success, "Sync auf $name: $outcome")
            assertEquals(0, outcome.problems, "Probleme auf $name: ${db.syncDao().problems()}")
        }

        /** Nach einem Abgleich darf nichts mehr zum Senden vorgemerkt sein und kein Problem offen. */
        suspend fun assertClean() {
            assertEquals(emptyList(), db.syncDao().outbox().map { it.type to it.recordId }, "Outbox auf $name")
            assertEquals(emptyList(), db.syncDao().problems(), "Probleme auf $name")
        }
    }

    /**
     * A legt den Haushalt an (Admin-Login), B tritt per Einladung bei; beide aktiviert. Mit [sameUser] ist B stattdessen
     * ein zweites Gerät desselben Kontos (Geräte widerrufen kann nur der Kontoinhaber).
     */
    private suspend fun ApplicationTestBuilder.pair(clock: TestClock, sameUser: Boolean = false): Pair<Device, Device> {
        val a = Device(client, "A", clock)
        val b = Device(client, "B", clock)
        val login = a.api.login(LoginRequest(TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "A"))
        a.token = login.token
        val household = a.api.createHousehold("Zuhause")
        a.api.selectHousehold(household.id)
        a.store.activate("http://localhost", household.id, uploadExisting = true)
        if (sameUser) {
            b.token = b.api.login(LoginRequest(TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "B")).token
            b.api.selectHousehold(household.id)
        } else {
            val invite = a.api.createInvite()
            val reg = b.api.register(RegisterRequest(invite.code, "bea", "geheimgeheim2", "B"))
            b.token = reg.token
            assertEquals(household.id, reg.householdId)
        }
        b.store.activate("http://localhost", household.id, uploadExisting = false)
        return a to b
    }

    private fun line(ingredientId: String, amount: Int) =
        RecipeDraft.Line(ingredientId, BigDecimal(amount), MeasureUnit.GRAM, null, false)

    @Test
    fun recipeTravelsBetweenDevices() = syncServerTest { _, clock ->
        val (a, b) = pair(clock)
        val mehl = a.ingredients.getOrCreate("Mehl")
        val zucker = a.ingredients.getOrCreate("Zucker")
        val rid = a.recipes.save(
            RecipeDraft(
                id = null, name = "Kuchen", defaultServings = 4, steps = listOf("Mischen", "Backen"),
                ingredients = listOf(line(mehl.id, 200), line(zucker.id, 100)),
            ),
        )
        a.runOk()
        b.runOk()

        val recipe = assertNotNull(b.db.recipeDao().get(rid))
        assertEquals("Kuchen", recipe.name)
        val lines = b.db.recipeDao().getIngredients(rid)
        assertEquals(
            mapOf(mehl.id to 200, zucker.id to 100),
            lines.associate { it.ingredientId to it.amount.toInt() },
        )
        assertEquals(listOf("Mischen", "Backen"), b.db.recipeDao().getSteps(rid).map { it.text })
        assertEquals(
            setOf(mehl.canonicalName, zucker.canonicalName),
            b.db.ingredientDao().getAll().map { it.canonicalName }.toSet(),
        )
        a.assertClean()
        b.assertClean()
    }

    @Test
    fun concurrentCheckAndAmountChangeMerge() = syncServerTest { _, clock ->
        val (a, b) = pair(clock)
        val listId = a.shopping.createEmptyList("Woche")
        a.shopping.addManual(listId, "Milch")
        val itemId = a.db.shoppingDao().getItems(listId).single().id
        a.db.shoppingDao().let { dao ->
            dao.upsertItem(dao.getItem(itemId)!!.copy(amount = BigDecimal.ONE, unit = MeasureUnit.LITER))
        }
        a.runOk()
        b.runOk()
        assertNotNull(b.db.shoppingDao().getItem(itemId))

        // beide offline: A hakt ab, B ändert die Menge
        a.shopping.setChecked(a.db.shoppingDao().getItem(itemId)!!, true)
        b.db.shoppingDao().let { dao ->
            dao.upsertItem(dao.getItem(itemId)!!.copy(amount = BigDecimal(3)))
        }
        a.runOk()
        b.runOk()
        a.runOk()

        for (d in listOf(a, b)) {
            val item = assertNotNull(d.db.shoppingDao().getItem(itemId), d.name)
            assertTrue(item.checked, "abgehakt auf ${d.name}")
            assertEquals(0, BigDecimal(3).compareTo(item.amount), "Menge auf ${d.name}: ${item.amount}")
        }
        a.assertClean()
        b.assertClean()
    }

    @Test
    fun sameIngredientOfflineOnBothDevicesBecomesOne() = syncServerTest { _, clock ->
        val (a, b) = pair(clock)
        val onionA = a.ingredients.getOrCreate("Zwiebel")
        val onionB = b.ingredients.getOrCreate("Zwiebel")
        assertTrue(onionA.id != onionB.id)
        val ra = a.recipes.save(RecipeDraft(null, "Suppe", 2, ingredients = listOf(line(onionA.id, 100))))
        val rb = b.recipes.save(RecipeDraft(null, "Salat", 2, ingredients = listOf(line(onionB.id, 50))))

        a.runOk()
        b.runOk()
        a.runOk()

        for (d in listOf(a, b)) {
            val onions = d.db.ingredientDao().getAll().filter { it.canonicalName == onionA.canonicalName }
            assertEquals(1, onions.size, "genau eine Zwiebel auf ${d.name}")
            val id = onions.single().id
            assertEquals(listOf(id), d.db.recipeDao().getIngredients(ra).map { it.ingredientId }, "Suppe auf ${d.name}")
            assertEquals(listOf(id), d.db.recipeDao().getIngredients(rb).map { it.ingredientId }, "Salat auf ${d.name}")
        }
        assertEquals(
            a.db.ingredientDao().getAll().map { it.id },
            b.db.ingredientDao().getAll().map { it.id },
        )
        a.assertClean()
        b.assertClean()
    }

    @Test
    fun deleteOnOneDeviceRemovesOnOther() = syncServerTest { _, clock ->
        val (a, b) = pair(clock)
        val mehl = a.ingredients.getOrCreate("Mehl")
        val rid = a.recipes.save(RecipeDraft(null, "Brot", 2, ingredients = listOf(line(mehl.id, 500))))
        a.plan.add(LocalDate.of(2026, 10, 5), "DINNER", rid, 2)
        a.runOk()
        b.runOk()
        assertNotNull(b.db.recipeDao().get(rid))
        assertEquals(1, b.db.mealPlanDao().getAll().size)

        a.db.recipeDao().delete(rid)
        a.runOk()
        b.runOk()

        assertNull(b.db.recipeDao().get(rid))
        assertTrue(b.db.recipeDao().getIngredients(rid).isEmpty())
        assertTrue(b.db.mealPlanDao().getAll().isEmpty())
        assertTrue(a.db.mealPlanDao().getAll().isEmpty())
        a.assertClean()
        b.assertClean() // insbesondere: die Löschung bei B stellt nichts in die Outbox
    }

    @Test
    fun revokedDeviceStopsSyncing() = syncServerTest { _, clock ->
        val (a, b) = pair(clock, sameUser = true)
        b.runOk()
        val bId = a.api.devices().single { !it.current }.id
        a.api.revokeDevice(bId)
        assertEquals(SyncOutcome.Unauthorized, b.run())
        assertEquals("unauthorized", b.db.syncDao().getState()!!.lastError)
        a.runOk()
        // Ausnahme: B bleibt absichtlich unberührt (kein Aufräumen nötig, Outbox/Probleme dort ohne Aussage).
        a.assertClean()
    }
}
