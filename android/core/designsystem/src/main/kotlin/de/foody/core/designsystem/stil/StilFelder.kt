package de.foody.core.designsystem.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.animeSchatten
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber

/**
 * Eingabefeld der Stile R–V wie in den Entwürfen: das [etikett] klein darüber (Versalien außer Bayrisch), das Feld im
 * Aussehen des Stils – weiße Fläche mit Tintenrand (Anime, Bayrisch), Unterstrich (Edo), dünne Linie (Grauen, Kreuzer,
 * beim Tippen im Akzent). TalkBack liest das Feld mit dem Etikett. Mindestens 48 dp hoch; mehrzeilig ([einzeilig]
 * `false`) mindestens [zeilen] Zeilen. Nur in Szenen der Stile R–V verwenden.
 */
@Composable
fun StilFeld(
    etikett: String,
    wert: String,
    onWert: (String) -> Unit,
    modifier: Modifier = Modifier,
    tastatur: KeyboardType = KeyboardType.Text,
    einzeilig: Boolean = true,
    zeilen: Int = 3,
    satzanfangGross: Boolean = false,
) {
    val f = FoodyTheme.farben
    val stil = FoodyTheme.stil.stil
    val quelle = remember { MutableInteractionSource() }
    val fokus by quelle.collectIsFocusedAsState()
    val a = feldAussehen(stil, fokus)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (stil == FoodyStil.BAYRISCH) etikett else etikett.uppercase(),
            Modifier.clearAndSetSemantics {},
            style = a.etikett,
            color = f.nebentext,
        )
        BasicTextField(
            wert,
            onWert,
            Modifier.fillMaxWidth().semantics { contentDescription = etikett },
            textStyle = a.schrift.copy(color = f.text),
            singleLine = einzeilig,
            minLines = if (einzeilig) 1 else zeilen,
            cursorBrush = SolidColor(f.primaer),
            interactionSource = quelle,
            keyboardOptions = KeyboardOptions(
                keyboardType = tastatur,
                capitalization = if (satzanfangGross) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            ),
            decorationBox = { feld ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(a.rahmen).padding(horizontal = a.innen, vertical = 10.dp)) { feld() }
            },
        )
    }
}

/** Aussehen eines Felds im Stil: Rahmen (Fläche und Linien), Abstand innen, Schriften. */
private class FeldAussehen(val rahmen: Modifier, val innen: Dp, val schrift: TextStyle, val etikett: TextStyle)

@Composable
private fun feldAussehen(stil: FoodyStil, fokus: Boolean): FeldAussehen {
    val f = FoodyTheme.farben
    return when (stil) {
        FoodyStil.ANIME -> {
            val form = RoundedCornerShape(14.dp)
            FeldAussehen(
                Modifier.background(Color.White, form).border(if (fokus) 3.dp else 2.5.dp, f.text, form),
                14.dp, Anime.text(16f), Anime.text(11.5f, FontWeight.ExtraBold),
            )
        }
        FoodyStil.EDO -> {
            val linie = if (fokus) edoShu() else f.text
            FeldAussehen(unterstrich(linie, 1.5.dp), 4.dp, Edo.text(16.5f), Edo.kopf(11.5f))
        }
        FoodyStil.BAYRISCH -> {
            val form = RoundedCornerShape(10.dp)
            FeldAussehen(
                Modifier.background(Color.White, form).border(2.dp, if (fokus) bayrischBlau() else f.text, form),
                12.dp, Bayrisch.text(16f), Bayrisch.text(13f, FontWeight.Bold),
            )
        }
        FoodyStil.GRAUEN -> {
            val glut = FoodyTheme.stil.zier
            FeldAussehen(
                Modifier.border(1.dp, if (fokus) glut else glut.copy(alpha = 0.35f), RectangleShape),
                10.dp, Grauen.text(17f), Grauen.maschine(11.5f),
            )
        }
        else -> {
            val form: Shape = RectangleShape
            FeldAussehen(
                Modifier.background(Color.White.copy(alpha = 0.03f), form)
                    .border(1.dp, if (fokus) kreuzerAmber() else f.nebentext.copy(alpha = 0.4f), form),
                12.dp, Kreuzer.titel(15.5f, FontWeight.Medium, 0f), Kreuzer.mono(11.5f, 0.08f),
            )
        }
    }
}

private fun unterstrich(farbe: Color, staerke: Dp) = Modifier.drawBehind {
    val y = size.height - staerke.toPx() / 2
    drawLine(farbe, Offset(0f, y), Offset(size.width, y), staerke.toPx())
}

/**
 * Überschrift eines Abschnitts in Szenen R–V (für TalkBack eine Überschrift): Zinnober (Edo), Blau (Bayrisch), Glut
 * (Grauen), Holo-Blau in Monospace-Versalien (Kreuzer), Tinte in Versalien (Anime).
 */
@Composable
fun StilAbschnitt(titel: String, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val s = FoodyTheme.stil
    val (schrift, farbe) = when (s.stil) {
        FoodyStil.ANIME -> Anime.text(15f, FontWeight.ExtraBold) to FoodyTheme.farben.text
        FoodyStil.EDO -> Edo.titel(17f) to edoShu()
        FoodyStil.BAYRISCH -> Bayrisch.titel(18f) to bayrischBlau()
        FoodyStil.GRAUEN -> Grauen.titel(18f) to s.zier
        else -> Kreuzer.mono(12f, 0.08f) to s.zier
    }
    val text = if (s.stil == FoodyStil.ANIME || s.stil == FoodyStil.KREUZER) titel.uppercase() else titel
    Text(
        text,
        modifier.semantics {
            heading()
            if (beschreibung != null) contentDescription = beschreibung
        },
        style = schrift,
        color = farbe,
    )
}

/**
 * Karte für zusammengehörige Felder in Szenen R–V: weiße Karte mit Tintenrand und Versatzschatten (Anime), Papier mit
 * feinem Rand (Edo), weiße Karte mit Rand (Bayrisch), dunkle Tafel mit Linie (Grauen); der Kreuzer hat keine Karte.
 */
@Composable
fun StilKarte(modifier: Modifier = Modifier, inhalt: @Composable ColumnScope.() -> Unit) {
    val s = FoodyTheme.stil
    val f = FoodyTheme.farben
    val rahmen = when (s.stil) {
        FoodyStil.ANIME -> {
            val form = RoundedCornerShape(20.dp)
            Modifier.animeSchatten(form, f.text, 3.dp).background(Color.White, form).border(2.5.dp, f.text, form).padding(14.dp)
        }
        FoodyStil.EDO -> Modifier.background(Edo.papier).border(1.dp, Edo.papierRand).padding(horizontal = 14.dp, vertical = 12.dp)
        FoodyStil.BAYRISCH -> {
            val form = Bayrisch.kartenForm
            Modifier.background(Color.White, form).border(2.dp, f.text, form).padding(horizontal = 14.dp, vertical = 12.dp)
        }
        FoodyStil.GRAUEN -> Modifier.background(Color.White.copy(alpha = 0.03f)).border(1.dp, s.zier.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
        else -> Modifier
    }
    Column(modifier.fillMaxWidth().then(rahmen), verticalArrangement = Arrangement.spacedBy(10.dp), content = inhalt)
}
