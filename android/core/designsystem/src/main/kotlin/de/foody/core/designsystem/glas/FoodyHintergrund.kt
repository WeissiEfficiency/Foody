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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import de.foody.core.designsystem.FoodyTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Der Haze-Zustand des nächsten [FoodyHintergrund]; `null` außerhalb – Glas zeigt dann nur Tönung. */
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Hintergrund der V3: Grund mit zwei weichen Radialverläufen in Tönen der Farbwelt (oben links, unten rechts). Er ist die Quelle, die
 * [GlasFlaeche] unscharf zeichnet; Inhalte liegen darüber.
 */
@Composable
fun FoodyHintergrund(modifier: Modifier = Modifier, inhalt: @Composable BoxScope.() -> Unit) {
    val hazeState = rememberHazeState()
    val grund = FoodyTheme.farben.grund
    val verlauf = FoodyTheme.werte.verlauf
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
                },
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) { inhalt() }
    }
}
