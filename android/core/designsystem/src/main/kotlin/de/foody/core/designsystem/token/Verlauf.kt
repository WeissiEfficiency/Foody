package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Die zwei Radialverläufe des Hintergrunds: oben links und unten rechts, jeweils mit Alpha auf dem Grund. Beide Töne
 * kommen aus der Hauptfarbe der [Farbwelt] (unten um 28° verschoben).
 */
@Immutable
data class Verlauf(val oben: Color, val unten: Color)
