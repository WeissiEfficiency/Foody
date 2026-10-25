package de.foody.app3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import de.foody.core.data.Darstellung

/** Ob die App dunkel zeichnet: „System“ folgt der Einstellung des Geräts. */
@Composable
fun istDunkel(d: Darstellung): Boolean = when (d) {
    Darstellung.SYSTEM -> isSystemInDarkTheme()
    Darstellung.HELL -> false
    Darstellung.DUNKEL -> true
}
