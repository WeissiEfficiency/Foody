package de.foody.core.designsystem.stil.edo

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme

/**
 * Bausteine des Edo-Stils (S) in Immersiv. Der Entwurf setzt Kaisei Decol (Titel) und Zen Kaku Gothic New; bis die
 * Schriften geladen werden dürfen, stehen Serife und Grotesk des Systems dafür. Das Zinnober („Shu“) ist der gewählte
 * Akzent (auch Indigo, Moos oder Ocker), Papier, Holz und Montierung kommen aus dem Entwurf.
 */
object Edo {
    /** Papier des Rollbilds. */
    val papier = Color(0xFFF6EFDF)

    /** Rand des Papiers. */
    val papierRand = Color(0xFFC9B98F)

    /** Feine Linie im Papier. */
    val papierLinie = Color(0xFFE2D6BC)

    /** Holz der Stäbe oben und unten. */
    val holz = Color(0xFF3A2516)

    /** Indigo der Montierung. */
    val indigo = Color(0xFF24375A)

    /** Gestrichelter Rand eines leeren Stempels. */
    val stempelLeer = Color(0xFF8A7F70)

    /** Serife für Titel. */
    fun titel(groesse: Float) =
        TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = groesse.sp, lineHeight = (groesse * 1.25f).sp)

    /** Grotesk für Text und Angaben. */
    fun text(groesse: Float, gewicht: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = gewicht, fontSize = groesse.sp, lineHeight = (groesse * 1.35f).sp)

    /** Gesperrte Kopfzeile („HEUTE ABEND · 24. OKTOBER“). */
    fun kopf(groesse: Float = 11.5f) = text(groesse, FontWeight.Bold).copy(letterSpacing = 0.14.em)

    /** Fast eckige Form (2 dp) der Knöpfe. */
    val form = RoundedCornerShape(2.dp)
}

/** Das Zinnober des Stils: der gewählte Akzent. */
@Composable
fun edoShu(): Color = FoodyTheme.stil.akzent?.farbe ?: FoodyTheme.farben.primaer

/**
 * Knopf im Edo-Stil: [voll] = Zinnober mit weißer, gesperrter Schrift, sonst dünner Rahmen in Tusche. Mindestens
 * 48 dp hoch; TalkBack hört [beschreibung], falls gesetzt.
 */
@Composable
fun EdoKnopf(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, voll: Boolean = true, beschreibung: String? = null) {
    val f = FoodyTheme.farben
    Box(
        modifier.heightIn(min = 48.dp).clip(Edo.form)
            .then(if (voll) Modifier.background(edoShu()) else Modifier.border(1.5.dp, f.text, Edo.form))
            .clickable(role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (voll) Edo.text(14.5f, FontWeight.Bold).copy(letterSpacing = 0.12.em) else Edo.text(14.5f, FontWeight.Bold),
            color = if (voll) Color.White else f.text,
            textAlign = TextAlign.Center,
        )
    }
}
