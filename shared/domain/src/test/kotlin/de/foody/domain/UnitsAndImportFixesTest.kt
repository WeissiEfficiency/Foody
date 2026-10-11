package de.foody.domain

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Packungen und Dosen als eigene Einheiten; Import-Korrekturen für Mehltypen, Crème fraîche und Prisen. */
class UnitsAndImportFixesTest {
    private fun bd(s: String) = BigDecimal(s)

    @Test fun packageConvertsOnlyWithKnownPackageWeight() {
        val pck = Quantity.of(bd("2"), MeasureUnit.PACKAGE)
        assertEquals(0, bd("16").compareTo(UnitConverter.convert(pck, Dimension.MASS, ConversionInfo(packageWeightG = bd("8")))!!.baseAmount))
        // Ohne Packungsgewicht keine Umrechnung – auch nicht über das Stückgewicht (Würfel ≠ Päckchen)
        assertNull(UnitConverter.convert(pck, Dimension.MASS, ConversionInfo(pieceWeightG = bd("42"))))
    }

    @Test fun canAndPieceAreDifferentUnitsOnTheShoppingList() {
        val day = LocalDate.of(2026, 10, 5)
        val recipe = Recipe("r", "Backen", 1, listOf(
            RecipeIngredient("l1", "backpulver", bd("1"), MeasureUnit.PACKAGE),
            RecipeIngredient("l2", "tomate", bd("1"), MeasureUnit.CAN),
        ))
        val needs = GenerateShoppingListUseCase()(
            DateRange(day, day),
            listOf(MealSlot("s", day, "Abendessen", "r", 2)),
            mapOf("r" to recipe),
            emptyMap(),
        )
        val byIngredient = needs.associateBy { it.ingredientId }
        assertEquals("2 Pck.", QuantityFormatter.format(byIngredient.getValue("backpulver").toBuy))
        assertEquals("2 Dose", QuantityFormatter.format(byIngredient.getValue("tomate").toBuy))
    }

    @Test fun nutritionUsesPackageWeight() {
        val vanillin = Ingredient(
            "v", "Vanillezucker", ConversionInfo(packageWeightG = bd("8")),
            NutrientProfile(NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to bd("1680"))),
        )
        val recipe = Recipe("r", "Kuchen", 1, listOf(RecipeIngredient("l", "v", bd("1"), MeasureUnit.PACKAGE)))
        val result = NutritionCalculator.calculate(recipe, mapOf("v" to vanillin))
        // 8 g × 1680 kJ / 100 g
        assertEquals(0, bd("134.4").compareTo(result.totals.getValue(Nutrient.ENERGY_KJ)))
    }

    @Test fun catalogKnowsTypicalPackageAndCanSizes() {
        assertEquals(bd("8"), IngredientCatalog.packageWeightG("Vanillezucker"))
        assertEquals(bd("7"), IngredientCatalog.packageWeightG("Frischhefe"), "Pck. Hefe = Trockenhefe")
        assertEquals(bd("400"), IngredientCatalog.canWeightG("Tomaten"))
        assertNull(IngredientCatalog.packageWeightG("Butter"))
    }

    private fun import(vararg lines: String) =
        MarkdownRecipeImporter.parse("# Test\n\n## Zutaten\n\n" + lines.joinToString("\n\n") + "\n\n## Zubereitung\n\nKochen.").ingredients

    @Test fun flourTypeStaysInTheNameAndMapsToFlour() {
        val flour = import("500 g", "Weizenmehl Type 405").single()
        assertEquals("Weizenmehl Type 405", flour.name)
        assertEquals("Weizenmehl", IngredientCatalog.canonicalName(flour.name))
    }

    @Test fun cremeFraicheKeepsBothWords() {
        val cf = import("1 Becher", "Crème fraîche, kalt").single()
        assertEquals("Crème fraîche", cf.name)
        assertEquals("kalt", cf.note?.substringAfterLast(", "))
    }

    @Test fun pinchBecomesAsNeeded() {
        val salt = import("1 Prise(n)", "Salz").single()
        assertNull(salt.amount, "Prise = nach Bedarf, nicht 1 Stück")
        assertEquals("1 Prise(n)", salt.note)
    }
}
