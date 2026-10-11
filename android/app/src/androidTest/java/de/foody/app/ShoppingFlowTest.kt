package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.ShoppingRepository
import de.foody.domain.DateRange
import de.foody.domain.DiffType
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** End-to-End Rezept → Plan → Einkauf gegen eine In-Memory-Room-Datenbank. */
@RunWith(AndroidJUnit4::class)
class ShoppingFlowTest {
    private lateinit var db: FoodyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodyDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun ingredient(id: String, name: String) =
        IngredientEntity(id = id, canonicalName = name, createdAt = 0, updatedAt = 0)

    @Test fun recipeToPlanToShoppingSnapshot() = runTest {
        db.ingredientDao().upsert(ingredient("rice", "Reis"))
        val recipes = RecipeRepository(db.recipeDao())
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        val shopping = ShoppingRepository(db)

        val curry = recipes.save(RecipeDraft(id = null, name = "Curry", defaultServings = 4, ingredients =
            listOf(RecipeDraft.Line("rice", BigDecimal("300"), MeasureUnit.GRAM, null, false))))
        val pan = recipes.save(RecipeDraft(id = null, name = "Reispfanne", defaultServings = 2, ingredients =
            listOf(RecipeDraft.Line("rice", BigDecimal("150"), MeasureUnit.GRAM, null, false))))

        val today = LocalDate.of(2026, 10, 1)
        plan.add(today, "Abend", curry, 4)
        plan.add(today.plusDays(1), "Mittag", pan, 2)
        plan.add(today.plusDays(5), "Mittag", pan, 2) // außerhalb des Zeitraums

        val range = DateRange.ofDays(today, 2)
        val listId = shopping.createSnapshot("Test", range, shopping.preview(range, usePantry = true))
        val items = db.shoppingDao().getItems(listId)
        assertEquals(1, items.size)
        assertEquals(0, BigDecimal("450").compareTo(items.single().amount))
        val sources = db.shoppingDao().getSources(items.single().id).sortedBy { it.date }
        assertEquals(2, sources.size)
        // Herkunft mit Rezeptname und Plandatum (aus den vorab geladenen Rezepten/Planpositionen)
        assertEquals(listOf("Curry" to today, "Reispfanne" to today.plusDays(1)), sources.map { it.recipeName to it.date })

        // Rezeptänderung verändert den Snapshot nicht stillschweigend
        recipes.save(RecipeDraft(id = curry, name = "Curry", defaultServings = 4, ingredients =
            listOf(RecipeDraft.Line("rice", BigDecimal("400"), MeasureUnit.GRAM, null, false))))
        assertEquals(0, BigDecimal("450").compareTo(db.shoppingDao().getItems(listId).single().amount))
    }

    /** Neuberechnung: weniger Bedarf lässt abgehakte Einträge abgehakt, mehr Bedarf setzt sie zurück auf die Liste. */
    @Test fun applyDiffKeepsCheckedWhenLessIsNeeded() = runTest {
        db.ingredientDao().upsert(ingredient("rice", "Reis"))
        db.ingredientDao().upsert(ingredient("onion", "Zwiebel"))
        val recipes = RecipeRepository(db.recipeDao())
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        val shopping = ShoppingRepository(db)
        val curry = recipes.save(RecipeDraft(id = null, name = "Curry", defaultServings = 2, ingredients = listOf(
            RecipeDraft.Line("rice", BigDecimal("200"), MeasureUnit.GRAM, null, false),
            RecipeDraft.Line("onion", BigDecimal("1"), MeasureUnit.PIECE, null, false),
        )))
        val today = LocalDate.of(2026, 10, 1)
        plan.add(today, "Abend", curry, 4)
        val range = DateRange.ofDays(today, 1)
        val listId = shopping.createSnapshot("Test", range, shopping.preview(range, usePantry = false))
        db.shoppingDao().getItems(listId).forEach { shopping.setChecked(it, true) }

        // Reis: 400 g → 200 g (weniger), Zwiebel: 2 → 1 Stk. (weniger); danach neue Zutat hinzu
        val slot = db.mealPlanDao().getRange(today, today).single()
        plan.update(slot.copy(servings = 2))
        val (entries, previews) = shopping.diff(listId, usePantry = false)
        assertTrue(entries.all { it.type == DiffType.CHANGED })
        shopping.applyDiff(listId, entries, previews)
        val afterLess = db.shoppingDao().getItems(listId).associateBy { it.ingredientId }
        assertTrue(afterLess.values.all { it.checked })
        assertEquals(0, BigDecimal("200").compareTo(afterLess.getValue("rice").amount))
        // Herkunft passt zum neuen Bedarf (genau eine Quelle mit 200 g)
        assertEquals(0, BigDecimal("200").compareTo(db.shoppingDao().getSources(afterLess.getValue("rice").id).single().contributedAmount))

        plan.update(slot.copy(servings = 6))
        db.ingredientDao().upsert(ingredient("salt", "Salz"))
        recipes.save(RecipeDraft(id = curry, name = "Curry", defaultServings = 2, ingredients = listOf(
            RecipeDraft.Line("rice", BigDecimal("200"), MeasureUnit.GRAM, null, false),
            RecipeDraft.Line("onion", BigDecimal("1"), MeasureUnit.PIECE, null, false),
            RecipeDraft.Line("salt", BigDecimal("5"), MeasureUnit.GRAM, null, false),
        )))
        val (more, morePreviews) = shopping.diff(listId, usePantry = false)
        shopping.applyDiff(listId, more, morePreviews)
        val afterMore = db.shoppingDao().getItems(listId).associateBy { it.ingredientId }
        assertFalse(afterMore.getValue("rice").checked)
        assertFalse(afterMore.getValue("onion").checked)
        assertEquals(0, BigDecimal("15").compareTo(afterMore.getValue("salt").amount))
        assertEquals(3, afterMore.size)
    }

    @Test fun markCookedDeductsPantryAcrossUnits() = runTest {
        db.ingredientDao().upsert(ingredient("rice", "Reis"))
        db.ingredientDao().upsert(ingredient("onion", "Zwiebel").copy(pieceWeightG = BigDecimal("100")))
        val recipes = RecipeRepository(db.recipeDao())
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        val curry = recipes.save(RecipeDraft(id = null, name = "Curry", defaultServings = 2, ingredients = listOf(
            RecipeDraft.Line("rice", BigDecimal("200"), MeasureUnit.GRAM, null, false),
            RecipeDraft.Line("onion", BigDecimal("1"), MeasureUnit.PIECE, null, false),
        )))
        db.pantryDao().upsert(PantryItemEntity("p1", "rice", BigDecimal("1"), MeasureUnit.KILOGRAM, null, 0))
        db.pantryDao().upsert(PantryItemEntity("p2", "onion", BigDecimal("250"), MeasureUnit.GRAM, null, 0))
        val today = LocalDate.of(2026, 10, 1)
        plan.add(today, "Abend", curry, 4)
        plan.markCooked(db.mealPlanDao().getRange(today, today).single().id)

        val pantry = db.pantryDao().getAll().associateBy { it.id }
        assertEquals(0, BigDecimal("0.6").compareTo(pantry.getValue("p1").amount))
        assertEquals(0, BigDecimal("50").compareTo(pantry.getValue("p2").amount))
    }
}
