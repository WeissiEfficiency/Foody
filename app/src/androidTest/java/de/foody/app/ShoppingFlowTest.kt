package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.ShoppingRepository
import de.foody.domain.DateRange
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

/** End-to-End Rezept → Plan → Einkauf gegen eine In-Memory-Room-Datenbank. */
@RunWith(AndroidJUnit4::class)
class ShoppingFlowTest {
    private lateinit var db: FoodyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodyDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun ingredient(id: String, name: String) =
        IngredientEntity(id, name, null, null, null, null, null, null, null, null, null, null, null, null, 0, 0)

    @Test fun recipeToPlanToShoppingSnapshot() = runTest {
        db.ingredientDao().upsert(ingredient("rice", "Reis"))
        val recipes = RecipeRepository(db.recipeDao())
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        val shopping = ShoppingRepository(db)

        val curry = recipes.save(RecipeDraft(null, "Curry", 4, null, null, null, null, "",
            listOf(RecipeDraft.Line("rice", BigDecimal("300"), MeasureUnit.GRAM, null, false)), emptyList()))
        val pan = recipes.save(RecipeDraft(null, "Reispfanne", 2, null, null, null, null, "",
            listOf(RecipeDraft.Line("rice", BigDecimal("150"), MeasureUnit.GRAM, null, false)), emptyList()))

        val today = LocalDate.of(2026, 10, 1)
        plan.add(today, "Abend", curry, 4)
        plan.add(today.plusDays(1), "Mittag", pan, 2)
        plan.add(today.plusDays(5), "Mittag", pan, 2) // außerhalb des Zeitraums

        val range = DateRange.ofDays(today, 2)
        val listId = shopping.createSnapshot("Test", range, shopping.preview(range, usePantry = true))
        val items = db.shoppingDao().getItems(listId)
        assertEquals(1, items.size)
        assertEquals(0, BigDecimal("450").compareTo(items.single().amount))
        assertEquals(2, db.shoppingDao().getSources(items.single().id).size)

        // Rezeptänderung verändert den Snapshot nicht stillschweigend
        recipes.save(RecipeDraft(curry, "Curry", 4, null, null, null, null, "",
            listOf(RecipeDraft.Line("rice", BigDecimal("400"), MeasureUnit.GRAM, null, false)), emptyList()))
        assertEquals(0, BigDecimal("450").compareTo(db.shoppingDao().getItems(listId).single().amount))
    }
}
