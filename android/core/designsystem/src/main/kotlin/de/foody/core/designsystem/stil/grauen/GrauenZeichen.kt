package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gezeichnete Zeichen des Grauens (Plan „Zwei Gesichter“, Schritt 2): Bedienung als Feder und Tinte statt Kästen.
 * Jedes Zeichen ist auf 24 × 24 gezeichnet und wird auf die Größe gestreckt.
 */
enum class GrauenZeichen {
    /** Pfeil mit Schwung zurück. */
    ZURUECK,

    /** Derselbe Pfeil nach vorn. */
    WEITER,

    /** „∴“: drei Punkte, öffnet ein Menü. */
    MENUE,

    /** Lesebändchen, leer: merken. */
    BAND,

    /** Lesebändchen, gefüllt: gemerkt. */
    BAND_VOLL,

    /** Kerze: kochen, das Ritual. */
    KERZE,

    /** Schriftrolle: Liste. */
    ROLLE,

    /** Feder: notieren. */
    FEDER,

    /** Auge: sehen, füllen. */
    AUGE,

    /** Das „Älteste Zeichen“: Stamm mit Ästen im Kreis, Siegel ohne eigenes Zeichen. */
    AELTESTES,
}

/** Ein [zeichen] in [farbe], [groesse] groß; ohne Semantik (der Knopf darum trägt die Beschreibung). */
@Composable
fun GrauenSymbol(zeichen: GrauenZeichen, farbe: Color, modifier: Modifier = Modifier, groesse: Dp = 22.dp) {
    Canvas(modifier.size(groesse).clearAndSetSemantics { }) { zeichne(zeichen, farbe) }
}

/** Zeichnet [zeichen] in die ganze Fläche (Vorlage 24 × 24). */
fun DrawScope.zeichne(zeichen: GrauenZeichen, farbe: Color) {
    val k = size.minDimension / 24f
    val strich = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    scale(k, k, pivot = Offset.Zero) {
        fun linie(p: Path) = drawPath(p, farbe, style = strich)
        when (zeichen) {
            GrauenZeichen.ZURUECK, GrauenZeichen.WEITER -> {
                val spiegeln = zeichen == GrauenZeichen.WEITER
                scale(if (spiegeln) -1f else 1f, 1f, pivot = Offset(12f, 12f)) {
                    linie(Path().apply { moveTo(13f, 5f); lineTo(6f, 12f); lineTo(13f, 19f) })
                    linie(
                        Path().apply {
                            moveTo(6f, 12f)
                            lineTo(16f, 12f)
                            cubicTo(20f, 12f, 21f, 15f, 19.5f, 16.5f)
                            cubicTo(18f, 18f, 15.5f, 17f, 16.5f, 15f)
                        },
                    )
                }
            }
            GrauenZeichen.MENUE -> listOf(Offset(12f, 6.5f), Offset(6.5f, 17f), Offset(17.5f, 17f)).forEach { drawCircle(farbe, 2.1f, it) }
            GrauenZeichen.BAND, GrauenZeichen.BAND_VOLL -> {
                val band = Path().apply { moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 21f); lineTo(12f, 16.5f); lineTo(7f, 21f); close() }
                if (zeichen == GrauenZeichen.BAND_VOLL) drawPath(band, farbe)
                linie(band)
            }
            GrauenZeichen.KERZE -> {
                linie(Path().apply { moveTo(9f, 21f); lineTo(9f, 10f); lineTo(15f, 10f); lineTo(15f, 21f) })
                linie(Path().apply { moveTo(6.5f, 21f); lineTo(17.5f, 21f) })
                linie(Path().apply { moveTo(12f, 10f); lineTo(12f, 8.3f) })
                val flamme = Path().apply {
                    moveTo(12f, 7.8f)
                    cubicTo(10.2f, 6.2f, 10.8f, 4f, 12f, 2.3f)
                    cubicTo(13.2f, 4f, 13.8f, 6.2f, 12f, 7.8f)
                    close()
                }
                drawPath(flamme, farbe)
            }
            GrauenZeichen.ROLLE -> {
                linie(Path().apply { moveTo(6f, 5f); lineTo(17f, 5f); cubicTo(19f, 5f, 19f, 8f, 17f, 8f); lineTo(8f, 8f) })
                linie(Path().apply { moveTo(6f, 5f); cubicTo(4f, 5f, 4f, 8f, 6f, 8f); lineTo(6f, 19f); cubicTo(6f, 21f, 8f, 21f, 9f, 20f) })
                linie(Path().apply { moveTo(8f, 8f); lineTo(8f, 19f); lineTo(18f, 19f); lineTo(18f, 8f) })
                linie(Path().apply { moveTo(10f, 12f); lineTo(16f, 12f); moveTo(10f, 15f); lineTo(15f, 15f) })
            }
            GrauenZeichen.FEDER -> {
                linie(Path().apply { moveTo(20f, 3f); cubicTo(13f, 4f, 8f, 9f, 6f, 18f) })
                linie(Path().apply { moveTo(20f, 3f); cubicTo(19f, 9f, 14f, 13f, 8f, 14f) })
                linie(Path().apply { moveTo(4f, 21f); lineTo(6f, 18f) })
            }
            GrauenZeichen.AUGE -> {
                linie(Path().apply { moveTo(2f, 12f); cubicTo(6f, 6f, 18f, 6f, 22f, 12f); cubicTo(18f, 18f, 6f, 18f, 2f, 12f); close() })
                drawCircle(farbe, 3f, Offset(12f, 12f), style = strich)
                drawLine(farbe, Offset(12f, 9.5f), Offset(12f, 14.5f), 1.7f, StrokeCap.Round)
            }
            GrauenZeichen.AELTESTES -> {
                drawCircle(farbe, 10f, Offset(12f, 12f), style = Stroke(1.2f))
                linie(Path().apply { moveTo(12f, 4f); lineTo(12f, 20f) })
                listOf(8f to 3.2f, 12f to 4.2f, 16f to 3.4f).forEach { (y, w) ->
                    linie(Path().apply { moveTo(12f - w, y - 2.4f); lineTo(12f, y); lineTo(12f + w, y - 2.4f) })
                }
            }
        }
    }
}

/** Wachs eines Siegels im Ominösen: dunkles Grün mit Licht oben links. */
private val WACHS = listOf(Color(0xFF3F6A56), Color(0xFF22392F), Color(0xFF101C16))

/** Wachs im Necronomicon: schwarz mit mattem Glanz; das Zeichen darauf in Messing. */
private val WACHS_SCHWARZ = listOf(Color(0xFF45403A), Color(0xFF1C1915), Color(0xFF080706))

/**
 * Ein Wachssiegel mit gewelltem Rand und [zeichen] in der Mitte, [groesse] groß: grünes Wachs in Ominös, schwarzes mit
 * Messing im Necronomicon. Nur Zeichnung: Klick und Beschreibung trägt der Knopf darum.
 */
@Composable
fun GrauenSiegel(zeichen: GrauenZeichen, modifier: Modifier = Modifier, groesse: Dp = 52.dp, farbe: Color = Color(0xFFDFE4D6)) {
    val buch = FoodyTheme.stil.grauenBuch
    val wachs = if (buch) WACHS_SCHWARZ else WACHS
    val zeichenFarbe = if (buch) Grauen.messing else farbe
    Box(modifier.size(groesse).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(groesse)) {
            val r = size.minDimension / 2f
            val mitte = center
            val zacken = 34
            val rand = Path().apply {
                for (i in 0 until zacken) {
                    val w = 2 * PI * i / zacken
                    val ri = if (i % 2 == 0) r else r * 0.93f
                    val p = Offset(mitte.x + (ri * cos(w)).toFloat(), mitte.y + (ri * sin(w)).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(rand, Brush.radialGradient(wachs, Offset(mitte.x - r * 0.3f, mitte.y - r * 0.35f), r * 1.3f))
            // Eingeprägter Ring
            drawCircle(lerp(wachs[2], Color.Black, 0.3f), r * 0.78f, mitte, style = Stroke(r * 0.06f))
            drawCircle(Color.White.copy(alpha = 0.08f), r * 0.84f, mitte, style = Stroke(r * 0.04f))
        }
        GrauenSymbol(zeichen, zeichenFarbe, groesse = groesse * 0.46f)
    }
}
