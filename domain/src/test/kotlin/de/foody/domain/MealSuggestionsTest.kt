package de.foody.domain

import de.foody.domain.MealSuggestions.Candidate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MealSuggestionsTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val week = (0L..6L).map { monday.plusDays(it) }

    private fun candidates(n: Int) = (1..n).map { Candidate("r$it", favorite = false, missing = null, lastPlanned = null) }

    @Test fun fillsEveryDayWithoutRepeats() {
        val plan = MealSuggestions.suggest(candidates(20), week, seed = 1)
        assertEquals(week.toSet(), plan.keys)
        assertEquals(7, plan.values.toSet().size, "kein Rezept doppelt")
    }

    @Test fun fewerRecipesThanDaysFillsWhatItCan() {
        val plan = MealSuggestions.suggest(candidates(3), week, seed = 1)
        assertEquals(3, plan.size)
        assertEquals(week.take(3).toSet(), plan.keys, "früheste Tage zuerst")
    }

    @Test fun cookableFromPantryAndFavoritesComeFirst() {
        val list = candidates(30) + listOf(
            Candidate("alles-da", favorite = false, missing = 0, lastPlanned = null),
            Candidate("favorit-alles-da", favorite = true, missing = 0, lastPlanned = null),
        )
        // Über viele Seeds: Diese beiden landen immer im Vorschlag, der Favorit mit vollem Vorrat stets am ersten Tag
        repeat(50) { seed ->
            val plan = MealSuggestions.suggest(list, week, seed.toLong())
            assertEquals("favorit-alles-da", plan[monday])
            assertTrue("alles-da" in plan.values)
        }
    }

    @Test fun recentlyPlannedRecipesAreSkipped() {
        val list = listOf(
            Candidate("gestern", favorite = true, missing = 0, lastPlanned = monday.minusDays(1)),
            Candidate("vor-einem-monat", favorite = false, missing = null, lastPlanned = monday.minusDays(30)),
        )
        assertEquals(mapOf(monday to "vor-einem-monat"), MealSuggestions.suggest(list, listOf(monday), seed = 1))
    }

    @Test fun sameSeedSameResultOtherSeedReshuffles() {
        val list = candidates(40)
        assertEquals(MealSuggestions.suggest(list, week, 7), MealSuggestions.suggest(list.reversed(), week, 7))
        assertNotEquals(MealSuggestions.suggest(list, week, 7), MealSuggestions.suggest(list, week, 8))
    }
}
