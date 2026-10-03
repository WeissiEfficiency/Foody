package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.ShoppingRepository
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rezept direkt auf die Einkaufsliste – ohne Planer. */
@RunWith(AndroidJUnit4::class)
class RecipeToShoppingTest {
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var shopping: ShoppingRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
        shopping = ShoppingRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun ingredient(id: String, name: String) =
        IngredientEntity(id, name, null, null, null, null, null, null, null, null, null, null, null, null, 0, 0)

    private suspend fun soup(): String {
        listOf(ingredient("kart", "Kartoffel"), ingredient("salz", "Salz"), ingredient("wasser", "Wasser"), ingredient("speck", "Speck"))
            .forEach { db.ingredientDao().upsert(it) }
        return recipes.save(
            RecipeDraft(null, "Suppe", 4, null, null, null, null, "", listOf(
                RecipeDraft.Line("kart", BigDecimal("1"), MeasureUnit.KILOGRAM, null, false),
                RecipeDraft.Line("salz", BigDecimal.ZERO, MeasureUnit.PIECE, null, false), // nach Bedarf
                RecipeDraft.Line("wasser", BigDecimal("1"), MeasureUnit.LITER, null, false), // nie einkaufen
                RecipeDraft.Line("speck", BigDecimal("100"), MeasureUnit.GRAM, null, true), // optional
            ), emptyList()),
        )
    }

    @Test fun addsScaledLinesToNewestListAndSkipsAsNeededWaterAndOptional() = runTest {
        val soup = soup()
        val result = shopping.addRecipe(soup, servings = 2, defaultListName = "Einkauf")

        assertEquals(1, result.added)
        val items = db.shoppingDao().getItems(result.listId)
        assertEquals(listOf("Kartoffel"), items.map { it.name })
        assertEquals(0, BigDecimal("500").compareTo(items.single().amount))
        assertEquals(MeasureUnit.GRAM, items.single().unit)
        assertTrue(items.single().manual) // „Neu berechnen“ fasst ihn nicht an
    }

    @Test fun addingTwiceSumsUpInsteadOfDuplicating() = runTest {
        val soup = soup()
        val first = shopping.addRecipe(soup, servings = 4, defaultListName = "Einkauf")
        val second = shopping.addRecipe(soup, servings = 4, defaultListName = "Einkauf")

        assertEquals(first.listId, second.listId)
        val item = db.shoppingDao().getItems(first.listId).single()
        assertEquals(0, BigDecimal("2000").compareTo(item.amount))
    }
}
