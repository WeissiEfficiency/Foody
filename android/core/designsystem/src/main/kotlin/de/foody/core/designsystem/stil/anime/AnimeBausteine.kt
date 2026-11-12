package de.foody.core.designsystem.stil.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme

/**
 * Bausteine des Anime-Stils (R) in Immersiv. Der Entwurf setzt Dela Gothic One (Titel) und M PLUS Rounded 1c; bis
 * die Schriften geladen werden dürfen, steht die Grotesk des Systems in sehr fetten Schnitten dafür. Kanten und
 * harte Schatten in der Tintenfarbe des Stils, Flächen im gewählten Akzent und im Gelb des Entwurfs.
 */
object Anime {
    /** Gelb der Knöpfe und Abzeichen (im Entwurf #FFE45C). */
    val gelb = Color(0xFFFFE45C)

    /** Türkis der Eintragen-Kachel (im Entwurf #3EC6E0). */
    val tuerkis = Color(0xFF3EC6E0)

    /** Plakative Titelschrift (sehr fett). */
    fun titel(groesse: Float, abstand: Float = 0.02f) = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.12f).sp,
        letterSpacing = abstand.em,
    )

    /** Runde, fette Textschrift. */
    fun text(groesse: Float, gewicht: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = gewicht, fontSize = groesse.sp, lineHeight = (groesse * 1.35f).sp)

    /** Pille für Knöpfe und Abzeichen. */
    val pille = RoundedCornerShape(999.dp)
}

/** Harter Schatten wie im Comic: die [form] in der Tintenfarbe, um [versatz] nach rechts unten verschoben. */
fun Modifier.animeSchatten(form: Shape, farbe: Color, versatz: Dp = 5.dp): Modifier = drawBehind {
    val v = versatz.toPx()
    translate(v, v) { drawOutline(form.createOutline(size, layoutDirection, this), farbe) }
}

/**
 * Gelber Pillenknopf mit dicker Kante und hartem Schatten; [voll] = false ist eine weiße Pille ohne Schatten.
 * Mindestens 48 dp hoch, [aktiv] = false blasst ihn ab.
 */
@Composable
fun AnimeKnopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    beschreibung: String? = null,
    voll: Boolean = true,
    aktiv: Boolean = true,
) {
    val tinte = FoodyTheme.farben.text
    Box(
        modifier.heightIn(min = if (voll) 50.dp else 48.dp)
            .alpha(if (aktiv) 1f else 0.38f)
            .then(if (voll) Modifier.animeSchatten(Anime.pille, tinte, 3.dp) else Modifier)
            .clip(Anime.pille).background(if (voll) Anime.gelb else Color.White).border(if (voll) 3.dp else 2.5.dp, tinte, Anime.pille)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (voll) Anime.titel(18f, 0.04f) else Anime.text(15f, FontWeight.ExtraBold), color = tinte, textAlign = TextAlign.Center)
    }
}

/** Weiße Pille mit Kante für Angaben („2 Port.“, „540 kcal“). */
@Composable
fun AnimePille(text: String, modifier: Modifier = Modifier, grund: Color = Color.White, schrift: Color? = null) {
    val tinte = FoodyTheme.farben.text
    Box(modifier.background(grund, Anime.pille).border(2.dp, tinte, Anime.pille).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(text, style = Anime.text(13f, FontWeight.ExtraBold), color = schrift ?: tinte)
    }
}
