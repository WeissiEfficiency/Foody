package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KochenTest {
    private fun zeile(id: String, zutat: String, menge: String, einheit: MeasureUnit, optional: Boolean = false) =
        RecipeIngredient(id, zutat, BigDecimal(menge), einheit, optional)

    @Test fun ziehtUmgerechnetAbNieUnterNull() {
        val rezept = Recipe("r", "Brot", 2, listOf(zeile("l", "mehl", "1200", MeasureUnit.GRAM)))
        val umrechnung = mapOf("mehl" to ConversionInfo(packageWeightG = BigDecimal(1000)))
        val vorrat = listOf(Kochen.Vorrat("v", "mehl", BigDecimal.ONE, MeasureUnit.PACKAGE))
        val neu = Kochen.vorratAbziehen(rezept, 2, umrechnung, vorrat)
        assertEquals(0, neu.getValue("v").signum())
    }

    @Test fun optionaleUndUnbekannteZeilenBleiben() {
        val rezept = Recipe("r", "Salat", 2, listOf(
            zeile("l1", "nuss", "50", MeasureUnit.GRAM, optional = true),
            zeile("l2", "fremd", "50", MeasureUnit.GRAM),
        ))
        val vorrat = listOf(Kochen.Vorrat("v1", "nuss", BigDecimal(100), MeasureUnit.GRAM), Kochen.Vorrat("v2", "fremd", BigDecimal(100), MeasureUnit.GRAM))
        assertTrue(Kochen.vorratAbziehen(rezept, 2, mapOf("nuss" to ConversionInfo()), vorrat).isEmpty())
    }

    @Test fun portionenSkalieren() {
        val rezept = Recipe("r", "Reis", 2, listOf(zeile("l", "reis", "200", MeasureUnit.GRAM)))
        val neu = Kochen.vorratAbziehen(rezept, 4, mapOf("reis" to ConversionInfo()), listOf(Kochen.Vorrat("v", "reis", BigDecimal(1000), MeasureUnit.GRAM)))
        assertEquals(0, neu.getValue("v").compareTo(BigDecimal(600)))
    }

    @Test fun mehrereVorratsposten() {
        val rezept = Recipe("r", "Reis", 1, listOf(zeile("l", "reis", "300", MeasureUnit.GRAM)))
        val vorrat = listOf(Kochen.Vorrat("a", "reis", BigDecimal(200), MeasureUnit.GRAM), Kochen.Vorrat("b", "reis", BigDecimal("0.5"), MeasureUnit.KILOGRAM))
        val neu = Kochen.vorratAbziehen(rezept, 1, mapOf("reis" to ConversionInfo()), vorrat)
        assertEquals(0, neu.getValue("a").signum())
        assertEquals(0, neu.getValue("b").compareTo(BigDecimal("0.4")), "Rest in der Einheit des Vorrats (kg)")
    }
}
