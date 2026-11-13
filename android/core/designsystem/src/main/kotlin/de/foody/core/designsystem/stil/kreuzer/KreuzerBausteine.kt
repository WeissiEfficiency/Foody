package de.foody.core.designsystem.stil.kreuzer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.Exo2Familie
import de.foody.core.designsystem.token.ShareTechMonoFamilie

/**
 * Bausteine des Sternenkreuzers (Stil V) in Immersiv. Der Entwurf setzt Exo 2 und Share Tech Mono. Zwei Farben: Amber (der gewählte
 * Akzent) für Handlungen, Holo-Blau (`FoodyTheme.stil.zier`) für Anzeigen.
 */
object Kreuzer {
    /** Grotesk für Titel und Knöpfe. */
    fun titel(groesse: Float, gewicht: FontWeight = FontWeight.ExtraBold, abstand: Float = 0.02f) = TextStyle(
        fontFamily = Exo2Familie,
        fontWeight = gewicht,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.2f).sp,
        letterSpacing = abstand.em,
    )

    /** Monospace für Anzeigen, Kennungen und Kopfzeilen. */
    fun mono(groesse: Float, abstand: Float = 0.06f) =
        TextStyle(fontFamily = ShareTechMonoFamilie, fontSize = groesse.sp, lineHeight = (groesse * 1.3f).sp, letterSpacing = abstand.em)

    /** Text auf Amber (im Entwurf #1A1206). */
    val aufAmber = Color(0xFF1A1206)

    /** Rechteck mit zwei abgeschnittenen Ecken (oben rechts, unten links) wie die Karten des Entwurfs. */
    fun ecke(schnitt: Dp = 16.dp): Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val s = with(density) { schnitt.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
            return Outline.Generic(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width - s, 0f)
                    lineTo(size.width, s)
                    lineTo(size.width, size.height)
                    lineTo(s, size.height)
                    lineTo(0f, size.height - s)
                    close()
                },
            )
        }
    }
}

/** Amber des Sternenkreuzers: der gewählte Akzent (Handlungen, Sonne). */
@Composable
fun kreuzerAmber(): Color = FoodyTheme.stil.akzent?.farbe ?: FoodyTheme.farben.primaer

/**
 * Knopf des Sternenkreuzers: [voll] = Amber mit dunkler Schrift, sonst Rahmen in Amber (halb durchsichtig). Eckig,
 * mindestens 48 dp hoch; TalkBack hört [beschreibung], falls gesetzt.
 */
@Composable
fun KreuzerKnopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    voll: Boolean = true,
    beschreibung: String? = null,
    aktiv: Boolean = true,
) {
    val amber = kreuzerAmber()
    Box(
        modifier.heightIn(min = 48.dp)
            .alpha(if (aktiv) 1f else 0.38f)
            .then(if (voll) Modifier.background(amber) else Modifier.border(1.dp, amber.copy(alpha = 0.45f)))
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Kreuzer.titel(15f, if (voll) FontWeight.ExtraBold else FontWeight.SemiBold, 0.04f),
            color = if (voll) Kreuzer.aufAmber else FoodyTheme.farben.text,
            textAlign = TextAlign.Center,
        )
    }
}

/** Verweis in Monospace und Holo-Blau („BORDBUCH ÖFFNEN ›“), mindestens 48 dp hoch. */
@Composable
fun KreuzerVerweis(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null) {
    Box(
        modifier.heightIn(min = 48.dp).clip(FoodyTheme.formen.klein).clickable(role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Kreuzer.mono(12.5f), color = FoodyTheme.stil.zier)
    }
}
