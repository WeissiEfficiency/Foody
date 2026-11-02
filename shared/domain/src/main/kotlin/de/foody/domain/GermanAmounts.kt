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
        // Beutel und Tüten sind Packungen: „1 Beutel Trockenhefe“ = 7 g wie „1 Pck.“
        "beutel" to MeasureUnit.PACKAGE, "btl." to MeasureUnit.PACKAGE, "tüte" to MeasureUnit.PACKAGE, "tüte/n" to MeasureUnit.PACKAGE,
        "tüten" to MeasureUnit.PACKAGE, "tütchen" to MeasureUnit.PACKAGE,
    )

    fun unitOf(word: String): MeasureUnit? = unitWords[word.lowercase()]

    /**
     * Küchenmaße ohne Waage als Faustwert je Einheit: Prise und Messerspitze ≈ 0,5 g, Schuss ≈ 20 ml, Spritzer ≈ 2 ml,
     * Tasse ≈ 150 ml (Kaffeetasse). Gilt für jede Zutat; genauer wird es nur mit der Waage.
     */
    private val schaetzmengen: Map<String, Pair<BigDecimal, MeasureUnit>> = buildMap {
        fun maß(menge: String, einheit: MeasureUnit, vararg woerter: String) = woerter.forEach { put(it, BigDecimal(menge) to einheit) }
        maß("0.5", MeasureUnit.GRAM, "prise", "prisen", "msp.", "msp", "messerspitze", "messerspitzen")
        maß("20", MeasureUnit.MILLILITER, "schuss")
        maß("2", MeasureUnit.MILLILITER, "spritzer")
        maß("150", MeasureUnit.MILLILITER, "tasse", "tassen")
    }

    /** Menge je Einheit für ein Küchenmaß wie „Prise(n)“ oder „Tasse/n“; null für andere Wörter. */
    fun schaetzmenge(word: String): Pair<BigDecimal, MeasureUnit>? =
        schaetzmengen[word.lowercase().replace(Regex("""\((n|e|en)\)$|/(n|e|en)$"""), "")]

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
