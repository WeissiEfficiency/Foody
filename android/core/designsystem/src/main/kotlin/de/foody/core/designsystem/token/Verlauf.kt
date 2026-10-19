package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Die zwei Radialverläufe des Hintergrunds: oben links und unten rechts, jeweils mit Alpha auf dem Grund. */
@Immutable
data class Verlauf(val oben: Color, val unten: Color)

/** Aprikose oben links, Oliv unten rechts. */
val VerlaufHell = Verlauf(oben = Color(0xFFFFB882).copy(alpha = 0.60f), unten = Color(0xFFBAC480).copy(alpha = 0.50f))

/** Glut oben links, Oliv unten rechts. */
val VerlaufDunkel = Verlauf(oben = Color(0xFF8C3C19).copy(alpha = 0.45f), unten = Color(0xFF46501E).copy(alpha = 0.40f))
