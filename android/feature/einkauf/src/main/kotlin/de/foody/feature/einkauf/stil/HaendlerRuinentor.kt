package de.foody.feature.einkauf.stil

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.svgPfad
import kotlin.math.sin

/**
 * Der Händler im Ruinentor (Runde 2, Variante B): Er steht als Schattenriss im Torbogen, dahinter goldenes Licht, davor
 * Nebel und schwebende Funken, an den Rändern tote Bäume. Darunter der Spruch wie ein Untertitel im Spiel – der Name
 * klein in Gold, der Satz mittig. Die Zeichnung ist für TalkBack stumm, der Spruch nicht.
 *
 * Bewegt (wenn Animationen erlaubt): das Licht im Tor atmet, die Laterne flackert, die Funken steigen langsam.
 */
@Composable
internal fun HaendlerRuinentor(offen: Int) {
    val f = FoodyTheme.farben
    val bewegt = animationenErlaubt()
    val zeit = rememberInfiniteTransition(label = "Ruinentor")
    val licht by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "Licht")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val flackern by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "Laterne")
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    val steigen by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "Funken")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val pfade = remember { TorPfade() }
    Column(Modifier.fillMaxWidth().border(1.dp, f.balkenSpur).background(NACHT)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Höhe aus der Breite (Seitenverhältnis des Entwurfs), auf dem Tablet gedeckelt; die Szene steht dann mittig
            val hoehe = min(maxWidth * (HOEHE / BREITE), 230.dp)
            Canvas(Modifier.fillMaxWidth().height(hoehe).clipToBounds().clearAndSetSemantics { }) {
                drawRect(Brush.verticalGradient(listOf(NACHT, Color(0xFF1C1914))))
                val s = size.height / HOEHE
                translate((size.width - BREITE * s) / 2f, 0f) {
                    scale(s, Offset.Zero) { tor(pfade, licht, flackern, steigen) }
                }
                // Nebel über die ganze Breite
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x30B4AA92)), size.height * 0.75f, size.height),
                    Offset(0f, size.height * 0.75f), Size(size.width, size.height * 0.25f),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("WANDERNDER HÄNDLER", style = Sage.titel(10.5f, 0.22f), color = FoodyTheme.stil.zier)
            Text(
                "Was führt Euch her? ${haendlerSpruch(offen)}",
                Modifier.padding(top = 4.dp), style = Sage.text(16f), color = sageHell(), textAlign = TextAlign.Center,
            )
        }
    }
}

private const val BREITE = 360f
private const val HOEHE = 190f
private val NACHT = Color(0xFF0E0D0B)
private val GOLD = Color(0xFFC9A65A)
private val GOLD_HELL = Color(0xFFF3D58A)
private val STEIN = Color(0xFF2B261E)
private val SCHATTEN = Color(0xFF080706)

/** Schwebende Funken: Lage im Entwurf und Versatz im Takt, damit sie nicht gleichzeitig steigen. */
private val FUNKEN = listOf(
    Offset(120f, 80f) to 0f, Offset(240f, 100f) to 0.3f, Offset(70f, 140f) to 0.55f,
    Offset(290f, 70f) to 0.15f, Offset(150f, 30f) to 0.7f, Offset(210f, 150f) to 0.85f,
)

/**
 * Das Tor in Entwurfseinheiten (360 × 190): [licht] lässt den Schein im Bogen atmen, [flackern] die Laterne, [steigen]
 * hebt die Funken um bis zu 30 Einheiten und lässt sie oben verblassen.
 */
private fun DrawScope.tor(p: TorPfade, licht: Float, flackern: Float, steigen: Float) {
    p.baeume.forEach { drawPath(it, Color(0xFF2A251D), style = Stroke(3f, cap = StrokeCap.Round)) }
    clipPath(p.innen) {
        drawRect(
            Brush.radialGradient(
                listOf(GOLD_HELL.copy(alpha = 0.7f + 0.2f * licht), GOLD.copy(alpha = 0.3f + 0.1f * licht), Color.Transparent),
                Offset(180f, 140f), 80f + 12f * licht,
            ),
        )
        p.strahlen.forEach { drawPath(it, GOLD_HELL.copy(alpha = 0.08f + 0.06f * licht)) }
    }
    drawPath(p.bogen, STEIN)
    drawPath(p.bogen, Color(0xFF443C2F), style = Stroke(1.2f))
    FUGEN.forEach { (a, b) -> drawLine(Color(0xFF1A1712), a, b, 1f) }
    drawPath(p.bruch, NACHT)
    drawRect(Color(0xFF221E18), Offset(96f, 176f), Size(168f, 6f))
    drawRect(Color(0xFF1A1712), Offset(86f, 182f), Size(188f, 8f))
    // Händler als Schattenriss, Stab mit Laterne, glimmende Augen
    drawPath(p.haendler, SCHATTEN)
    drawLine(SCHATTEN, Offset(202f, 102f), Offset(202f, 178f), 2.5f)
    drawLine(SCHATTEN, Offset(202f, 104f), Offset(210f, 108f), 1.5f)
    drawRect(Sage.feuer.copy(alpha = 0.8f + 0.2f * flackern), Offset(207f, 110f), Size(6f, 8f))
    drawCircle(Sage.feuer.copy(alpha = 0.18f + 0.12f * flackern), 11f + 2f * flackern, Offset(210f, 114f))
    drawCircle(GOLD_HELL, 1.2f, Offset(177.5f, 106f))
    drawCircle(GOLD_HELL, 1.2f, Offset(182.5f, 106f))
    FUNKEN.forEach { (o, versatz) ->
        val t = (steigen + versatz) % 1f
        val y = o.y - 30f * t
        val x = o.x + 4f * sin(t * 6.28f)
        val a = 1f - t * t
        drawCircle(GOLD_HELL.copy(alpha = 0.25f * a), 4f, Offset(x, y))
        drawCircle(GOLD_HELL.copy(alpha = a), 1.2f, Offset(x, y))
    }
}

private val FUGEN = listOf(
    Offset(108f, 110f) to Offset(134f, 110f),
    Offset(108f, 150f) to Offset(134f, 150f),
    Offset(226f, 130f) to Offset(252f, 130f),
)

private class TorPfade {
    val innen = svgPfad("M134 190V86C134 56 226 56 226 86V190Z")
    val bogen = svgPfad("M108 190V84C108 24 252 24 252 84V190H226V86C226 56 134 56 134 86V190Z")
    val bruch = svgPfad("M172 36L188 36L186 28L174 30Z")
    val haendler = svgPfad("M164 178C165 150 168 130 172 120C170 110 173 98 180 96C187 98 190 110 188 120C192 130 195 150 196 178Z")
    val strahlen = listOf("M160 60L150 190H170Z", "M196 60L192 190H214Z", "M178 60L176 190H186Z").map { svgPfad(it) }
    val baeume = listOf(
        "M24 190V110L14 86M24 122L36 98L44 90M24 140L10 126",
        "M336 190V100L346 78M336 118L322 94M336 140L350 128",
    ).map { svgPfad(it) }
}
