package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Gemeinsame Bausteine zum Lesen deutscher Mengenangaben – für den Import und den Kochmodus. */
internal object GermanAmounts {
    /**
     * Zahl wie „0,25“, „1/2“, „½“ oder gemischt „1 1/2“, „1½“ (ohne Anker, zum Einbetten in größere Muster).
     * Brüche stehen vor der Dezimalzahl: Sonst gewinnt bei „1/2 TL“ die erste Alternative mit „1“.
     */
    const val NUMBER = """\d+\s+\d+/\d+|\d+\s*[½¼¾⅓⅔]|\d+/\d+|\d+(?:[.,]\d+)?|[½¼¾⅓⅔]"""

    private val mixedNumber = Regex("""(\d+)(?:\s+(\d+/\d+)|\s*([½¼¾⅓⅔]))""")

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

    fun parseNumber(s: String): BigDecimal? {
        mixedNumber.matchEntire(s)?.let { m ->
            val fraction = parseNumber(m.groupValues[2].ifEmpty { m.groupValues[3] }) ?: return null
            return BigDecimal(m.groupValues[1]) + fraction
        }
        return when (s) {
            "½" -> BigDecimal("0.5")
            "¼" -> BigDecimal("0.25")
            "¾" -> BigDecimal("0.75")
            "⅓" -> BigDecimal("0.333")
            "⅔" -> BigDecimal("0.667")
            else -> if ('/' in s) {
                val (a, b) = s.split('/')
                BigDecimal(b).takeIf { it.signum() != 0 }?.let { BigDecimal(a).divide(it, 3, RoundingMode.HALF_UP) }
            } else {
                s.replace(',', '.').toBigDecimalOrNull()
            }
        }
    }
}
