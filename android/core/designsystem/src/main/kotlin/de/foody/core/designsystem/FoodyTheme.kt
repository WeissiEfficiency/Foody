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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.StilWerte
import de.foody.core.designsystem.stil.milchglasWerte
import de.foody.core.designsystem.stil.stilWerte
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FarbweltWerte
import de.foody.core.designsystem.token.Flaechenart
import de.foody.core.designsystem.token.FoodyFarben
import de.foody.core.designsystem.token.FoodyFormen

private val LocalStil = staticCompositionLocalOf { milchglasWerte(Farbwelt.Standard, dunkel = false) }
private val LocalFarbwelt = staticCompositionLocalOf { Farbwelt.Standard }
private val LocalUnschaerfe = staticCompositionLocalOf { true }

/**
 * Theme der V3. Standard ist [FoodyStil.MILCHGLAS]: wählbare [Farbwelt] (Standard Mango) mit Milchglas, Fraunces/Figtree,
 * runde Ecken, hell oder [dunkel]; bewusst ohne Dynamic Color. Ein anderer [stil] bringt eigenen festen Grund, Schrift,
 * Formen und deckende Karten mit, in der [variante] und mit dem Akzent [akzent] (Name, unbekannt = Standard des Stils);
 * [dunkel] und [farbwelt] gelten dann nicht. [unschaerfe] = false zeigt Glas nur getönt – automatisch, wenn Animationen
 * systemweit aus sind; Stile haben nie Unschärfe.
 */
@Composable
fun FoodyTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    farbwelt: Farbwelt = Farbwelt.Standard,
    stil: FoodyStil = FoodyStil.Standard,
    variante: StilVariante = StilVariante.Standard,
    akzent: String? = null,
    unschaerfe: Boolean = unschaerfeErlaubt(),
    inhalt: @Composable () -> Unit,
) {
    val werte = remember(stil, variante, akzent, dunkel, farbwelt) { stilWerte(stil, variante, akzent, dunkel, farbwelt) }
    val farben = werte.farben
    // Gegenstück für inversePrimary: bei Milchglas der andere Modus, Stile haben nur einen Grund
    val gegenstueck = if (stil == FoodyStil.MILCHGLAS) farbwelt.werte(!dunkel).farben else farben
    val formen = werte.formen
    CompositionLocalProvider(
        LocalStil provides werte,
        LocalFarbwelt provides farbwelt,
        LocalUnschaerfe provides (unschaerfe && werte.flaeche == Flaechenart.GLAS),
    ) {
        MaterialTheme(
            colorScheme = farbschema(farben, gegenstueck, werte.dunkel),
            typography = werte.typografie,
            // extraSmall: Material zeichnet damit die Eingabefelder; rund wie im Entwurf statt fast eckig
            shapes = Shapes(extraSmall = formen.klein, small = formen.klein, medium = formen.mittel, large = formen.gross),
            content = inhalt,
        )
    }
}

/** Zugriff auf die eigenen Werte des Themes. */
object FoodyTheme {
    val farben: FoodyFarben
        @Composable @ReadOnlyComposable
        get() = LocalStil.current.farben

    /** Dunkler Grund: bei Milchglas der Modus, bei Stilen deren fester Grund. */
    val dunkel: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalStil.current.dunkel

    /** Gewählte Farbwelt; wirkt nur bei Milchglas. */
    val farbwelt: Farbwelt
        @Composable @ReadOnlyComposable
        get() = LocalFarbwelt.current

    /** Verlauf, Glastönung und Platzhalter des aktuellen Stils (bei Milchglas: der Farbwelt im aktuellen Modus). */
    val werte: FarbweltWerte
        @Composable @ReadOnlyComposable
        get() = LocalStil.current.werte

    /** Alles, was der gewählte Stil festlegt (Formen, Schrift, Flächen, Muster). */
    val stil: StilWerte
        @Composable @ReadOnlyComposable
        get() = LocalStil.current

    val formen: FoodyFormen
        @Composable @ReadOnlyComposable
        get() = LocalStil.current.formen

    /** Augenbraue über Überschriften in der Schrift des Stils. */
    val augenbraue: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalStil.current.augenbraue

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
