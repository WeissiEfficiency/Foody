package de.foody.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeImportRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.recipes.RecipeListUiState
import de.foody.app.ui.recipes.RecipeListViewModel
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals

/** Rezeptliste: Stöbern teilt die Abfrage der aktiven Rezepte, Suche und Archiv nutzen die Suchabfrage. */
@RunWith(AndroidJUnit4::class)
class RecipeListViewModelTest {
    private lateinit var db: FoodyDatabase
    private lateinit var vm: RecipeListViewModel
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
        val importer = RecipeImportRepository(db, recipes, IngredientRepository(db, db.ingredientDao(), context), context)
        db.ingredientDao().upsert(IngredientEntity(id = "z", canonicalName = "Zucchini", createdAt = 0, updatedAt = 0))
        db.ingredientDao().upsert(IngredientEntity(id = "r", canonicalName = "Reis", createdAt = 0, updatedAt = 0))
        suspend fun recipe(name: String, ingredient: String) = recipes.save(
            RecipeDraft(id = null, name = name, defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line(ingredient, BigDecimal.ONE, MeasureUnit.PIECE, null, false))),
        )
        recipe("Gemüsepfanne", "z")
        recipe("Risotto", "r")
        recipes.setArchived(recipe("Altes Curry", "r"), archived = true)
        vm = RecipeListViewModel(recipes, importer, SavedStateHandle())
    }

    @After fun tearDown() = db.close()

    private fun RecipeListUiState.names() = recipes.map { it.name }

    /**
     * Wartet, bis die Liste [expected] zeigt. Der Suchbegriff aktualisiert sich sofort (flüssiges Tippen),
     * das Ergebnis kommt einen Moment später aus der Datenbank – Zwischenzustände sind also erlaubt.
     */
    private fun awaitNames(expected: List<String>, condition: (RecipeListUiState) -> Boolean = { true }) = runBlocking {
        withTimeout(5_000) { vm.state.first { !it.loading && condition(it) && it.names() == expected } }
    }

    @Test fun browsingSearchArchiveAndBack() {
        awaitNames(listOf("Gemüsepfanne", "Risotto"))

        vm.onQuery("zucchini") // Treffer über die Zutat → Suchabfrage
        awaitNames(listOf("Gemüsepfanne")) { it.query == "zucchini" }

        vm.onQuery("")
        vm.onToggleArchived()
        awaitNames(listOf("Altes Curry")) { it.showArchived }

        vm.onToggleArchived() // zurück zum Stöbern → wieder die geteilte Abfrage der aktiven Rezepte
        val browsing = awaitNames(listOf("Gemüsepfanne", "Risotto")) { !it.showArchived && it.query.isEmpty() }
        // Tagesauswahl und Tags kommen immer aus den aktiven Rezepten
        assertEquals(setOf("Gemüsepfanne", "Risotto"), browsing.dailyPicks.map { it.name }.toSet())
    }
}
