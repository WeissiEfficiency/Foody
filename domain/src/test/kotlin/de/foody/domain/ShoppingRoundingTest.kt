package de.foody.domain

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stück, Packungen und Dosen kauft man ganz: der Einkaufsbedarf wird aufgerundet, Gramm und Milliliter nicht. */
class ShoppingRoundingTest {
    private fun bd(s: String) = BigDecimal(s)
    private val day = LocalDate.of(2026, 10, 5)

    private fun needs(vararg lines: RecipeIngredient, servings: Int = 2, pantry: List<PantryItem> = emptyList(), ingredients: Map<String, Ingredient> = emptyMap()) =
        GenerateShoppingListUseCase()(
            DateRange(day, day),
            listOf(MealSlot("s", day, "Abend", "r", servings)),
            mapOf("r" to Recipe("r", "R", 2, lines.toList())),
            ingredients,
            pantry,
        ).associateBy { it.ingredientId }

    @Test fun countableNeedsAreRoundedUp() {
        val onion = Ingredient("onion", "Zwiebel", ConversionInfo(pieceWeightG = bd("100")))
        val result = needs(
            RecipeIngredient("a", "onion", bd("1"), MeasureUnit.PIECE),
            RecipeIngredient("b", "onion", bd("50"), MeasureUnit.GRAM),
            RecipeIngredient("c", "tomato", bd("1"), MeasureUnit.CAN),
            RecipeIngredient("d", "rice", bd("150"), MeasureUnit.GRAM),
            servings = 3,
            pantry = listOf(PantryItem("onion", Quantity.of(bd("120"), MeasureUnit.GRAM))),
            ingredients = mapOf("onion" to onion),
        )
        // Zwiebel: (1 Stk. + 50 g) × 1,5 = 2,25 Stk., davon 1,2 Stk. im Vorrat → 1,05 → 2 Stk.
        assertEquals("2 Stk.", QuantityFormatter.format(result.getValue("onion").toBuy))
        assertEquals("2 Dose", QuantityFormatter.format(result.getValue("tomato").toBuy)) // 1,5 Dosen
        assertEquals("225 g", QuantityFormatter.format(result.getValue("rice").toBuy)) // Gramm bleibt exakt
    }

    @Test fun roundingNoiseDoesNotBuyAnExtraPiece() {
        // 1 Stk. für 3 Portionen, gekocht für 2 → 0,666667 Stk.; dreimal eingeplant = 2,000001 Stk.
        val slots = (1..3).map { MealSlot("s$it", day, "Abend$it", "r", 2) }
        val recipe = Recipe("r", "R", 3, listOf(RecipeIngredient("a", "egg", bd("1"), MeasureUnit.PIECE)))
        val need = GenerateShoppingListUseCase()(DateRange(day, day), slots, mapOf("r" to recipe), emptyMap()).single()
        assertEquals("2 Stk.", QuantityFormatter.format(need.toBuy)) // nicht 3
        // Mit zwei Eiern im Vorrat bleibt nichts zu kaufen – auch nicht 0,000001 Stk. → 1 Stk.
        val covered = GenerateShoppingListUseCase()(
            DateRange(day, day), slots, mapOf("r" to recipe), emptyMap(),
            pantry = listOf(PantryItem("egg", Quantity.of(bd("2"), MeasureUnit.PIECE))),
        ).single()
        assertTrue(covered.toBuy.isZero())
    }

    @Test fun missingEnergyIsNamedAsGap() {
        // Nährwerte vorhanden, aber ohne Energie → kein kcal-Beitrag, also Lücke „keine Werte“
        val oil = Ingredient("oil", "Öl", nutrients = NutrientProfile(NutrientBasis.PER_100_G, mapOf(Nutrient.FAT_G to bd("100"))))
        val recipe = Recipe("r", "R", 1, listOf(RecipeIngredient("a", "oil", bd("10"), MeasureUnit.GRAM)))
        val result = NutritionCalculator.calculate(recipe, mapOf("oil" to oil))
        assertEquals(mapOf("a" to LineGap.NO_VALUES), result.lineGaps)
        assertEquals(0, bd("10").compareTo(result.totals.getValue(Nutrient.FAT_G)))
    }
}
