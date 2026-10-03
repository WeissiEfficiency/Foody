package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Gemeinsame Bausteine zum Lesen deutscher Mengenangaben – für den Import und den Kochmodus. */
internal object GermanAmounts {
    /** Zahl wie „0,25“, „1/2“ oder „½“ (ohne Anker, zum Einbetten in größere Muster). */
    const val NUMBER = """\d+(?:[.,]\d+)?|\d+/\d+|[½¼¾⅓⅔]"""

    val unitWords: Map<String, MeasureUnit> = mapOf(
        "mg" to MeasureUnit.MILLIGRAM, "g" to MeasureUnit.GRAM, "gr" to MeasureUnit.GRAM, "gramm" to MeasureUnit.GRAM,
        "kg" to MeasureUnit.KILOGRAM,
        "ml" to MeasureUnit.MILLILITER, "cl" to MeasureUnit.CENTILITER, "l" to MeasureUnit.LITER, "liter" to MeasureUnit.LITER,
        "tl" to MeasureUnit.TEASPOON, "el" to MeasureUnit.TABLESPOON,
        "dose" to MeasureUnit.CAN, "dose/n" to MeasureUnit.CAN, "dosen" to MeasureUnit.CAN,
        "stück" to MeasureUnit.PIECE, "stück(e)" to MeasureUnit.PIECE, "stk." to MeasureUnit.PIECE, "stk" to MeasureUnit.PIECE,
        "pck." to MeasureUnit.PACKAGE, "päckchen" to MeasureUnit.PACKAGE, "packung" to MeasureUnit.PACKAGE, "pkt." to MeasureUnit.PACKAGE,
    )

    fun unitOf(word: String): MeasureUnit? = unitWords[word.lowercase()]

    fun parseNumber(s: String): BigDecimal? = when (s) {
        "½" -> BigDecimal("0.5")
        "¼" -> BigDecimal("0.25")
        "¾" -> BigDecimal("0.75")
        "⅓" -> BigDecimal("0.333")
        "⅔" -> BigDecimal("0.667")
        else -> if ('/' in s) {
            val (a, b) = s.split('/')
            BigDecimal(a).divide(BigDecimal(b), 3, RoundingMode.HALF_UP)
        } else {
            s.replace(',', '.').toBigDecimalOrNull()
        }
    }
}
