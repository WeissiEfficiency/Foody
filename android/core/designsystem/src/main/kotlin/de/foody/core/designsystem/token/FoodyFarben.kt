package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Farben der Richtung „Ofenwarm“: Creme, Tomate, Oliv, Safran. Werte aus der Spec Phase 4, Abschnitt 2; der
 * [kontrastPaare]-Test entscheidet, ob sie WCAG 2.2 AA halten.
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
        val Hell = FoodyFarben(
            grund = Color(0xFFFBF4EA),
            text = Color(0xFF2B1D14),
            nebentext = Color(0xFF6B5547),
            primaer = Color(0xFFA8341A),
            aufPrimaer = Color(0xFFFFFFFF),
            primaerBehaelter = Color(0xFFFCE0D3),
            aufPrimaerBehaelter = Color(0xFF7A220E),
            oliv = Color(0xFF5C6B2A),
            safran = Color(0xFFBD741B),
            balkenSpur = Color(0xFFEADCC9),
            lichtkante = Color.White.copy(alpha = 0.78f),
        )

        val Dunkel = FoodyFarben(
            grund = Color(0xFF1A1310),
            text = Color(0xFFF5EADF),
            nebentext = Color(0xFFC9B6A7),
            primaer = Color(0xFFF08A65),
            aufPrimaer = Color(0xFF3A1206),
            primaerBehaelter = Color(0xFF5A2212),
            aufPrimaerBehaelter = Color(0xFFFFDCCD),
            oliv = Color(0xFFBCC67F),
            safran = Color(0xFFE3A653),
            balkenSpur = Color(0xFF3A2C24),
            lichtkante = Color.White.copy(alpha = 0.12f),
        )
    }
}
