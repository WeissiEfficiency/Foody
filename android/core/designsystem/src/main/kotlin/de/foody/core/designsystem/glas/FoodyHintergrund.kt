package de.foody.core.designsystem.glas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.Hintergrundmuster
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.random.Random

/** Der Haze-Zustand des nächsten [FoodyHintergrund]; `null` außerhalb – Glas zeigt dann nur Tönung. */
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Hintergrund der V3: Grund mit zwei weichen Radialverläufen in Tönen der Farbwelt oder des Stils (oben links, unten
 * rechts), bei Stilen in Immersiv dazu ein Muster ([Hintergrundmuster]). Er ist die Quelle, die [GlasFlaeche] unscharf
 * zeichnet; Inhalte liegen darüber.
 */
@Composable
fun FoodyHintergrund(modifier: Modifier = Modifier, inhalt: @Composable BoxScope.() -> Unit) {
    val hazeState = rememberHazeState()
    val grund = FoodyTheme.farben.grund
    val verlauf = FoodyTheme.werte.verlauf
    val stil = FoodyTheme.stil
    val linie = FoodyTheme.farben.balkenSpur
    val text = FoodyTheme.farben.text
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .drawBehind {
                    drawRect(grund)
                    drawRect(
                        Brush.radialGradient(
                            listOf(verlauf.oben, Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension * 0.85f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(verlauf.unten, Color.Transparent),
                            center = Offset(size.width, size.height),
                            radius = size.maxDimension * 0.7f,
                        ),
                    )
                    when (stil.muster) {
                        Hintergrundmuster.KEINS -> Unit
                        Hintergrundmuster.PUNKTE -> punkte(text.copy(alpha = 0.10f))
                        Hintergrundmuster.FASERN -> fasern(linie.copy(alpha = 0.55f))
                        Hintergrundmuster.RAUTEN -> rautenband(stil.zier)
                        Hintergrundmuster.STERNE -> sterne(stil.zier)
                    }
                },
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) { inhalt() }
    }
}

/** Rasterpunkte wie im Manga-Druck (Anime). */
private fun DrawScope.punkte(farbe: Color) {
    val abstand = 14f * density
    val radius = 1.3f * density
    var y = abstand / 2
    while (y < size.height) {
        var x = abstand / 2
        while (x < size.width) {
            drawCircle(farbe, radius, Offset(x, y))
            x += abstand
        }
        y += abstand
    }
}

/** Feine waagrechte Fasern wie im Washi-Papier (Edo). */
private fun DrawScope.fasern(farbe: Color) {
    val zufall = Random(7)
    var y = 0f
    while (y < size.height) {
        y += (18f + zufall.nextFloat() * 22f) * density
        val start = zufall.nextFloat() * size.width * 0.6f
        val laenge = (0.2f + zufall.nextFloat() * 0.4f) * size.width
        drawLine(farbe, Offset(start, y), Offset(start + laenge, y), strokeWidth = 0.8f * density)
    }
}

/** Rautenband am unteren Rand (Bayrisch), über der Systemleiste; Text liegt dort nie. */
private fun DrawScope.rautenband(farbe: Color) {
    val breite = 20f * density
    val hoehe = 14f * density
    val oben = size.height - hoehe * 2
    drawRect(Color.White, Offset(0f, oben), Size(size.width, hoehe * 2))
    for (reihe in 0..1) {
        val y = oben + reihe * hoehe
        var x = if (reihe == 0) 0f else -breite / 2
        while (x < size.width) {
            val raute = Path().apply {
                moveTo(x + breite / 2, y)
                lineTo(x + breite, y + hoehe / 2)
                lineTo(x + breite / 2, y + hoehe)
                lineTo(x, y + hoehe / 2)
                close()
            }
            drawPath(raute, farbe)
            x += breite
        }
    }
}

/** Sternenfeld mit festem Muster (Grauen, Sternenkreuzer), einige Sterne im Ton des Stils. */
private fun DrawScope.sterne(zier: Color) {
    val zufall = Random(42)
    val anzahl = (size.width * size.height / (2600f * density * density)).toInt().coerceIn(40, 260)
    repeat(anzahl) { i ->
        val farbe = if (i % 7 == 0) zier else Color.White
        drawCircle(
            farbe.copy(alpha = 0.25f + zufall.nextFloat() * 0.45f),
            radius = (0.5f + zufall.nextFloat() * 0.8f) * density,
            center = Offset(zufall.nextFloat() * size.width, zufall.nextFloat() * size.height),
        )
    }
}
