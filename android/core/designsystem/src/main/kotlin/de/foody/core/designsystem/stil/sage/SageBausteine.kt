package de.foody.core.designsystem.stil.sage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.token.CinzelFamilie
import de.foody.core.designsystem.token.EbGaramondFamilie

/**
 * Bausteine der Düsteren Sage (Stil Q), die mehrere Screens in Immersiv teilen: Schriften in den Größen des Entwurfs,
 * der helle Titelton, Seitenkopf, Hauptknopf mit Goldschimmer, Nebenknopf und die Raute als Markierung. Alle Farben
 * kommen aus dem Theme (Akzent = `FoodyTheme.stil.zier`), damit die vier Akzente und der Kontrast-Test gelten.
 */
object Sage {
    /** Cinzel in [groesse] sp mit Laufweite [abstand] (em), wie im Entwurf. */
    fun titel(groesse: Float, abstand: Float, gewicht: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontFamily = CinzelFamilie, fontWeight = gewicht, fontSize = groesse.sp, letterSpacing = abstand.em)

    /** EB Garamond in [groesse] sp. */
    fun text(groesse: Float, kursiv: Boolean = false) = TextStyle(
        fontFamily = EbGaramondFamilie,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.3f).sp,
        fontStyle = if (kursiv) FontStyle.Italic else FontStyle.Normal,
    )

    /** Römische Zahl für Schritte und Kapitel (I … XXXIX), ab 40 die arabische. */
    fun roemisch(n: Int): String {
        if (n !in 1..39) return n.toString()
        return "X".repeat(n / 10) + listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")[n % 10]
    }
}

/** Heller Ton für Titel und Zahlen (im Entwurf #F4EEDD), aus dem Text des Stils. */
@Composable
fun sageHell(): Color = lerp(FoodyTheme.farben.text, Color.White, 0.4f)

/**
 * Seitenkopf: Überzeile und Titel in Cinzel (Titel im Akzent), rechts Platz für Zahl und Knöpfe, darunter eine Linie.
 * TalkBack hört [beschreibung] (der normale Seitenname) statt des Titels der Sage.
 */
@Composable
fun SageKopf(unter: String, titel: String, beschreibung: String, modifier: Modifier = Modifier, rechts: @Composable RowScope.() -> Unit = {}) {
    val f = FoodyTheme.farben
    Row(
        modifier.fillMaxWidth().drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(unter, style = Sage.titel(11f, 0.26f, FontWeight.Medium), color = f.nebentext)
            Text(
                titel,
                Modifier.semantics {
                    heading()
                    contentDescription = beschreibung
                },
                style = Sage.titel(30f, 0.1f),
                color = FoodyTheme.farben.primaer,
            )
        }
        rechts()
    }
}

/** Hauptknopf: Rahmen im Akzent, Goldschimmer zur Mitte, Cinzel in Versalien ([schrift]); mindestens 50 dp hoch. */
@Composable
fun SageHauptknopf(
    text: String,
    beschreibung: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    schrift: TextStyle = Sage.titel(15f, 0.18f),
    aktiv: Boolean = true,
) {
    val glut = if (aktiv) FoodyTheme.stil.zier else FoodyTheme.farben.lichtkante
    Box(
        modifier.fillMaxWidth().heightIn(min = 50.dp).border(1.dp, glut)
            .background(Brush.horizontalGradient(listOf(glut.copy(alpha = 0f), glut.copy(alpha = 0.24f), glut.copy(alpha = 0f))))
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .semantics(mergeDescendants = true) { contentDescription = beschreibung }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = schrift, color = if (aktiv) sageHell() else FoodyTheme.farben.nebentext, textAlign = TextAlign.Center)
    }
}

/** Nebenknopf: schmaler Rahmen in der Kantenfarbe, Cinzel klein ([schrift] etwa groß für Pfeile, [farbe] für Warnungen); mindestens 48 dp hoch. */
@Composable
fun SageNebenknopf(
    text: String,
    beschreibung: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    akzent: Boolean = false,
    schrift: TextStyle = Sage.titel(11.5f, 0.12f),
    farbe: Color? = null,
) {
    val f = FoodyTheme.farben
    Box(
        modifier.heightIn(min = 48.dp).border(1.dp, if (akzent) FoodyTheme.stil.zier else f.lichtkante)
            .clickable(role = Role.Button, onClick = onKlick)
            .semantics(mergeDescendants = true) { contentDescription = beschreibung }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = schrift, color = farbe ?: if (akzent) f.primaer else f.text, textAlign = TextAlign.Center)
    }
}

/** Raute als Markierung: Rahmen im Akzent, [gefuellt] = ausgefüllt; [glanz] leuchtet (heute). Nur Grafik. */
@Composable
fun SageRaute(groesse: Dp, gefuellt: Boolean, modifier: Modifier = Modifier, fuellung: Color = FoodyTheme.stil.zier, glanz: Boolean = false) {
    val glut = FoodyTheme.stil.zier
    Box(
        modifier.size(groesse).rotate(45f)
            .then(if (glanz) Modifier.shadow(10.dp, RectangleShape, ambientColor = fuellung, spotColor = fuellung) else Modifier)
            .background(if (gefuellt) fuellung else Color.Transparent)
            .border(1.5.dp, glut),
    )
}

/** Feine Linie unter einer Zeile, in der Farbe der Balkenspur. */
fun Modifier.sageLinieUnten(farbe: Color): Modifier =
    drawBehind { drawLine(farbe, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }

/**
 * [de.foody.core.designsystem.komponenten.FoodyKnopf] in der Düsteren Sage: eckig, Cinzel; Primär mit Rahmen im Akzent
 * und Goldschimmer, Umriss mit feiner Kante, Text nur in Akzentfarbe. Mindestens 48 dp hoch, inaktiv gedämpft.
 */
@Composable
internal fun SageKnopf(text: String, onKlick: () -> Unit, modifier: Modifier, art: KnopfArt, aktiv: Boolean) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val primaer = art == KnopfArt.PRIMAER
    val rand = when {
        art == KnopfArt.TEXT -> null
        !aktiv -> f.balkenSpur
        primaer -> glut
        else -> f.lichtkante
    }
    val schimmer = Brush.horizontalGradient(listOf(glut.copy(alpha = 0.06f), glut.copy(alpha = 0.26f), glut.copy(alpha = 0.06f)))
    Box(
        modifier.heightIn(min = 48.dp)
            .then(if (rand != null) Modifier.border(1.dp, rand) else Modifier)
            .then(if (primaer && aktiv) Modifier.background(schimmer) else Modifier)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Sage.titel(if (primaer) 14f else 12.5f, 0.12f),
            color = when {
                !aktiv -> f.nebentext
                primaer -> sageHell()
                art == KnopfArt.UMRISS -> f.text
                else -> f.primaer
            },
            textAlign = TextAlign.Center,
        )
    }
}
