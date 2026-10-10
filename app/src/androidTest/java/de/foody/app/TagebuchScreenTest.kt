package de.foody.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.GoalPreferences
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.TagebuchRepository
import de.foody.app.ui.tagebuch.TagebuchScreen
import de.foody.app.ui.tagebuch.TagebuchViewModel
import de.foody.app.ui.theme.FoodyTheme
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/** Tagebuch-Bildschirm: vier Mahlzeiten, Vorschlag aus dem Plan, „Gegessen“ macht daraus einen Eintrag. */
@RunWith(AndroidJUnit4::class)
class TagebuchScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: FoodyDatabase
    private lateinit var vm: TagebuchViewModel

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        val recipes = RecipeRepository(db.recipeDao())
        db.ingredientDao().upsert(
            IngredientEntity(id = "reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G,
                energyKj = BigDecimal("1464"), createdAt = 0, updatedAt = 0),
        )
        val curry = recipes.save(
            RecipeDraft(id = null, name = "Curry", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("reis", BigDecimal("200"), MeasureUnit.GRAM, null, false))),
        )
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        plan.add(LocalDate.now(), "ABENDESSEN", curry, 2)
        vm = TagebuchViewModel(
            TagebuchRepository(db, db.tagebuchDao()), plan, recipes, IngredientRepository(db, db.ingredientDao(), context),
            GoalPreferences(context), SavedStateHandle(),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun vorschlagWirdZumEintrag() {
        compose.setContent { FoodyTheme { TagebuchScreen(vm = vm) } }
        for (titel in listOf("Frühstück", "Mittagessen", "Snack", "Abendessen")) {
            compose.onNodeWithText(titel).performScrollTo().assertIsDisplayed()
        }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Geplant: Curry")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Gegessen").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Geplant: Curry")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Curry").performScrollTo().assertIsDisplayed()
        // Zeile, Mahlzeit und Tag zeigen dieselbe Summe
        assert(compose.onAllNodesWithText("350 kcal").fetchSemanticsNodes().isNotEmpty())
    }
}
