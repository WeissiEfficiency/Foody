package de.foody.feature.kochen

import de.foody.core.data.rezepte.RezeptVoll
import de.foody.domain.MeasureUnit
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.domain.RecipeScaler
import de.foody.domain.StepIngredientMatcher
import de.foody.domain.StepTimer
import de.foody.domain.StepTimerParser
import java.math.BigDecimal
import java.time.Duration

/** Eine Zeile auf der Seite „Bereitlegen“; [menge] `null` = ohne Menge (dann steht die Angabe im [hinweis]). */
data class BereitZeile(val zeilenIndex: Int, val menge: String?, val name: String, val hinweis: String?, val optional: Boolean)

/** Eine im Schritt erwähnte Zutat: [menge] die Teilmenge aus dem Schritt, sonst die Gesamtmenge; [gesamt] nur neben einer Teilmenge. */
data class SchrittZutat(val zeilenIndex: Int, val name: String, val menge: String?, val gesamt: String?)

data class KochSchritt(val text: String, val zutaten: List<SchrittZutat>, val timer: List<StepTimer>)

private val TIMER_MIN = Duration.ofSeconds(1)
private val TIMER_MAX = Duration.ofHours(24)

/** Alle Zutaten des Rezepts für [portionen]. */
fun bereitlegen(voll: RezeptVoll, portionen: Int): List<BereitZeile> = voll.zeilen.mapIndexed { i, z ->
    BereitZeile(i, mengeText(skaliert(z.menge, voll, portionen), z.einheit), z.name, z.hinweis, z.optional)
}

/**
 * Je Arbeitsschritt die erwähnten Zutaten (über [StepIngredientMatcher], Schlüssel = Zeilenindex, damit dieselbe
 * Zutat in zwei Zeilen zweimal zählt) mit Mengen für [portionen], dazu die Timer von 1 s bis 24 h.
 */
fun kochSchritte(voll: RezeptVoll, portionen: Int): List<KochSchritt> {
    val namen = voll.zeilen.withIndex().associate { (i, z) -> i.toString() to z.name }
    return voll.schritte.map { text ->
        val zutaten = StepIngredientMatcher.mentions(text, namen).map { m ->
            val i = m.ingredientId.toInt()
            val z = voll.zeilen[i]
            val gesamt = mengeText(skaliert(z.menge, voll, portionen), z.einheit)
            val teil = m.amount?.let { a ->
                val einheit = m.unit ?: z.einheit
                mengeText(skaliert(a, voll, portionen), einheit)
            }
            if (teil != null) SchrittZutat(i, z.name, teil, gesamt) else SchrittZutat(i, z.name, gesamt, null)
        }
        KochSchritt(text, zutaten, StepTimerParser.find(text).filter { it.duration in TIMER_MIN..TIMER_MAX })
    }
}

private fun skaliert(menge: BigDecimal, voll: RezeptVoll, portionen: Int): BigDecimal =
    RecipeScaler.scale(menge, voll.rezept.defaultServings.coerceAtLeast(1), portionen)

/** „400 g“; `null` für Menge 0 (= ohne Menge). */
private fun mengeText(menge: BigDecimal, einheit: MeasureUnit): String? =
    if (menge.signum() == 0) null else QuantityFormatter.format(Quantity.of(menge, einheit), einheit)
