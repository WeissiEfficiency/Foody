package de.foody.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Outbox-Trigger (Sync Etappe 2): nur bei aktivem Sync, Kindzeilen zählen für den Elterndatensatz. */
@RunWith(AndroidJUnit4::class)
class SyncTriggersTest {
    private lateinit var db: FoodyDatabase

    @Before fun setUp() { db = syncTestDb() }
    @After fun tearDown() = db.close()

    private suspend fun queued() = db.syncDao().outbox().map { Triple(it.type, it.recordId, it.deleted) }.toSet()

    private fun ingredient(id: String) =
        IngredientEntity(id = id, canonicalName = "Zutat $id", createdAt = 0, updatedAt = 0)

    private fun draft(vararg ingredientIds: String) = RecipeDraft(
        id = null, name = "Rezept", defaultServings = 2,
        ingredients = ingredientIds.map { RecipeDraft.Line(it, BigDecimal.ONE, MeasureUnit.GRAM, null, false) },
        steps = listOf("Schritt eins"),
    )

    private fun slot(id: String, recipeId: String) = MealSlotEntity(
        id = id, date = LocalDate.of(2026, 1, 1), slotType = "DINNER", recipeId = recipeId, servings = 2, createdAt = 0, updatedAt = 0,
    )

    private fun item(id: String) = ShoppingItemEntity(id = id, listId = "l", name = "Milch", sortOrder = 0)

    private fun source(id: String, itemId: String) = ShoppingItemSourceEntity(
        id, itemId, "s1", "ri", "Rezept", LocalDate.of(2026, 1, 1), BigDecimal.ONE, MeasureUnit.GRAM,
    )

    private suspend fun shoppingListWith(vararg items: ShoppingItemEntity) {
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 0))
        items.forEach { db.shoppingDao().upsertItem(it) }
    }

    @Test fun savingRecipeWhileActiveQueuesItOnce() = runTest {
        // Regression: Rezept- und Zeilen-Trigger melden denselben Datensatz; INSERT OR REPLACE im Trigger brach unter @Insert (ABORT) ab
        db.ingredientDao().upsert(ingredient("i1"))
        db.activateSyncForTest()
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        assertEquals(listOf("recipe" to rid), db.syncDao().outbox().map { it.type to it.recordId })
    }

    @Test fun inactiveSyncWritesNoOutbox() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.mealPlanDao().upsert(slot("s1", rid))
        db.pantryDao().upsert(PantryItemEntity("p1", "i1", BigDecimal.ONE, MeasureUnit.GRAM, null, 0))
        shoppingListWith(item("it1"))
        db.shoppingDao().insertSources(listOf(source("src", "it1")))
        db.shoppingDao().setNote("it1", "Bio")
        db.mealPlanDao().delete("s1")
        db.shoppingDao().deleteItem("it1")
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun rootWritesAreQueued() = runTest {
        db.activateSyncForTest()
        db.ingredientDao().upsert(ingredient("i1"))
        assertEquals(setOf(Triple("ingredient", "i1", false)), queued())
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        assertEquals(setOf(Triple("ingredient", "i1", false)), queued())
        assertEquals(1, db.syncDao().outbox().size)
        db.ingredientDao().delete("i1")
        assertEquals(setOf(Triple("ingredient", "i1", true)), queued())
    }

    @Test fun childWritesQueueTheParent() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        db.ingredientDao().upsert(ingredient("i2"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1", "i2"))
        db.activateSyncForTest()
        db.syncDao().clearOutbox()
        val step = db.recipeDao().getSteps(rid).single()
        // Nur ein Schritt wird geändert
        db.openHelper.writableDatabase.execSQL("UPDATE instruction_step SET text = 'Geändert' WHERE id = ?", arrayOf(step.id))
        assertEquals(setOf(Triple("recipe", rid, false)), queued())
        db.syncDao().clearOutbox()
        db.recipeDao().insertSteps(listOf(InstructionStepEntity("extra", rid, 5, "Neu")))
        assertEquals(setOf(Triple("recipe", rid, false)), queued())

        shoppingListWith(item("it1"))
        db.syncDao().clearOutbox()
        db.shoppingDao().insertSources(listOf(source("src", "it1")))
        assertEquals(setOf(Triple("shopping_item", "it1", false)), queued())
    }

    @Test fun deletingRecipeQueuesRecipeAndCascadedSlots() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.mealPlanDao().upsert(slot("s1", rid))
        db.mealPlanDao().upsert(slot("s2", rid))
        db.activateSyncForTest()
        db.syncDao().clearOutbox()
        db.recipeDao().delete(rid)
        val q = queued()
        assertTrue(Triple("recipe", rid, true) in q, "recipe gelöscht: $q")
        assertTrue(Triple("meal_slot", "s1", true) in q, "slot s1: $q")
        assertTrue(Triple("meal_slot", "s2", true) in q, "slot s2: $q")
        assertTrue(Triple("recipe", rid, false) !in q, "keine lebende recipe-Zeile: $q")
    }

    @Test fun applyingRemoteSuppressesQueue() = runTest {
        db.activateSyncForTest()
        db.syncDao().setApplyingRemote(true)
        db.ingredientDao().upsert(ingredient("i1"))
        assertEquals(emptyList(), db.syncDao().outbox())
        db.syncDao().setApplyingRemote(false)
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        assertEquals(setOf(Triple("ingredient", "i1", false)), queued())
    }

    @Test fun checkedChangeStampsTime() = runTest {
        shoppingListWith(item("it1"))
        val inserted = db.shoppingDao().getItems("l").single()
        assertTrue(inserted.updatedAt > 0, "Insert setzt updatedAt")
        assertEquals(0L, inserted.checkedChangedAt)

        db.shoppingDao().setChecked("it1", true)
        assertTrue(db.shoppingDao().getItems("l").single().checkedChangedAt > 0)

        // Werte zurückdatieren, damit „unverändert“ und „erhöht“ unterscheidbar sind
        db.openHelper.writableDatabase.execSQL("UPDATE shopping_item SET updatedAt = 1000, checkedChangedAt = 2000 WHERE id = 'it1'")
        db.shoppingDao().setNote("it1", "Bio")
        val noted = db.shoppingDao().getItems("l").single()
        assertEquals(2000L, noted.checkedChangedAt)
        assertTrue(noted.updatedAt > 1000)
    }

    @Test fun updatedStampTerminatesAndAdvancesWhenClockIsBehind() = runTest {
        shoppingListWith(item("it1"))
        val future = System.currentTimeMillis() + 1_000_000_000L
        db.openHelper.writableDatabase.execSQL("UPDATE shopping_item SET updatedAt = $future WHERE id = 'it1'")
        db.shoppingDao().setNote("it1", "Bio")
        assertEquals(future + 1, db.shoppingDao().getItems("l").single().updatedAt)
    }

    @Test fun remoteAppliedCheckedKeepsSuppliedTimestamps() = runTest {
        shoppingListWith(item("it1"))
        db.activateSyncForTest()
        db.syncDao().setApplyingRemote(true)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE shopping_item SET checked = 1, checkedChangedAt = 4242, updatedAt = 4243 WHERE id = 'it1'",
        )
        val i = db.shoppingDao().getItems("l").single()
        assertEquals(4242L, i.checkedChangedAt)
        assertEquals(4243L, i.updatedAt)
        assertEquals(emptyList(), db.syncDao().outbox())
    }
}
