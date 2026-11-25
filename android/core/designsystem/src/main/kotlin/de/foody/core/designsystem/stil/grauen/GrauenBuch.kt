package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.foody.core.designsystem.FoodyTheme
import kotlin.math.abs

/**
 * Das Buch des Kosmischen Grauens (Runde 2 G2): Rezept und Kochmodus zeigen ihre Seiten als Blätter eines alten
 * Folianten. [GrauenSeite] zeichnet ein Blatt (dunkles Papier, Schatten am Bund links, gestapelte Seitenkanten rechts,
 * Kapitel oben, Seitenzahl unten), [umblaettern] lässt es im Pager wie ein Blatt um den Bund kippen statt zu gleiten.
 */
private val PAPIER = listOf(Color(0xFF16201C), Color(0xFF101815))
private val KANTE = Color(0xFF2C3D35)

/**
 * Ein Blatt des Buchs mit [kapitel] oben (Überschrift, TalkBack liest es) und [seite] (z. B. „II“) unten; der Inhalt
 * steht dazwischen.
 */
@Composable
fun GrauenSeite(kapitel: String?, seite: String?, modifier: Modifier = Modifier, inhalt: @Composable ColumnScope.() -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Column(
        modifier.fillMaxWidth().drawBehind {
            val kanten = 4
            val abstand = 2.dp.toPx()
            // Gestapelte Seiten rechts und unten, dann das Blatt selbst
            for (i in kanten downTo 1) {
                val lage = Offset(i * abstand, i * abstand)
                drawRect(KANTE.copy(alpha = 0.25f + 0.12f * (kanten - i)), lage, Size(size.width - kanten * abstand, size.height - kanten * abstand))
            }
            val blatt = Size(size.width - kanten * abstand, size.height - kanten * abstand)
            drawRect(Brush.verticalGradient(PAPIER), size = blatt)
            // Schatten am Bund links und ein Hauch Glut an der Kante
            val bund = 22.dp.toPx()
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, bund), size = Size(bund, blatt.height))
            drawRect(glut.copy(alpha = 0.35f), size = blatt, style = Stroke(1.dp.toPx()))
        }.padding(start = 26.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        kapitel?.let {
            Text(
                it, Modifier.fillMaxWidth().semantics { heading() },
                style = Grauen.titel(20f, 0.06f), color = f.primaer, textAlign = TextAlign.Center,
            )
        }
        inhalt()
        seite?.let {
            Text(
                "— $it —", Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { },
                style = Grauen.titel(13f, 0.1f), color = f.nebentext, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Seite [seite] des [pager]s kippt beim Blättern wie ein Blatt um den Bund (linke Kante), statt seitlich zu gleiten:
 * Das gehende Blatt dreht sich bis zur Kante weg und liegt dabei oben, das kommende liegt still darunter und wird
 * heller. Ohne Bewegung (Offset 0) wirkt es nicht.
 */
fun Modifier.umblaettern(pager: PagerState, seite: Int): Modifier {
    val versatz = (pager.currentPage - seite) + pager.currentPageOffsetFraction
    return zIndex(if (versatz >= 0f) 1f else 0f).graphicsLayer {
        if (versatz == 0f) return@graphicsLayer
        // Das Blatt bleibt am Ort, statt mit dem Pager zu gleiten
        translationX = versatz * size.width
        if (versatz > 0f) {
            // Gehendes Blatt: kippt um den Bund, ab der Hälfte unsichtbar
            transformOrigin = TransformOrigin(0f, 0.5f)
            rotationY = -100f * versatz.coerceAtMost(1f)
            cameraDistance = 14f * density
        }
        alpha = blattDeckkraft(versatz)
    }
}

/**
 * Deckkraft eines Blatts bei [versatz] (Seiten vom aktuellen Blatt entfernt, mit Bruchteil beim Wischen): Das gehende
 * Blatt ist bis kurz vor der Kante sichtbar, das kommende taucht aus dem Schatten auf. Ein Blatt, das eine ganze Seite
 * oder mehr entfernt liegt, ist unsichtbar; sonst schiene die vorbereitete Nachbarseite unter einem kürzeren Blatt durch.
 */
internal fun blattDeckkraft(versatz: Float): Float = when {
    versatz >= 0f -> if (versatz < 0.9f) 1f else 0f
    versatz <= -1f -> 0f
    else -> 1f - 0.5f * abs(versatz)
}
