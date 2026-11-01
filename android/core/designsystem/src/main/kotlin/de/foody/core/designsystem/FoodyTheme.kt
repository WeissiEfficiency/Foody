package de.foody.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FarbweltWerte
import de.foody.core.designsystem.token.FoodyFarben
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.FoodyTypografie

private val LocalFarben = staticCompositionLocalOf { FoodyFarben.Hell }
private val LocalFarbwelt = staticCompositionLocalOf { Farbwelt.Standard }
private val LocalDunkel = staticCompositionLocalOf { false }
private val LocalUnschaerfe = staticCompositionLocalOf { true }

/**
 * Theme der V3: wählbare [Farbwelt] (Standard Mango) mit Milchglas, Fraunces/Figtree, runde Ecken; bewusst ohne
 * Dynamic Color. [unschaerfe] = false zeigt Glas nur getönt – automatisch, wenn Animationen systemweit aus sind.
 */
@Composable
fun FoodyTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    farbwelt: Farbwelt = Farbwelt.Standard,
    unschaerfe: Boolean = unschaerfeErlaubt(),
    inhalt: @Composable () -> Unit,
) {
    val farben = farbwelt.werte(dunkel).farben
    CompositionLocalProvider(
        LocalFarben provides farben,
        LocalFarbwelt provides farbwelt,
        LocalDunkel provides dunkel,
        LocalUnschaerfe provides unschaerfe,
    ) {
        MaterialTheme(
            colorScheme = farbschema(farben, farbwelt.werte(!dunkel).farben, dunkel),
            typography = FoodyTypografie,
            // extraSmall: Material zeichnet damit die Eingabefelder; rund wie im Entwurf statt fast eckig
            shapes = Shapes(extraSmall = FoodyFormen.klein, small = FoodyFormen.klein, medium = FoodyFormen.mittel, large = FoodyFormen.gross),
            content = inhalt,
        )
    }
}

/** Zugriff auf die eigenen Werte des Themes. */
object FoodyTheme {
    val farben: FoodyFarben
        @Composable @ReadOnlyComposable
        get() = LocalFarben.current

    val dunkel: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalDunkel.current

    val farbwelt: Farbwelt
        @Composable @ReadOnlyComposable
        get() = LocalFarbwelt.current

    /** Verlauf, Glastönung und Platzhalter der aktuellen Farbwelt im aktuellen Modus. */
    val werte: FarbweltWerte
        @Composable @ReadOnlyComposable
        get() = LocalFarbwelt.current.werte(LocalDunkel.current)

    val unschaerfe: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalUnschaerfe.current
}

/** `false`, wenn Animationen systemweit abgeschaltet sind (Animator-Dauer 0). */
@Composable
fun animationenErlaubt(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (_: Exception) {
        true
    }
}

/** Unschärfe gehört zu den Effekten, die mit den Animationen entfallen. */
@Composable
fun unschaerfeErlaubt(): Boolean = animationenErlaubt()

/**
 * Material-Rollen aus den Foody-Farben. Die Behälterflächen (Menü, Dialog, Sheet) staffeln sich vom Grund zur
 * Balkenspur, damit nirgends die lila Baseline von Material 3 durchscheint.
 */
private fun farbschema(f: FoodyFarben, gegenstueck: FoodyFarben, dunkel: Boolean): ColorScheme {
    val stufe = { anteil: Float -> lerp(f.grund, f.balkenSpur, anteil) }
    return (if (dunkel) darkColorScheme() else lightColorScheme()).copy(
        primary = f.primaer, onPrimary = f.aufPrimaer,
        primaryContainer = f.primaerBehaelter, onPrimaryContainer = f.aufPrimaerBehaelter,
        inversePrimary = gegenstueck.primaer,
        secondary = f.oliv, tertiary = f.safran,
        background = f.grund, onBackground = f.text,
        surface = f.grund, onSurface = f.text,
        surfaceVariant = f.balkenSpur, onSurfaceVariant = f.nebentext,
        surfaceTint = f.primaer,
        inverseSurface = f.text, inverseOnSurface = f.grund,
        outline = f.nebentext, outlineVariant = f.balkenSpur,
        surfaceBright = if (dunkel) f.balkenSpur else f.grund,
        surfaceDim = if (dunkel) f.grund else f.balkenSpur,
        surfaceContainerLowest = lerp(f.grund, if (dunkel) Color.Black else Color.White, 0.4f),
        surfaceContainerLow = stufe(0.15f),
        surfaceContainer = stufe(0.3f),
        surfaceContainerHigh = stufe(0.45f),
        surfaceContainerHighest = stufe(0.6f),
    )
}
