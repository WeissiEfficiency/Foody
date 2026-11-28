package de.foody.feature.heute.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.sageHell
import java.util.Locale

/*
 * Erfassen der Düsteren Sage in Immersiv (Runde 2 S2): Foto, Scannen, Galerie und Nährwerttabelle liegen als
 * Gegenstände in vier Inventarfeldern nebeneinander, die Treffer der Suche stehen als Liste im Stil darunter – je Zeile
 * ein kleines Fach mit der Rune (Anfangsbuchstabe), Name, Werte und ein goldener Pfeil. Alles startet mit einem Tipp
 * wie in den anderen Stilen; TalkBack hört dieselben Namen.
 */

private val FACH_DUNKEL = Color(0xFF0B0A08)
private val FACH_HELL = Color(0xFF1A1712)

/**
 * Ein Gegenstand im Inventar: [name] für TalkBack, [kurz] steht unter dem Feld (in Sage kürzer, damit vier in eine Reihe
 * passen), [tag] als Test-Tag.
 */
internal class Werkzeug(
    val name: String,
    val kurz: String,
    val symbol: ImageVector,
    val onKlick: () -> Unit,
    val aktiv: Boolean = true,
    val laeuft: Boolean = false,
    val tag: String? = null,
)

/**
 * Vier Inventarfelder in einer Reihe; `null` lässt ein Feld leer (noch nicht bekannt bzw. nicht verfügbar), damit
 * kein Gegenstand springt, wenn die anderen dazukommen.
 */
@Composable
internal fun SageWerkzeuge(felder: List<Werkzeug?>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SageAbschnitt("Ausrüstung")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            felder.forEach { w ->
                val m = Modifier.weight(1f)
                if (w == null) LeeresFach(m) else Gegenstand(w, m)
            }
        }
    }
}

@Composable
private fun Gegenstand(w: Werkzeug, modifier: Modifier) {
    val glut = FoodyTheme.stil.zier
    val f = FoodyTheme.farben
    Column(
        modifier.then(w.tag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(enabled = w.aktiv, role = Role.Button, onClick = w.onKlick)
            .semantics(mergeDescendants = true) { contentDescription = w.name }
            .alpha(if (w.aktiv) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .shadow(8.dp, RectangleShape, ambientColor = glut, spotColor = glut)
                .background(Brush.radialGradient(listOf(glut.copy(alpha = 0.16f), FACH_DUNKEL)))
                .border(1.dp, glut.copy(alpha = 0.75f))
                .padding(3.dp)
                .eingelassen(),
            contentAlignment = Alignment.Center,
        ) {
            if (w.laeuft) {
                CircularProgressIndicator(Modifier.size(26.dp), color = glut, strokeWidth = 2.dp)
            } else {
                Icon(w.symbol, contentDescription = null, modifier = Modifier.size(30.dp), tint = glut)
            }
        }
        Text(
            w.kurz, Modifier.clearAndSetSemantics { }, style = Sage.titel(10.5f, 0.12f), color = f.text,
            textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

@Composable
private fun LeeresFach(modifier: Modifier) {
    Box(
        modifier.aspectRatio(1f).clearAndSetSemantics { }
            .background(Brush.radialGradient(listOf(FACH_HELL, FACH_DUNKEL)))
            .border(1.dp, FoodyTheme.farben.lichtkante)
            .padding(3.dp)
            .eingelassen(),
    )
}

/** Innere dunkle Kante: Das Fach wirkt eingelassen. */
private fun Modifier.eingelassen(): Modifier = drawWithContent {
    drawContent()
    drawRect(Color.Black.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
}

/** Überschrift eines Abschnitts in Gold zwischen zwei feinen Linien; TalkBack liest sie als Überschrift. */
@Composable
internal fun SageAbschnitt(text: String) {
    val glut = FoodyTheme.stil.zier
    val kante = FoodyTheme.farben.lichtkante
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Linie(kante, Modifier.weight(1f))
        Text(
            text, Modifier.semantics { heading() },
            style = Sage.titel(11f, 0.22f), color = glut, textAlign = TextAlign.Center,
        )
        Linie(kante, Modifier.weight(1f))
    }
}

@Composable
private fun Linie(farbe: Color, modifier: Modifier) {
    Box(
        modifier.heightIn(min = 1.dp).drawBehind {
            drawLine(farbe, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        },
    )
}

/**
 * Ein Treffer als Zeile im Inventar: kleines Fach mit der Rune (Anfangsbuchstabe), Name, [info] darunter und ein
 * goldener Pfeil; mindestens 56 dp hoch. Ein Knopf wie in den anderen Stilen (keine Auswahl).
 */
@Composable
internal fun SageTreffer(titel: String, info: String?, onWahl: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onWahl)
            .background(Brush.horizontalGradient(listOf(FACH_HELL, Color.Transparent)))
            .drawBehind {
                val dicke = 1.dp.toPx()
                drawLine(f.lichtkante, Offset(0f, size.height - dicke / 2), Offset(size.width, size.height - dicke / 2), dicke)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clearAndSetSemantics { }
                .background(Brush.radialGradient(listOf(glut.copy(alpha = 0.18f), FACH_DUNKEL)))
                .border(1.dp, glut.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(rune(titel), style = Sage.titel(17f, 0f), color = glut)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(titel, style = Sage.text(17f), color = sageHell(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            info?.let { Text(it, style = Sage.text(13.5f), color = f.nebentext, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Text("›", Modifier.clearAndSetSemantics { }, style = Sage.titel(20f, 0f), color = glut)
    }
}

/** Erster Buchstabe oder erste Ziffer des Namens, sonst eine Raute. */
internal fun rune(name: String): String = name.firstOrNull { it.isLetterOrDigit() }?.uppercase(Locale.GERMAN) ?: "◆"
