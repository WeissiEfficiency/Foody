package de.foody.core.designsystem.stil.bayrisch

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.AlegreyaFamilie
import de.foody.core.designsystem.token.AlegreyaSansFamilie
import de.foody.core.designsystem.token.UnifrakturFamilie

/**
 * Bausteine des Bayrischen Stils (T) in Immersiv. Der Entwurf setzt UnifrakturCook (Seitentitel), Alegreya und
 * Alegreya Sans. Das Blau ist der gewählte Akzent (auch Grün, Rot oder Braun), Text und Linien das dunkle Braun des Stils.
 */
object Bayrisch {
    /** Alegreya für Titel, Überschriften und Knöpfe. */
    fun titel(groesse: Float, gewicht: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = AlegreyaFamilie, fontWeight = gewicht, fontSize = groesse.sp, lineHeight = (groesse * 1.15f).sp)

    /** Fraktur (UnifrakturCook) nur für den Seitentitel („Heid“, „’s Standl“, „Eistellunga“). */
    fun fraktur(groesse: Float) =
        TextStyle(fontFamily = UnifrakturFamilie, fontWeight = FontWeight.Bold, fontSize = groesse.sp, lineHeight = (groesse * 1.1f).sp)

    /** Alegreya Sans für Fließtext und Angaben. */
    fun text(groesse: Float, gewicht: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = AlegreyaSansFamilie, fontWeight = gewicht, fontSize = groesse.sp, lineHeight = (groesse * 1.3f).sp)

    /** Form der Knöpfe (10 dp) und der Karten (14 dp) wie im Entwurf. */
    val knopfForm = RoundedCornerShape(10.dp)
    val kartenForm = RoundedCornerShape(14.dp)
}

/** Das Blau des Stils: der gewählte Akzent. */
@Composable
fun bayrischBlau(): Color = FoodyTheme.stil.akzent?.farbe ?: FoodyTheme.farben.primaer

/**
 * Knopf des Bayrischen Stils: [voll] = Akzent mit weißer Serife, sonst weiß mit dunklem Rahmen (2 dp). Ecken 10 dp,
 * mindestens 48 dp hoch; TalkBack hört [beschreibung], falls gesetzt.
 */
@Composable
fun BayrischKnopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    voll: Boolean = true,
    beschreibung: String? = null,
    aktiv: Boolean = true,
) {
    val f = FoodyTheme.farben
    val form = Bayrisch.knopfForm
    Box(
        modifier.heightIn(min = 48.dp).alpha(if (aktiv) 1f else 0.38f).clip(form)
            .then(if (voll) Modifier.background(bayrischBlau()) else Modifier.background(Color.White).border(2.dp, f.text, form))
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (voll) Bayrisch.titel(17f) else Bayrisch.text(15f, FontWeight.Bold),
            color = if (voll) Color.White else f.text,
            textAlign = TextAlign.Center,
        )
    }
}
