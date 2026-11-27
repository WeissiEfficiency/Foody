package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.ImFellFamilie
import de.foody.core.designsystem.token.ImFellKapitaelchenFamilie
import de.foody.core.designsystem.token.SpecialEliteFamilie
import de.foody.core.designsystem.token.mitSilbentrennung

/**
 * Bausteine des Kosmischen Grauens (Stil U) in Immersiv. Der Entwurf setzt IM Fell English (Buchschrift) und Special
 * Elite (Schreibmaschine). Farben kommen aus dem Theme, die Glut ist `FoodyTheme.stil.zier`.
 */
object Grauen {
    /** Pergament der Zettel und Rezeptblätter (im Entwurf #D9CDAE), mit Tinte und blasser Tinte darauf. */
    val pergament = Color(0xFFD9CDAE)
    val tinte = Color(0xFF2A2418)
    val tinteNeben = Color(0xFF4A4030)

    /** Necronomicon: getrocknetes Blut für Mengen, Initialen und Rubriken; Grünspan nur sparsam; mattes Messing. */
    val blut = Color(0xFF5C2318)
    val gruenspan = Color(0xFF3D5446)
    val messing = Color(0xFFB9A77C)

    /** Kapitälchen (IM Fell English SC) für Titel, mit leichter Laufweite [abstand] (em). */
    fun titel(groesse: Float, abstand: Float = 0.04f) = TextStyle(
        fontFamily = ImFellKapitaelchenFamilie,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.15f).sp,
        letterSpacing = abstand.em,
    ).mitSilbentrennung()

    /** Rezeptnamen in der Buchschrift ohne Kapitälchen, sonst wie [titel]. */
    fun name(groesse: Float, abstand: Float = 0f) = titel(groesse, abstand).copy(fontFamily = ImFellFamilie)

    /** Buchschrift für Fließtext, auf Wunsch kursiv. */
    fun text(groesse: Float, kursiv: Boolean = false) = TextStyle(
        fontFamily = ImFellFamilie,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.3f).sp,
        fontStyle = if (kursiv) FontStyle.Italic else FontStyle.Normal,
    ).mitSilbentrennung()

    /** Schreibmaschine für Zeiten, Mengen und Protokollzeilen. */
    fun maschine(groesse: Float) = TextStyle(fontFamily = SpecialEliteFamilie, fontSize = groesse.sp, lineHeight = (groesse * 1.35f).sp).mitSilbentrennung()
}

/** Heller Ton für Knopftexte (im Entwurf #E6F2EA), aus dem Text des Stils; im Necronomicon die Tinte selbst. */
@Composable
fun grauenHell(): Color = if (!FoodyTheme.stil.grauenBuch) {
    lerp(FoodyTheme.farben.text, Color.White, 0.45f)
} else {
    // Auf Pergament ist die Tinte selbst der kräftigste Ton
    FoodyTheme.farben.text
}

/** Knopftexte, die im Grauen als gezeichnetes Zeichen erscheinen; `null` = Schrift. */
internal fun grauenZeichenFuer(text: String): GrauenZeichen? = when (text.trim()) {
    "‹" -> GrauenZeichen.ZURUECK
    "›" -> GrauenZeichen.WEITER
    "⋯", "…", "∴" -> GrauenZeichen.MENUE
    "◇", "☆" -> GrauenZeichen.BAND
    "♦", "★" -> GrauenZeichen.BAND_VOLL
    else -> null
}

/** Knöpfe, die nur ein Rechenzeichen sind (Portionen, Mengen): ohne Rahmen, groß gesetzt. */
private val RECHENZEICHEN = setOf("−", "+", "-")

/**
 * Knopf des Grauens ohne Kasten (Plan „Zwei Gesichter“, Schritt 2): Zeichen wie „‹“, „⋯“ oder „◇“ werden gezeichnet
 * ([GrauenZeichen]), − und + stehen frei, der volle Knopf ist ein Wachssiegel mit kursivem Text daneben, [umriss] eine
 * Zeile in Kapitälchen mit einem Schwung darunter. Mindestens 48 × 48 dp, [aktiv] = false blasst ihn ab; TalkBack hört
 * [beschreibung], falls gesetzt.
 */
@Composable
fun GrauenKnopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    beschreibung: String? = null,
    umriss: Boolean = false,
    aktiv: Boolean = true,
) {
    val hell = grauenHell()
    val glut = FoodyTheme.stil.zier
    val zeichen = grauenZeichenFuer(text)
    val basis = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
        .alpha(if (aktiv) 1f else 0.38f)
        .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
        .then(
            when {
                beschreibung != null -> Modifier.semantics { contentDescription = beschreibung }
                zeichen != null -> Modifier.semantics { contentDescription = text }
                else -> Modifier
            },
        )
    when {
        zeichen != null -> Box(basis, contentAlignment = Alignment.Center) { GrauenSymbol(zeichen, hell, groesse = 24.dp) }
        text.trim() in RECHENZEICHEN -> Box(basis, contentAlignment = Alignment.Center) {
            Text(text.trim(), style = Grauen.titel(26f), color = hell)
        }
        !umriss -> Row(
            basis.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GrauenSiegel(GrauenZeichen.AELTESTES, groesse = 46.dp, farbe = hell)
            Text(text, Modifier.weight(1f, fill = false), style = Grauen.text(18f, kursiv = true), color = hell)
        }
        else -> Box(basis.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                Modifier.drawBehind { schwung(glut.copy(alpha = 0.75f)) }.padding(bottom = 7.dp),
                style = Grauen.titel(16f, 0.06f), color = hell, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Ein leichter Schwung unter einem Wort: die Unterstreichung des Buchs (Reiter, Textknöpfe). */
fun DrawScope.schwung(farbe: Color) {
    val y = size.height - 2.5.dp.toPx()
    val l = size.width * 0.12f
    val r = size.width * 0.88f
    val w = r - l
    val pfad = Path().apply {
        moveTo(l, y)
        cubicTo(l + w * 0.25f, y - 4.dp.toPx(), l + w * 0.5f, y + 3.dp.toPx(), l + w * 0.75f, y - 1.5.dp.toPx())
        quadraticTo(l + w * 0.9f, y - 3.dp.toPx(), r, y - 0.5.dp.toPx())
    }
    drawPath(pfad, farbe, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Kleiner Knopf in Schreibmaschine („+ notieren“, „Gegessen?“) ohne Kasten: ein führendes „+“ wird zur Feder, darunter
 * ein Schwung in Glut.
 */
@Composable
fun GrauenNotizknopf(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val f = FoodyTheme.farben
    val feder = text.startsWith("+")
    val wort = if (feder) text.removePrefix("+").trim() else text
    val glut = FoodyTheme.stil.zier
    Row(
        modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feder) GrauenSymbol(GrauenZeichen.FEDER, glut, groesse = 18.dp)
        Text(
            wort,
            Modifier.drawBehind { schwung(glut.copy(alpha = 0.6f)) }.padding(bottom = 6.dp),
            style = Grauen.maschine(13f), color = f.text,
        )
    }
}

/**
 * Wahlwort des Grauens statt eines Chips (Tage, Mahlzeiten, Filter): Kapitälchen ohne Rahmen, gewählt hell mit einem
 * Schwung in Glut darunter, sonst im Nebentext. Meldet „ausgewählt“ an TalkBack, mindestens 48 dp.
 */
@Composable
fun GrauenWahlwort(text: String, ausgewaehlt: Boolean, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val glut = FoodyTheme.stil.zier
    Box(
        modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .selectable(selected = ausgewaehlt, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            Modifier.then(if (ausgewaehlt) Modifier.drawBehind { schwung(glut) } else Modifier).padding(bottom = 7.dp),
            style = Grauen.titel(15.5f, 0.04f),
            color = if (ausgewaehlt) grauenHell() else FoodyTheme.farben.nebentext,
            textAlign = TextAlign.Center,
        )
    }
}
