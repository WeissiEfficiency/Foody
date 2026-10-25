package de.foody.app3

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import de.foody.core.data.Darstellung

/** Schleier hinter der Navigationsleiste bei Drei-Tasten-Navigation (wie v1). */
private val HELLER_SCHLEIER = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DUNKLER_SCHLEIER = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** Ob die App dunkel zeichnet: „System“ folgt der Einstellung des Geräts. */
@Composable
fun istDunkel(d: Darstellung): Boolean = when (d) {
    Darstellung.SYSTEM -> isSystemInDarkTheme()
    Darstellung.HELL -> false
    Darstellung.DUNKEL -> true
}

/**
 * Symbole von Status- und Navigationsleiste passend zur gewählten Darstellung, nicht nur zum System: Bei „Dunkel“ und
 * hellem System wären Uhr und Akku sonst dunkel auf dunklem Grund.
 */
@Composable
fun SystemleistenPassend(dunkel: Boolean) {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity, dunkel) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dunkel },
            navigationBarStyle = SystemBarStyle.auto(HELLER_SCHLEIER, DUNKLER_SCHLEIER) { dunkel },
        )
        onDispose {}
    }
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
