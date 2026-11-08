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
    /** Eiweiß, Kohlenhydrate, Fett in Balken und Legenden; in allen Farbwelten gleich, nur hell und dunkel verschieden. */
    val makro: MakroFarben,
) {
    companion object {
        /** Farben der [Farbwelt.Standard], hell. */
        val Hell: FoodyFarben get() = Farbwelt.Standard.werte(dunkel = false).farben

        /** Farben der [Farbwelt.Standard], dunkel. */
        val Dunkel: FoodyFarben get() = Farbwelt.Standard.werte(dunkel = true).farben
    }
}

/**
 * Drei klar getrennte Farbtöne für die Nährwerte: Himmelblau (Eiweiß), Bernstein (Kohlenhydrate), Magenta (Fett). Nur für
 * Grafik (Balken, Punkte), nie für Text; der [kontrastPaare]-Test prüft 3:1 auf jedem Glas, `MakroFarbenTest` den
 * Abstand der Töne.
 */
@Immutable
data class MakroFarben(val eiweiss: Color, val kohlenhydrate: Color, val fett: Color) {
    companion object {
        val Hell = MakroFarben(eiweiss = Color(0xFF1F76A8), kohlenhydrate = Color(0xFFA8650F), fett = Color(0xFFA83C7C))
        val Dunkel = MakroFarben(eiweiss = Color(0xFF6CC0E6), kohlenhydrate = Color(0xFFE3A653), fett = Color(0xFFEA8FC4))
    }
}
