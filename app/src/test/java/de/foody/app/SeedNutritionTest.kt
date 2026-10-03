package de.foody.app

import de.foody.app.data.db.SeedData
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plausibilität der Startdaten: Die Energie muss zu den Makronährstoffen passen (EU-Faktoren, kJ je g:
 * Eiweiß 17, Kohlenhydrate 17, Fett 37, Ballaststoffe 8). Fängt Tippfehler wie 3700 statt 370 kJ ab.
 */
class SeedNutritionTest {
    // Alkohol (29 kJ/g) steht nicht in den Makros – Wein liegt deshalb bewusst darüber
    private val withAlcohol = setOf("Rotwein", "Weißwein")

    @Test fun energyMatchesMacros() {
        val problems = SeedData.ingredients(0).filter { it.energyKj != null && it.canonicalName !in withAlcohol }.mapNotNull { i ->
            fun BigDecimal?.d() = this?.toDouble() ?: 0.0
            val calc = 17 * i.protein.d() + 17 * i.carbs.d() + 37 * i.fat.d() + 8 * i.fiber.d()
            val kj = i.energyKj.d()
            // Kleine Absolutwerte (Brühe, Wasser) schwanken prozentual stark – dort zählt die absolute Abweichung
            val ok = if (calc < 100) kotlin.math.abs(kj - calc) <= 15 else kotlin.math.abs(kj - calc) / calc <= 0.12
            if (ok) null else "${i.canonicalName}: $kj kJ, aus Makros ${calc.toInt()} kJ"
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test fun sugarNeverExceedsCarbs() {
        SeedData.ingredients(0).filter { it.sugar != null && it.carbs != null }.forEach {
            assertTrue(it.sugar!! <= it.carbs!!, "${it.canonicalName}: Zucker > Kohlenhydrate")
        }
    }
}
