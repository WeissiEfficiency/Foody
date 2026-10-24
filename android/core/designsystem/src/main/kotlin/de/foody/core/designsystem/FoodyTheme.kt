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
import androidx.compose.ui.platform.LocalContext
import de.foody.core.designsystem.token.FoodyFarben
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.FoodyTypografie

private val LocalFarben = staticCompositionLocalOf { FoodyFarben.Hell }
private val LocalDunkel = staticCompositionLocalOf { false }
private val LocalUnschaerfe = staticCompositionLocalOf { true }

/**
 * Theme der V3: Ofenwarm-Farben, Fraunces/Figtree, eigene Ecken; bewusst ohne Dynamic Color.
 * [unschaerfe] = false zeigt Glas nur getönt – automatisch, wenn Animationen systemweit aus sind.
 */
@Composable
fun FoodyTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    unschaerfe: Boolean = unschaerfeErlaubt(),
    inhalt: @Composable () -> Unit,
) {
    val farben = if (dunkel) FoodyFarben.Dunkel else FoodyFarben.Hell
    CompositionLocalProvider(
        LocalFarben provides farben,
        LocalDunkel provides dunkel,
        LocalUnschaerfe provides unschaerfe,
    ) {
        MaterialTheme(
            colorScheme = farbschema(farben, dunkel),
            typography = FoodyTypografie,
            shapes = Shapes(small = FoodyFormen.klein, medium = FoodyFormen.mittel, large = FoodyFormen.gross),
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

private fun farbschema(f: FoodyFarben, dunkel: Boolean): ColorScheme =
    if (dunkel) {
        darkColorScheme(
            primary = f.primaer, onPrimary = f.aufPrimaer,
            primaryContainer = f.primaerBehaelter, onPrimaryContainer = f.aufPrimaerBehaelter,
            secondary = f.oliv, tertiary = f.safran,
            background = f.grund, onBackground = f.text,
            surface = f.grund, onSurface = f.text,
            surfaceVariant = f.balkenSpur, onSurfaceVariant = f.nebentext,
            outline = f.nebentext,
        )
    } else {
        lightColorScheme(
            primary = f.primaer, onPrimary = f.aufPrimaer,
            primaryContainer = f.primaerBehaelter, onPrimaryContainer = f.aufPrimaerBehaelter,
            secondary = f.oliv, tertiary = f.safran,
            background = f.grund, onBackground = f.text,
            surface = f.grund, onSurface = f.text,
            surfaceVariant = f.balkenSpur, onSurfaceVariant = f.nebentext,
            outline = f.nebentext,
        )
    }
