package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.token.UnifrakturFamilie

/*
 * Schmuck des Grauens (Plan „Zwei Gesichter“, Schritt 3): Trennlinien mit Auge statt Kästen, das Älteste Zeichen als
 * Wasserzeichen mit einer Tentakel-Ranke am Rand, Fraktur-Initialen für große Titel. Alles nur Zeichnung ohne
 * Semantik; TalkBack hört den Inhalt, nicht den Schmuck.
 */

/** Höhe, die ein [trennerMitAuge] braucht (Abstand des Inhalts darüber oder darunter). */
val TRENNER_HOEHE = 22.dp

/**
 * Eine Trennlinie mit einem Auge in der Mitte auf Höhe [y]: zwei Linien, die zur Mitte hin kräftiger werden, je ein
 * Punkt davor und das Auge (Federzeichnung) dazwischen.
 */
fun DrawScope.trennerMitAuge(farbe: Color, y: Float = size.height / 2f) {
    val mitte = size.width / 2f
    val auge = 18.dp.toPx()
    val luecke = auge / 2f + 8.dp.toPx()
    val strich = 1.dp.toPx()
    val blass = farbe.copy(alpha = 0f)
    drawLine(Brush.horizontalGradient(listOf(blass, farbe), 0f, mitte - luecke), Offset(0f, y), Offset(mitte - luecke, y), strich)
    drawLine(Brush.horizontalGradient(listOf(farbe, blass), mitte + luecke, size.width), Offset(mitte + luecke, y), Offset(size.width, y), strich)
    drawCircle(farbe, 1.6.dp.toPx(), Offset(mitte - luecke + 4.dp.toPx(), y))
    drawCircle(farbe, 1.6.dp.toPx(), Offset(mitte + luecke - 4.dp.toPx(), y))
    translate(mitte - auge / 2f, y - auge / 2f) {
        zeichneIn(auge) { zeichne(GrauenZeichen.AUGE, farbe) }
    }
}

/** Zeichnet [block] in ein Quadrat der Kantenlänge [kante] ab der aktuellen Lage. */
private inline fun DrawScope.zeichneIn(kante: Float, block: DrawScope.() -> Unit) {
    val alt = drawContext.size
    drawContext.size = Size(kante, kante)
    block()
    drawContext.size = alt
}

/** [trennerMitAuge] als eigene Zeile, so breit wie möglich; ohne Semantik. */
@Composable
fun GrauenTrenner(farbe: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(TRENNER_HOEHE).clearAndSetSemantics { }) { trennerMitAuge(farbe) }
}

/**
 * Wasserzeichen des Grauens auf dem Grund: das Älteste Zeichen groß und blass unten rechts, links eine Tentakel-Ranke,
 * die vom unteren Rand aufsteigt. So blass ([farbe] mit wenigen Prozent Deckkraft), dass Text darüber seinen Kontrast
 * behält.
 */
fun DrawScope.grauenWasserzeichen(farbe: Color) {
    val zeichen = farbe.copy(alpha = 0.06f)
    val kante = size.width * 0.62f
    translate(size.width - kante * 0.78f, size.height - kante * 0.95f) {
        rotate(-12f, pivot = Offset(kante / 2f, kante / 2f)) {
            zeichneIn(kante) { zeichne(GrauenZeichen.AELTESTES, zeichen) }
        }
    }
    tentakel(farbe.copy(alpha = 0.08f))
}

/** Eine Ranke am linken Rand: ein sich verjüngender Arm mit Saugnäpfen, der sich oben einrollt. */
private fun DrawScope.tentakel(farbe: Color) {
    val h = size.height
    val b = 18.dp.toPx()
    val pfad = Path().apply {
        moveTo(-b * 0.4f, h)
        cubicTo(b * 1.6f, h * 0.86f, -b * 0.2f, h * 0.74f, b * 1.1f, h * 0.64f)
        cubicTo(b * 2.2f, h * 0.56f, b * 2.6f, h * 0.5f, b * 1.6f, h * 0.47f)
        cubicTo(b * 0.9f, h * 0.45f, b * 0.8f, h * 0.5f, b * 1.25f, h * 0.51f)
    }
    // Der Arm wird nach oben dünner: dünn über die ganze Länge, darunter kräftiger
    listOf(0.4f to 2.5f, 0.6f to 4.5f, 0.78f to 7f).forEach { (oben, breite) ->
        clipRect(top = h * oben) { drawPath(pfad, farbe, style = Stroke(breite.dp.toPx(), cap = StrokeCap.Round)) }
    }
    // Saugnäpfe entlang der Innenseite
    listOf(0.95f to 0.55f, 0.88f to 1.15f, 0.8f to 0.45f, 0.72f to 0.75f, 0.66f to 1.35f).forEach { (yAnteil, xAnteil) ->
        drawCircle(farbe, 2.2.dp.toPx(), Offset(b * xAnteil + 4.dp.toPx(), h * yAnteil), style = Stroke(1.dp.toPx()))
    }
}

/**
 * [text] mit einer Fraktur-Initiale: der erste Buchstabe in UnifrakturCook, [faktor]-mal so groß wie [groesse] (sp)
 * und in [farbe]; der Rest bleibt in der Schrift des Textfelds. Ein einziger Text, TalkBack liest das ganze Wort.
 */
fun grauenFraktur(text: String, farbe: Color, groesse: Float, faktor: Float = 1.35f): AnnotatedString = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString
    withStyle(SpanStyle(fontFamily = UnifrakturFamilie, fontSize = (groesse * faktor).sp, color = farbe)) { append(text.take(1)) }
    append(text.drop(1))
}
