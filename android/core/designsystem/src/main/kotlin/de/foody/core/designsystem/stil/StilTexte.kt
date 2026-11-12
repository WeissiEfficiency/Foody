package de.foody.core.designsystem.stil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import de.foody.core.designsystem.FoodyTheme

/**
 * Wort für Überschriften, Knöpfe und Leertexte je Stil: In Immersiv das Wort des gewählten Stils, sonst (und wenn der
 * Stil keines hat) [standard]. Namen, Mengen und Fehlermeldungen laufen nie hierüber. Wer ein Stilwort zeigt, gibt
 * TalkBack den normalen Begriff (`contentDescription`), wo das Wort allein unklar wäre.
 */
@Composable
@ReadOnlyComposable
fun stilText(
    standard: String,
    sage: String? = null,
    anime: String? = null,
    edo: String? = null,
    bayrisch: String? = null,
    grauen: String? = null,
    kreuzer: String? = null,
): String {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return standard
    return when (s.stil) {
        FoodyStil.MILCHGLAS -> null
        FoodyStil.SAGE -> sage
        FoodyStil.ANIME -> anime
        FoodyStil.EDO -> edo
        FoodyStil.BAYRISCH -> bayrisch
        FoodyStil.GRAUEN -> grauen
        FoodyStil.KREUZER -> kreuzer
    } ?: standard
}
