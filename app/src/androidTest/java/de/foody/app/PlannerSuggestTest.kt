package de.foody.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.PantryRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.planner.PlannerViewModel
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** „Leere Tage füllen“: nur freie Tage ab heute, ohne schon Geplantes zu wiederholen; Übernehmen legt die Slots an. */
@RunWith(AndroidJUnit4::class)
class PlannerSuggestTest {
    private lateinit var db: FoodyDatabase
    private lateinit var plan: PlanRepository
    private lateinit var vm: PlannerViewModel
    private val collector = CoroutineScope(Dispatchers.Main)
    private val today = LocalDate.now()

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        val recipes = RecipeRepository(db.recipeDao())
        db.ingredientDao().upsert(IngredientEntity(id = "r", canonicalName = "Reis", createdAt = 0, updatedAt = 0))
        val ids = (1..10).map { n ->
            recipes.save(
                RecipeDraft(id = null, name = "Rezept $n", defaultServings = 3,
                    ingredients = listOf(RecipeDraft.Line("r", BigDecimal.ONE, MeasureUnit.PIECE, null, false))),
            )
        }
        plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        // Morgen ist schon belegt – dieser Tag bleibt, und sein Rezept wird nicht noch einmal vorgeschlagen
        plan.add(today.plusDays(1), "Abendessen", ids.first(), 2)
        vm = PlannerViewModel(plan, recipes, PantryRepository(db.pantryDao()), SavedStateHandle(mapOf("start" to today.toEpochDay(), "days" to 3)))
        collector.launch { vm.state.collect {} } // stateIn(WhileSubscribed) braucht einen Abonnenten
        withTimeout(5_000) { vm.state.first { it.activeRecipes.size == 10 && it.slotsByDay.isNotEmpty() } }
        Unit
    }

    @After fun tearDown() {
        collector.cancel()
        db.close()
    }

    @Test fun fillsOnlyEmptyDaysAndAcceptCreatesSlots() = runBlocking {
        vm.suggest(seed = 42)
        val proposal = withTimeout(5_000) { vm.proposal.filterNotNull().first() }

        assertEquals(listOf(today, today.plusDays(2)), proposal.entries.map { it.first }, "nur die freien Tage")
        val alreadyPlanned = db.mealPlanDao().getRange(today, today.plusDays(2)).single().recipeId
        assertTrue(proposal.entries.none { it.second.id == alreadyPlanned }, "nichts doppelt in der Woche")

        vm.acceptProposal("Abendessen")
        val slots = withTimeout(5_000) {
            var s = db.mealPlanDao().getRange(today, today.plusDays(2))
            while (s.size < 3) { kotlinx.coroutines.delay(50); s = db.mealPlanDao().getRange(today, today.plusDays(2)) }
            s
        }
        assertEquals(3, slots.map { it.date }.toSet().size, "jeder Tag hat jetzt ein Gericht")
        assertTrue(slots.filter { it.date != today.plusDays(1) }.all { it.servings == 3 && it.slotType == "Abendessen" })
    }
}
