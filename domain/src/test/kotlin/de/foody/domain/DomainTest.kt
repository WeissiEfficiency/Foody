package de.foody.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun bd(s: String) = BigDecimal(s)
private fun assertAmount(expected: String, actual: BigDecimal) =
    assertEquals(0, bd(expected).compareTo(actual), "erwartet $expected, war $actual")

class ScalingTest {
    @Test fun scalesFourServingRecipe() {
        val cases = mapOf(1 to "75", 2 to "150", 6 to "450", 10 to "750")
        cases.forEach { (servings, expected) ->
            assertAmount(expected, RecipeScaler.scale(bd("300"), 4, servings))
        }
    }
}

class UnitTest {
    @Test fun addsGramsAndKilograms() {
        val sum = Quantity.of(bd("500"), MeasureUnit.GRAM) + Quantity.of(bd("1.2"), MeasureUnit.KILOGRAM)
        assertAmount("1.7", sum.amountIn(MeasureUnit.KILOGRAM))
        assertEquals("1,7 kg", QuantityFormatter.format(sum))
    }

    @Test fun pieceToGramWithoutPieceWeightStaysUnconverted() {
        val onion = Quantity.of(bd("1"), MeasureUnit.PIECE)
        assertNull(UnitConverter.convert(onion, Dimension.MASS, ConversionInfo()))
        val withWeight = UnitConverter.convert(onion, Dimension.MASS, ConversionInfo(pieceWeightG = bd("80")))
        assertAmount("80", withWeight!!.baseAmount)
    }

    @Test fun volumeToMassNeedsDensity() {
        val milk = Quantity.of(bd("500"), MeasureUnit.MILLILITER)
        assertNull(UnitConverter.convert(milk, Dimension.MASS, ConversionInfo()))
        assertAmount("515", UnitConverter.convert(milk, Dimension.MASS, ConversionInfo(densityGPerMl = bd("1.03")))!!.baseAmount)
    }

    @Test fun spoonsAreVolumes() {
        assertAmount("15", Quantity.of(bd("1"), MeasureUnit.TABLESPOON).baseAmount)
        assertAmount("5", Quantity.of(bd("1"), MeasureUnit.TEASPOON).baseAmount)
    }
}

class NutritionTest {
    private val rice = Ingredient(
        "rice", "Reis",
        nutrients = NutrientProfile(
            NutrientBasis.PER_100_G,
            Nutrient.entries.associateWith { bd("10") } + (Nutrient.ENERGY_KJ to bd("1500")),
        ),
    )
    private val unknown = Ingredient("x", "Geheimzutat")

    @Test fun calculatesTotalsAndPerServing() {
        val recipe = Recipe("r", "Reis", 4, listOf(RecipeIngredient("l1", "rice", bd("200"), MeasureUnit.GRAM)))
        val r = NutritionCalculator.calculate(recipe, mapOf("rice" to rice))
        assertAmount("3000", r.totals.getValue(Nutrient.ENERGY_KJ))
        assertAmount("750", r.perServing(Nutrient.ENERGY_KJ)!!)
        assertTrue(r.isComplete(Nutrient.PROTEIN_G))
    }

    @Test fun missingNutrientsAreIncompleteNotZero() {
        val recipe = Recipe(
            "r", "Mix", 2,
            listOf(
                RecipeIngredient("l1", "rice", bd("100"), MeasureUnit.GRAM),
                RecipeIngredient("l2", "x", bd("100"), MeasureUnit.GRAM),
            ),
        )
        val r = NutritionCalculator.calculate(recipe, mapOf("rice" to rice, "x" to unknown))
        assertFalse(r.isComplete(Nutrient.ENERGY_KJ))
        assertEquals(0.5, r.overallCompleteness, 1e-9)
        assertTrue("x" in r.missingIngredientIds)

        val onlyUnknown = Recipe("r2", "U", 1, listOf(RecipeIngredient("l", "x", bd("1"), MeasureUnit.GRAM)))
        val u = NutritionCalculator.calculate(onlyUnknown, mapOf("x" to unknown))
        assertNull(u.totals[Nutrient.ENERGY_KJ])
        assertEquals(0.0, u.overallCompleteness)
    }
}

class ShoppingTest {
    private val today = LocalDate.of(2026, 3, 28)
    private val ingredients = listOf(
        Ingredient("rice", "Reis"), Ingredient("milk", "Milch"), Ingredient("flour", "Mehl"),
    ).associateBy { it.id }
    private val useCase = GenerateShoppingListUseCase()

    private fun recipe(id: String, servings: Int, vararg lines: Triple<String, String, MeasureUnit>) =
        Recipe(id, id, servings, lines.mapIndexed { i, (ing, amt, u) -> RecipeIngredient("$id-$i", ing, bd(amt), u) })

    @Test fun scalesByPlannedServings() {
        val r = recipe("curry", 4, Triple("rice", "300", MeasureUnit.GRAM))
        val out = useCase(DateRange.ofDays(today, 1), listOf(MealSlot("s", today, "Abend", "curry", 6)), mapOf("curry" to r), ingredients)
        assertAmount("450", out.single().toBuy.baseAmount)
    }

    @Test fun aggregatesSameIngredientAcrossRecipesWithSources() {
        val a = recipe("a", 1, Triple("rice", "500", MeasureUnit.GRAM))
        val b = recipe("b", 1, Triple("rice", "1.2", MeasureUnit.KILOGRAM))
        val out = useCase(
            DateRange.ofDays(today, 2),
            listOf(MealSlot("s1", today, "Mittag", "a", 1), MealSlot("s2", today.plusDays(1), "Mittag", "b", 1)),
            mapOf("a" to a, "b" to b), ingredients,
        )
        assertEquals(1, out.size)
        assertEquals("1,7 kg", QuantityFormatter.format(out.single().toBuy))
        assertEquals(2, out.single().sources.size)
    }

    @Test fun milkAndFlourNeverMerged() {
        val r = recipe("pancake", 1, Triple("milk", "500", MeasureUnit.MILLILITER), Triple("flour", "300", MeasureUnit.GRAM))
        val out = useCase(DateRange.ofDays(today, 1), listOf(MealSlot("s", today, "F", "pancake", 1)), mapOf("pancake" to r), ingredients)
        assertEquals(2, out.size)
    }

    @Test fun sameIngredientDifferentDimensionsWithoutDensityStaySeparate() {
        val r = recipe("x", 1, Triple("milk", "500", MeasureUnit.MILLILITER), Triple("milk", "300", MeasureUnit.GRAM))
        val out = useCase(DateRange.ofDays(today, 1), listOf(MealSlot("s", today, "F", "x", 1)), mapOf("x" to r), ingredients)
        assertEquals(2, out.size)
    }

    @Test fun pantryIsSubtractedNeverNegative() {
        val r = recipe("x", 1, Triple("rice", "500", MeasureUnit.GRAM))
        val slots = listOf(MealSlot("s", today, "F", "x", 1))
        val out1 = useCase(DateRange.ofDays(today, 1), slots, mapOf("x" to r), ingredients,
            listOf(PantryItem("rice", Quantity.of(bd("300"), MeasureUnit.GRAM))))
        assertAmount("200", out1.single().toBuy.baseAmount)
        val out2 = useCase(DateRange.ofDays(today, 1), slots, mapOf("x" to r), ingredients,
            listOf(PantryItem("rice", Quantity.of(bd("700"), MeasureUnit.GRAM))))
        assertAmount("0", out2.single().toBuy.baseAmount)
    }

    @Test fun slotsOutsideRangeIgnored() {
        val r = recipe("x", 1, Triple("rice", "500", MeasureUnit.GRAM))
        val out = useCase(
            DateRange.ofDays(today, 1),
            listOf(MealSlot("s1", today, "F", "x", 1), MealSlot("s2", today.plusDays(1), "F", "x", 1), MealSlot("s3", today.minusDays(1), "F", "x", 1)),
            mapOf("x" to r), ingredients,
        )
        assertAmount("500", out.single().toBuy.baseAmount)
    }

    @Test fun optionalIngredientsExcludedByDefault() {
        val r = Recipe("x", "x", 1, listOf(RecipeIngredient("l", "rice", bd("100"), MeasureUnit.GRAM, optional = true)))
        val slots = listOf(MealSlot("s", today, "F", "x", 1))
        assertTrue(useCase(DateRange.ofDays(today, 1), slots, mapOf("x" to r), ingredients).isEmpty())
        assertEquals(1, useCase(DateRange.ofDays(today, 1), slots, mapOf("x" to r), ingredients, includeOptional = true).size)
    }

    @Test fun dstDoesNotShiftLocalDay() {
        // 29.03.2026: Umstellung auf Sommerzeit in Europa/Berlin
        val zone = ZoneId.of("Europe/Berlin")
        val lateEvening = ZonedDateTime.of(2026, 3, 29, 23, 30, 0, 0, zone).toLocalDate()
        val range = DateRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29))
        assertTrue(lateEvening in range)
        assertEquals(3, DateRange.ofDays(LocalDate.of(2026, 3, 28), 3).days.size)
    }

    @Test fun diffDetectsAddedChangedRemoved() {
        data class Old(val key: String, val q: Quantity)
        val g = { s: String -> Quantity.of(bd(s), MeasureUnit.GRAM) }
        val old = listOf(Old("rice|MASS", g("100")), Old("flour|MASS", g("50")), Old("salt|MASS", g("5")))
        val new = listOf(
            ShoppingNeed("rice", g("200"), g("0"), emptyList()),
            ShoppingNeed("flour", g("50"), g("0"), emptyList()),
            ShoppingNeed("sugar", g("10"), g("0"), emptyList()),
        )
        val d = ShoppingDiff.diff(old, { it.key }, { it.q }, new).associateBy { it.key }
        assertEquals(DiffType.CHANGED, d.getValue("rice|MASS").type)
        assertEquals(DiffType.UNCHANGED, d.getValue("flour|MASS").type)
        assertEquals(DiffType.ADDED, d.getValue("sugar|MASS").type)
        assertEquals(DiffType.REMOVED, d.getValue("salt|MASS").type)
    }

    @Test fun textExportSkipsCheckedItems() {
        val s = ShoppingListSnapshot("Einkauf", listOf(
            ShoppingListSnapshot.SnapshotItem("Reis", Quantity.of(bd("450"), MeasureUnit.GRAM), false, null),
            ShoppingListSnapshot.SnapshotItem("Salz", null, true, null),
        ))
        assertEquals("Einkauf\n- Reis (450 g)", ShoppingListTextFormatter.format(s))
        assertNotNull(s)
    }
}
