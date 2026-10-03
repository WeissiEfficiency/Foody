package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayNutritionTest {
    private fun bd(s: String) = BigDecimal(s)
    private val oats = Ingredient("oats", "Haferflocken", nutrients = NutrientProfile(NutrientBasis.PER_100_G, mapOf(
        Nutrient.ENERGY_KJ to bd("1545"), Nutrient.PROTEIN_G to bd("13"), Nutrient.CARBS_G to bd("60"), Nutrient.FAT_G to bd("7"),
    )))
    private val mystery = Ingredient("x", "Geheimzutat")

    private fun recipe(servings: Int, vararg lines: Pair<String, String>) =
        Recipe("r", "R", servings, lines.mapIndexed { i, (id, g) -> RecipeIngredient("l$i", id, bd(g), MeasureUnit.GRAM) })

    @Test fun sumsPerServingOfEachMeal() {
        // Frühstück: 200 g Hafer für 2 → 100 g je Portion; Abendessen: 400 g für 4 → 100 g je Portion
        val day = DayNutrition.of(listOf(recipe(2, "oats" to "200"), recipe(4, "oats" to "400")), mapOf("oats" to oats))!!
        // 2 × 1545 kJ = 3090 kJ ≈ 739 kcal pro Person
        assertEquals(739, day.kcal)
        assertEquals(26, day.protein)
        assertEquals(120, day.carbs)
        assertEquals(14, day.fat)
        assertTrue(day.complete)
    }

    @Test fun missingValuesMakeItALowerBound() {
        val day = DayNutrition.of(listOf(recipe(1, "oats" to "100", "x" to "100")), mapOf("oats" to oats, "x" to mystery))!!
        assertFalse(day.complete)
        assertEquals(369, day.kcal)
    }

    @Test fun emptyDayHasNoValues() {
        assertNull(DayNutrition.of(emptyList(), emptyMap()))
    }
}
