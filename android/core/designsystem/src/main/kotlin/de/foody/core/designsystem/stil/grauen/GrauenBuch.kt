package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import de.foody.core.designsystem.FoodyTheme
import kotlin.math.abs

/**
 * Das Buch des Kosmischen Grauens (Runde 2 G2): Rezept und Kochmodus zeigen ihre Seiten als Blätter eines alten
 * Folianten. [GrauenSeite] zeichnet ein Blatt (dunkles Papier, Schatten am Bund links, gestapelte Seitenkanten rechts,
 * Kapitel oben, Seitenzahl unten), [umblaettern] lässt es im Pager wie ein Blatt um den Bund kippen statt zu gleiten.
 * Buchschmuck (Runde 2, Buch ausbauen): ein Lesebändchen im rechten Rand (an jedem Blatt an derselben Stelle, so liegt
 * es beim Umblättern scheinbar still), ein Eselsohr an Blättern mit Folgeseite, eine [GrauenInitiale] und die
 * [GrauenRandnotiz].
 */
private val PAPIER = listOf(Color(0xFF16201C), Color(0xFF101815))
private val KANTE = Color(0xFF2C3D35)

/** Das Bändchen in dunklem Weinrot; die Randnotiz in blassem Rosé (auf dem Papier über 7:1). */
private val BAENDCHEN = Color(0xFF7A2E44)
private val ROSE = Color(0xFFD9A3B4)

/**
 * Ein Blatt des Buchs mit [kapitel] oben (Überschrift, TalkBack liest es) und [seite] (z. B. „II“) unten; der Inhalt
 * steht dazwischen. Mit [eselsohr] ist die untere rechte Ecke umgeknickt: Es folgt noch eine Seite. Das Lesebändchen
 * hängt im rechten Rand, neben dem Text.
 */
@Composable
fun GrauenSeite(
    kapitel: String?,
    seite: String?,
    modifier: Modifier = Modifier,
    eselsohr: Boolean = false,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
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
            // Das Blatt, beim Eselsohr ohne die umgeknickte Ecke (darunter sieht man die Kanten der nächsten Seiten)
            val ecke = if (eselsohr) 20.dp.toPx() else 0f
            val umriss = Path().apply {
                moveTo(0f, 0f)
                lineTo(blatt.width, 0f)
                lineTo(blatt.width, blatt.height - ecke)
                lineTo(blatt.width - ecke, blatt.height)
                lineTo(0f, blatt.height)
                close()
            }
            drawPath(umriss, Brush.verticalGradient(PAPIER))
            // Schatten am Bund links und ein Hauch Glut an der Kante
            val bund = 22.dp.toPx()
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, bund), size = Size(bund, blatt.height))
            drawPath(umriss, glut.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
            if (eselsohr) {
                // Die umgeknickte Ecke liegt als helleres Dreieck auf dem Blatt
                val ohr = Path().apply {
                    moveTo(blatt.width, blatt.height - ecke)
                    lineTo(blatt.width - ecke, blatt.height - ecke)
                    lineTo(blatt.width - ecke, blatt.height)
                    close()
                }
                drawPath(ohr, KANTE)
                drawPath(ohr, glut.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
            }
            lesebaendchen(blatt.width)
        }.padding(start = 26.dp, end = 24.dp, top = 18.dp, bottom = 16.dp),
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

/** Das Lesebändchen im rechten Rand des Blatts (Breite [blatt]): oben angesetzt, unten mit Kerbe. */
private fun DrawScope.lesebaendchen(blatt: Float) {
    val breite = 9.dp.toPx()
    val laenge = 60.dp.toPx()
    val kerbe = 5.dp.toPx()
    val x = blatt - 13.dp.toPx()
    val band = Path().apply {
        moveTo(x, 0f)
        lineTo(x + breite, 0f)
        lineTo(x + breite, laenge)
        lineTo(x + breite / 2, laenge - kerbe)
        lineTo(x, laenge)
        close()
    }
    drawPath(band, BAENDCHEN)
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(x + breite, 0f), Offset(x + breite, laenge), 1.dp.toPx())
}

/**
 * Ein Absatz mit Initiale: Der erste Buchstabe von [text] steht groß in der Glut in einem feinen Rahmen, der Rest
 * folgt in [stil]. Ein einziger Text – TalkBack und die Suche finden den ganzen Satz.
 */
@Composable
fun GrauenInitiale(text: String, stil: TextStyle, farbe: Color, modifier: Modifier = Modifier) {
    val glut = FoodyTheme.stil.zier
    var lage by remember { mutableStateOf<Rect?>(null) }
    val satz = buildAnnotatedString {
        withStyle(Grauen.name(30f).toSpanStyle().copy(color = glut, letterSpacing = 0.35.em)) { append(text.take(1)) }
        append(text.drop(1))
    }
    Text(
        satz,
        modifier.drawBehind {
            val r = lage ?: return@drawBehind
            val rand = 3.dp.toPx()
            val feld = Rect(r.left - rand, r.top, r.left + r.height * 0.8f + rand, r.bottom)
            drawRect(glut.copy(alpha = 0.08f), feld.topLeft, feld.size)
            drawRect(glut.copy(alpha = 0.6f), feld.topLeft, feld.size, style = Stroke(1.dp.toPx()))
        },
        style = stil, color = farbe,
        onTextLayout = { l -> lage = if (text.isEmpty()) null else l.getBoundingBox(0) },
    )
}

/** Eine Notiz am Rand in blassem Rosé, kursiv und leicht schräg wie von Hand nachgetragen. */
@Composable
fun GrauenRandnotiz(text: String, modifier: Modifier = Modifier) {
    Text(
        "✎ $text",
        modifier.padding(start = 18.dp, top = 6.dp).rotate(-1.5f).semantics { contentDescription = "Notiz: $text" },
        style = Grauen.text(15.5f, kursiv = true), color = ROSE,
    )
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
