package de.foody.app

import android.content.Context
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PantryRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeImportRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.recipes.RecipeListScreen
import de.foody.app.ui.recipes.RecipeListViewModel
import de.foody.app.ui.theme.FoodyTheme
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Regression: Beim ersten Buchstaben verschwindet die Tagesauswahl über dem Suchfeld. Ohne feste Schlüssel
 * im Raster wurde das Suchfeld dabei neu aufgebaut und verlor den Fokus – jeder weitere Buchstabe ging verloren.
 */
@RunWith(AndroidJUnit4::class)
class RecipeSearchFocusTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: FoodyDatabase
    private lateinit var vm: RecipeListViewModel

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        val recipes = RecipeRepository(db.recipeDao())
        db.ingredientDao().upsert(IngredientEntity(id = "r", canonicalName = "Reis", createdAt = 0, updatedAt = 0))
        for (name in listOf("Risotto", "Reispfanne", "Milchreis")) {
            recipes.save(
                RecipeDraft(id = null, name = name, defaultServings = 2,
                    ingredients = listOf(RecipeDraft.Line("r", BigDecimal.ONE, MeasureUnit.PIECE, null, false))),
            )
        }
        val importer = RecipeImportRepository(db, recipes, IngredientRepository(db, db.ingredientDao(), context), context)
        vm = RecipeListViewModel(recipes, PantryRepository(db.pantryDao()), importer, SavedStateHandle())
    }

    @After fun tearDown() = db.close()

    @Test fun searchFieldKeepsFocusWhileTyping() {
        compose.setContent { FoodyTheme { RecipeListScreen(onOpen = {}, onCreate = {}, vm = vm) } }
        val field = compose.onNode(hasSetTextAction())

        field.performClick()
        // Buchstabe für Buchstabe wie auf der Tastatur: Nach dem ersten verschwindet die Tagesauswahl
        for (c in "Ris") {
            field.performTextInput(c.toString())
            compose.waitForIdle()
            field.assertIsFocused()
        }
        field.assertTextContains("Ris")
    }
}
