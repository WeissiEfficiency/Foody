package de.foody.sync.protocol

import java.math.BigDecimal

/**
 * Grenzen für Zahlen aus fremder Hand (Sync, Sicherung, Eingabefelder) – die einzige Stelle dafür. „1E999999999“ wäre
 * ein gültiges BigDecimal, würde beim Formatieren oder Umrechnen aber Speicher und Zeit fressen.
 */
object ZahlGrenzen {
    const val MAX_LAENGE = 40
    private val SKALA = -6..20
    private const val MAX_PRAEZISION = 30

    fun imRahmen(wert: BigDecimal): Boolean = wert.scale() in SKALA && wert.precision() <= MAX_PRAEZISION

    /** Liest [text] als Zahl innerhalb der Grenzen; sonst `null`. */
    fun lesen(text: String): BigDecimal? {
        if (text.length > MAX_LAENGE) return null
        return try {
            BigDecimal(text).takeIf(::imRahmen)
        } catch (_: NumberFormatException) {
            null
        }
    }
}
