package de.foody.core.designsystem.stil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.AnimeKnopf
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf

/** Wo ein Titel steht: Seitenkopf (mit oder ohne Zurück) oder Blatt. */
enum class TitelArt { SEITE, SEITE_ZURUECK, BLATT }

/**
 * Titelschrift der Stile R–V in Immersiv für Seitenköpfe und Blätter, in den Größen der normalen Ansicht (höchstens
 * 30 sp), damit lange Wörter nicht öfter umbrechen. `null` = keine eigene (Milchglas, Ruhig, Sage hat ihre eigene).
 */
@Composable
fun stilTitel(art: TitelArt): TextStyle? {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return null
    val g = when (art) {
        TitelArt.SEITE -> 30f
        TitelArt.SEITE_ZURUECK -> 24f
        TitelArt.BLATT -> 22f
    }
    return when (s.stil) {
        FoodyStil.GRAUEN -> Grauen.titel(g, 0.02f)
        FoodyStil.KREUZER -> Kreuzer.titel(g - 2f)
        FoodyStil.BAYRISCH -> Bayrisch.titel(g, FontWeight.ExtraBold)
        FoodyStil.EDO -> Edo.titel(g - 2f)
        FoodyStil.ANIME -> {
            val d = LocalDensity.current.density
            Anime.titel(g, 0f).copy(
                textGeometricTransform = TextGeometricTransform(skewX = -0.14f),
                shadow = Shadow(s.zier, Offset(2f * d, 2f * d), 0f),
            )
        }
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> null
    }
}

/**
 * Knöpfe der Stile R–V in Immersiv: Primär und Umriss als Knopf des Stils, Text als Verweis in der Schrift des Stils.
 * Gibt `false` zurück, wenn der Stil keinen eigenen Knopf hat; dann zeichnet [de.foody.core.designsystem.komponenten.FoodyKnopf] den normalen.
 */
@Composable
internal fun StilKnopf(text: String, onKlick: () -> Unit, modifier: Modifier, art: KnopfArt, aktiv: Boolean): Boolean {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return false
    if (art == KnopfArt.TEXT && s.stil != FoodyStil.MILCHGLAS && s.stil != FoodyStil.SAGE) {
        StilTextKnopf(text, onKlick, modifier, aktiv)
        return true
    }
    val voll = art == KnopfArt.PRIMAER
    when (s.stil) {
        FoodyStil.GRAUEN -> GrauenKnopf(text, onKlick, modifier, umriss = !voll, aktiv = aktiv)
        FoodyStil.KREUZER -> KreuzerKnopf(text, onKlick, modifier, voll = voll, aktiv = aktiv)
        FoodyStil.BAYRISCH -> BayrischKnopf(text, onKlick, modifier, voll = voll, aktiv = aktiv)
        FoodyStil.EDO -> EdoKnopf(text, onKlick, modifier, voll = voll, aktiv = aktiv)
        FoodyStil.ANIME -> AnimeKnopf(text, onKlick, modifier, voll = voll, aktiv = aktiv)
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> return false
    }
    return true
}

/** Text-Knopf (Verweis) in der Schrift des Stils und im Primärton, mindestens 48 dp hoch. */
@Composable
private fun StilTextKnopf(text: String, onKlick: () -> Unit, modifier: Modifier, aktiv: Boolean) {
    val stil = when (FoodyTheme.stil.stil) {
        FoodyStil.GRAUEN -> Grauen.titel(16f, 0.04f)
        FoodyStil.KREUZER -> Kreuzer.mono(14f)
        FoodyStil.BAYRISCH -> Bayrisch.titel(16f)
        FoodyStil.EDO -> Edo.text(15f, FontWeight.Bold)
        else -> Anime.text(15f, FontWeight.ExtraBold)
    }
    Box(
        modifier.heightIn(min = 48.dp).alpha(if (aktiv) 1f else 0.38f)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = stil, color = FoodyTheme.farben.primaer, textAlign = TextAlign.Center)
    }
}
