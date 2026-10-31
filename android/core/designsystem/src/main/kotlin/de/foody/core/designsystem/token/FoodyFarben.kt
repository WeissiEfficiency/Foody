package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Farbrollen einer [Farbwelt] in einem Modus. Die Werte stehen in [Farbwelt]; der [kontrastPaare]-Test entscheidet, ob
 * sie WCAG 2.2 AA halten.
 */
@Immutable
data class FoodyFarben(
    val grund: Color,
    val text: Color,
    val nebentext: Color,
    val primaer: Color,
    val aufPrimaer: Color,
    val primaerBehaelter: Color,
    val aufPrimaerBehaelter: Color,
    val oliv: Color,
    /** Nur für Grafik (z. B. Kohlenhydrate im Nährwertbalken), nie für Text. */
    val safran: Color,
    val balkenSpur: Color,
    val lichtkante: Color,
) {
    companion object {
        /** Farben der [Farbwelt.Standard], hell. */
        val Hell: FoodyFarben get() = Farbwelt.Standard.werte(dunkel = false).farben

        /** Farben der [Farbwelt.Standard], dunkel. */
        val Dunkel: FoodyFarben get() = Farbwelt.Standard.werte(dunkel = true).farben
    }
}
