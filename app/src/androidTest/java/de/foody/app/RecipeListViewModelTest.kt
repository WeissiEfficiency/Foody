package de.foody.app

import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.PantryRepository
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
import de.foody.domain.RecipeSort
import de.foody.domain.Diet
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
        vm = RecipeListViewModel(recipes, PantryRepository(db.pantryDao()), IngredientRepository(db, db.ingredientDao(), context),
            PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao()), importer, SavedStateHandle())
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

    @Test fun pantryFilterShowsCookableFirstAndHidesTooMuchMissing() = runBlocking {
        db.ingredientDao().upsert(IngredientEntity(id = "p", canonicalName = "Paprika", createdAt = 0, updatedAt = 0))
        db.ingredientDao().upsert(IngredientEntity(id = "s", canonicalName = "Salz", createdAt = 0, updatedAt = 0))
        recipes.save(
            RecipeDraft(id = null, name = "Paprikapfanne", defaultServings = 2, ingredients = listOf(
                RecipeDraft.Line("z", BigDecimal.ONE, MeasureUnit.PIECE, null, false),
                RecipeDraft.Line("p", BigDecimal.ONE, MeasureUnit.PIECE, null, false),
                RecipeDraft.Line("s", BigDecimal.ONE, MeasureUnit.PIECE, null, false), // Salz setzt Foody voraus
            )),
        )
        PantryRepository(db.pantryDao()).save(null, "r", BigDecimal.ONE, MeasureUnit.PIECE, null)

        vm.onTogglePantry()
        // Risotto: alles da; Gemüsepfanne: 1 fehlt; Paprikapfanne: 2 fehlen → ausgeblendet
        val state = awaitNames(listOf("Risotto", "Gemüsepfanne")) { it.pantryOnly }
        assertEquals(mapOf("Risotto" to 0, "Gemüsepfanne" to 1), state.recipes.associate { it.name to state.missing.getValue(it.id) })

        vm.onTogglePantry()
        awaitNames(listOf("Gemüsepfanne", "Paprikapfanne", "Risotto")) { !it.pantryOnly && it.missing.isEmpty() }
        Unit
    }

    @Test fun vegetarianFilterHidesMeatAndSortByNewest() = runBlocking {
        db.ingredientDao().upsert(IngredientEntity(id = "h", canonicalName = "Hähnchenbrust", category = "Fleisch & Fisch", createdAt = 0, updatedAt = 0))
        recipes.save(
            RecipeDraft(id = null, name = "Hähnchen-Curry", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("h", BigDecimal.ONE, MeasureUnit.PIECE, null, false))),
        )
        awaitNames(listOf("Gemüsepfanne", "Hähnchen-Curry", "Risotto"))

        vm.onToggleDiet(Diet.VEGETARIAN)
        awaitNames(listOf("Gemüsepfanne", "Risotto")) { Diet.VEGETARIAN in it.diets }

        vm.resetDiscover()
        awaitNames(listOf("Gemüsepfanne", "Hähnchen-Curry", "Risotto")) { it.diets.isEmpty() && it.sort == RecipeSort.NAME }

        // Das zuletzt angelegte Rezept steht bei „Neueste“ vorn
        vm.onSort(RecipeSort.NEWEST)
        val newest = runBlocking { withTimeout(5_000) { vm.state.first { it.sort == RecipeSort.NEWEST && it.recipes.size == 3 } } }
        assertEquals("Hähnchen-Curry", newest.names().first())
    }

    @Test fun bestRatedComesFirstUnratedLast() = runBlocking {
        awaitNames(listOf("Gemüsepfanne", "Risotto"))
        val ids = db.recipeDao().getAll().associate { it.name to it.id }
        recipes.setRating(ids.getValue("Risotto"), 5)
        vm.onSort(RecipeSort.BEST_RATED)
        awaitNames(listOf("Risotto", "Gemüsepfanne")) { it.sort == RecipeSort.BEST_RATED }

        // Bewertung zurücknehmen → wieder Namensreihenfolge (stabil sortiert)
        recipes.setRating(ids.getValue("Risotto"), null)
        awaitNames(listOf("Gemüsepfanne", "Risotto")) { it.sort == RecipeSort.BEST_RATED }
        Unit
    }
}
