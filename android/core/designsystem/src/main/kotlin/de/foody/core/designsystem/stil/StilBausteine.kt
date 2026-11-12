package de.foody.core.designsystem.stil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.AnimeKnopf
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.grauenHell
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber

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

/** Aussehen eines Filter-Chips im Stil: Form, Fläche, Rahmen und Schrift. */
internal class ChipAussehen(
    val form: Shape,
    val flaeche: Color,
    val rand: Color,
    val randBreite: Dp,
    val schrift: TextStyle,
    val schriftFarbe: Color,
)

/**
 * Filter-Chip der Stile R–V in Immersiv, gewählt wie der volle Knopf des Stils, sonst wie sein Umriss. `null` = keiner
 * (Milchglas, Ruhig, Sage hat ihren eigenen).
 */
@Composable
internal fun stilChip(ausgewaehlt: Boolean): ChipAussehen? {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return null
    val f = FoodyTheme.farben
    return when (s.stil) {
        FoodyStil.ANIME -> ChipAussehen(
            Anime.pille, if (ausgewaehlt) Anime.gelb else Color.White, f.text, 2.dp, Anime.text(14.5f, FontWeight.ExtraBold), f.text,
        )
        FoodyStil.EDO -> {
            val shu = edoShu()
            ChipAussehen(
                Edo.form,
                if (ausgewaehlt) shu else Color.Transparent,
                if (ausgewaehlt) shu else f.text.copy(alpha = 0.6f),
                1.dp,
                Edo.text(14.5f, FontWeight.Bold),
                if (ausgewaehlt) Color.White else f.text,
            )
        }
        FoodyStil.BAYRISCH -> {
            val blau = bayrischBlau()
            ChipAussehen(
                Bayrisch.knopfForm,
                if (ausgewaehlt) blau else Color.White,
                if (ausgewaehlt) blau else f.text,
                1.5.dp,
                Bayrisch.text(15f, FontWeight.Bold),
                if (ausgewaehlt) Color.White else f.text,
            )
        }
        FoodyStil.GRAUEN -> ChipAussehen(
            RectangleShape,
            if (ausgewaehlt) s.zier.copy(alpha = 0.14f) else Color.Transparent,
            if (ausgewaehlt) s.zier else f.nebentext.copy(alpha = 0.6f),
            1.dp,
            Grauen.titel(15.5f, 0.04f),
            if (ausgewaehlt) grauenHell() else f.text,
        )
        FoodyStil.KREUZER -> {
            val amber = kreuzerAmber()
            ChipAussehen(
                Kreuzer.ecke(8.dp),
                if (ausgewaehlt) amber else Color.Transparent,
                if (ausgewaehlt) amber else amber.copy(alpha = 0.45f),
                1.dp,
                Kreuzer.titel(14f, FontWeight.SemiBold, 0.04f),
                if (ausgewaehlt) Kreuzer.aufAmber else f.text,
            )
        }
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> null
    }
}

/** Aussehen eines Blatts im Stil: Form, Griff und was hinter dem Inhalt gezeichnet wird. */
internal class BlattAussehen(val form: Shape, val griff: Color, val griffForm: Shape, val griffBreite: Dp, val zeichnen: DrawScope.() -> Unit)

/**
 * Bottom Sheet der Stile R–V in Immersiv: Grund des Stils (er hält AA mit dem Text) und oben die Kante des Stils –
 * Tintenrand (Anime), Indigoband (Edo), Rautenband (Bayrisch), doppelte Glutlinie (Grauen), Amberlinie mit
 * abgeschnittener Ecke (Kreuzer). `null` = keines (Milchglas, Ruhig, Sage hat ihr eigenes).
 */
@Composable
internal fun stilBlatt(): BlattAussehen? {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return null
    val f = FoodyTheme.farben
    val grund = f.grund
    return when (s.stil) {
        FoodyStil.ANIME -> {
            val form = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            BlattAussehen(form, f.text, Anime.pille, 44.dp) {
                drawRect(grund)
                drawOutline(form.createOutline(size, layoutDirection, this), f.text, style = Stroke(3.dp.toPx()))
            }
        }
        FoodyStil.EDO -> BlattAussehen(RectangleShape, Edo.indigo, RectangleShape, 40.dp) {
            drawRect(grund)
            drawRect(Edo.indigo, size = Size(size.width, 6.dp.toPx()))
            drawLine(Edo.holz, Offset(0f, 7.dp.toPx()), Offset(size.width, 7.dp.toPx()), 1.dp.toPx())
        }
        FoodyStil.BAYRISCH -> {
            val blau = bayrischBlau()
            val form = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            BlattAussehen(form, f.text.copy(alpha = 0.5f), Anime.pille, 40.dp) {
                drawRect(grund)
                rautenreihe(blau, 10.dp.toPx())
            }
        }
        FoodyStil.GRAUEN -> {
            val glut = s.zier
            BlattAussehen(RectangleShape, glut, RectangleShape, 40.dp) {
                drawRect(Brush.verticalGradient(listOf(lerp(grund, glut, 0.06f), grund)))
                drawLine(glut, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                drawLine(glut.copy(alpha = 0.5f), Offset(0f, 4.dp.toPx()), Offset(size.width, 4.dp.toPx()), 1.dp.toPx())
            }
        }
        FoodyStil.KREUZER -> {
            val amber = kreuzerAmber()
            // Nur oben rechts abgeschnitten; unten liegt der Inhalt am Rand
            val d = LocalDensity.current.density
            val form = GenericShape { g, _ ->
                val schnitt = minOf(28f * d, g.width / 2f, g.height / 2f)
                moveTo(0f, 0f)
                lineTo(g.width - schnitt, 0f)
                lineTo(g.width, schnitt)
                lineTo(g.width, g.height)
                lineTo(0f, g.height)
                close()
            }
            BlattAussehen(form, amber, RectangleShape, 40.dp) {
                drawRect(grund)
                drawOutline(form.createOutline(size, layoutDirection, this), amber.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
            }
        }
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> null
    }
}

/** Eine Reihe weiß-blauer Rauten am oberen Rand, [hoehe] hoch. */
private fun DrawScope.rautenreihe(farbe: Color, hoehe: Float) {
    val breite = hoehe * 1.4f
    drawRect(Color.White, size = Size(size.width, hoehe))
    var x = 0f
    while (x < size.width) {
        val raute = Path().apply {
            moveTo(x + breite / 2, 0f)
            lineTo(x + breite, hoehe / 2)
            lineTo(x + breite / 2, hoehe)
            lineTo(x, hoehe / 2)
            close()
        }
        drawPath(raute, farbe)
        x += breite
    }
}
