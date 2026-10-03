package de.foody.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Darstellung: dem System folgen oder fest hell/dunkel (Einstellungen → Darstellung). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Glas-Optik: Karten sind durchscheinend und lassen den Farbverlauf des Hintergrunds durch; statt eines
 * Schattens grenzt eine feine Lichtkante (oben links hell, unten rechts schwach) sie ab.
 * Die Flächen bleiben deckend genug, dass Text die WCAG-Kontraste der Grundfarben hält.
 */
object FoodyGlass {
    @Composable @ReadOnlyComposable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val fill: Color
        @Composable @ReadOnlyComposable
        get() = if (isDark()) Color(0xFF1B2521).copy(alpha = 0.66f) else Color.White.copy(alpha = 0.70f)

    val border: BorderStroke
        @Composable @ReadOnlyComposable
        get() = BorderStroke(
            1.dp,
            if (isDark()) {
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)))
            } else {
                Brush.linearGradient(listOf(Color.White, Color(0xFFC9CECB).copy(alpha = 0.55f)))
            },
        )
}

/**
 * Hintergrund der ganzen App: Grundton mit weichem Minz-Schimmer oben links und Korallen-Schimmer unten rechts.
 * Zwei Radialverläufe statt eines Bildes – skaliert auf jede Fenstergröße und kostet keinen Speicher.
 */
@Composable
fun FoodyBackground(content: @Composable () -> Unit) {
    val dark = FoodyGlass.isDark()
    val base = MaterialTheme.colorScheme.background
    val mint = if (dark) Color(0xFF0F5A45).copy(alpha = 0.55f) else Color(0xFFBFEBDD).copy(alpha = 0.75f)
    val coral = if (dark) Color(0xFF5A2219).copy(alpha = 0.40f) else Color(0xFFFFD9CF).copy(alpha = 0.55f)
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(base)
            drawRect(Brush.radialGradient(listOf(mint, Color.Transparent), center = Offset.Zero, radius = size.maxDimension * 0.85f))
            drawRect(
                Brush.radialGradient(
                    listOf(coral, Color.Transparent),
                    center = Offset(size.width, size.height * 0.9f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
        },
    ) { content() }
}
