package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Brüche, gemischte Zahlen und Spannen in Mengenzeilen und Arbeitsschritten. */
class AmountParsingTest {
    private fun single(amount: String, name: String) =
        MarkdownRecipeImporter.parse("# T\n## Zutaten\n$amount\n$name\n## Zubereitung\nx").ingredients.single()

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "erwartet $expected, war $actual")

    @Test fun fractionIsNotReadAsItsNumerator() {
        // Früher: „1/2 TL“ → 1 Stück mit Hinweis „/2 TL“
        val zimt = single("1/2 TL", "Zimt")
        assertAmount("0.5", zimt.amount)
        assertEquals(MeasureUnit.TEASPOON, zimt.unit)
        val milch = single("3/4 l", "Milch")
        assertAmount("0.75", milch.amount)
        assertEquals(MeasureUnit.LITER, milch.unit)
    }

    @Test fun mixedNumbersAddTheFraction() {
        val salz = single("1 1/2 TL", "Salz")
        assertAmount("1.5", salz.amount)
        assertEquals(MeasureUnit.TEASPOON, salz.unit)
        val zucker = single("1½ EL", "Zucker")
        assertAmount("1.5", zucker.amount)
        assertEquals(MeasureUnit.TABLESPOON, zucker.unit)
    }

    @Test fun rangeKeepsUnitAndUsesTheLowerBound() {
        for (text in listOf("2 - 3 EL", "2-3 EL", "2–3 EL", "2 bis 3 EL")) {
            val oel = single(text, "Öl")
            assertAmount("2", oel.amount)
            assertEquals(MeasureUnit.TABLESPOON, oel.unit, text)
            assertEquals("bis 3", oel.note, text)
        }
    }

    @Test fun zeroDensityConvertsInNeitherDirection() {
        val info = ConversionInfo(densityGPerMl = BigDecimal.ZERO)
        assertNull(UnitConverter.convert(Quantity.of(BigDecimal.TEN, MeasureUnit.MILLILITER), Dimension.MASS, info))
        assertNull(UnitConverter.convert(Quantity.of(BigDecimal.TEN, MeasureUnit.GRAM), Dimension.VOLUME, info))
    }

    @Test fun zeroDenominatorDoesNotCrash() {
        assertNull(GermanAmounts.parseNumber("1/0"))
    }

    @Test fun stepMentionReadsFractions() {
        val m = StepIngredientMatcher.mentions("1/2 TL Zimt unterrühren.", mapOf("z" to "Zimt")).single()
        assertAmount("0.5", m.amount)
        assertEquals(MeasureUnit.TEASPOON, m.unit)
    }

    @Test fun timerRangeWithBisUsesLowerBound() {
        assertEquals(listOf(10L), StepTimerParser.find("10 bis 15 Minuten köcheln").map { it.duration.toMinutes() })
    }

    @Test fun hoursAndMinutesJoinedByUndAreOneTimer() {
        assertEquals(listOf(90L), StepTimerParser.find("1 Stunde und 30 Minuten garen").map { it.duration.toMinutes() })
    }
}
