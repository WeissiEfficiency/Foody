package de.foody.core.designsystem.stil.grauen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme

/**
 * Bausteine des Kosmischen Grauens (Stil U) in Immersiv. Der Entwurf setzt IM Fell English (Buchschrift) und Special
 * Elite (Schreibmaschine); bis die Schriften geladen werden dürfen, stehen die Serife und die Monospace des Systems
 * dafür. Farben kommen aus dem Theme, die Glut ist `FoodyTheme.stil.zier`.
 */
object Grauen {
    /** Buchschrift für Titel, mit leichter Laufweite [abstand] (em). */
    fun titel(groesse: Float, abstand: Float = 0.04f) =
        TextStyle(fontFamily = FontFamily.Serif, fontSize = groesse.sp, lineHeight = (groesse * 1.15f).sp, letterSpacing = abstand.em)

    /** Buchschrift für Fließtext, auf Wunsch kursiv. */
    fun text(groesse: Float, kursiv: Boolean = false) = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = groesse.sp,
        lineHeight = (groesse * 1.3f).sp,
        fontStyle = if (kursiv) FontStyle.Italic else FontStyle.Normal,
    )

    /** Schreibmaschine für Zeiten, Mengen und Protokollzeilen. */
    fun maschine(groesse: Float) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = groesse.sp, lineHeight = (groesse * 1.35f).sp)
}

/** Heller Ton für Knopftexte (im Entwurf #E6F2EA), aus dem Text des Stils. */
@Composable
fun grauenHell(): Color = lerp(FoodyTheme.farben.text, Color.White, 0.45f)

/**
 * Knopf des Grauens: eckig, Rahmen in der Glut, Fläche mit einem Hauch Glut, Buchschrift; [umriss] = nur ein Rahmen
 * im Nebenton. Mindestens 48 dp hoch, [aktiv] = false blasst ihn ab; TalkBack hört [beschreibung], falls gesetzt.
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
    val glut = FoodyTheme.stil.zier
    Box(
        modifier.heightIn(min = 48.dp)
            .alpha(if (aktiv) 1f else 0.38f)
            .border(1.dp, if (umriss) FoodyTheme.farben.nebentext else glut)
            .background(if (umriss) Color.Transparent else glut.copy(alpha = 0.1f))
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Grauen.titel(17f, 0.06f), color = grauenHell(), textAlign = TextAlign.Center)
    }
}

/** Kleiner Knopf in Schreibmaschine mit dünnem Rahmen in der Textfarbe („+ notieren“). */
@Composable
fun GrauenNotizknopf(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val f = FoodyTheme.farben
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.heightIn(min = 40.dp).border(1.dp, f.text).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = Grauen.maschine(13f), color = f.text)
        }
    }
}
