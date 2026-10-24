package de.foody.core.designsystem.komponenten

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/** Ab dieser Schriftskalierung bekommen Titel mehr Zeilen und Knöpfe rutschen unter den Text. */
private const val GROSSE_SCHRIFT = 1.5f

@Composable
internal fun grosseSchrift(): Boolean = LocalDensity.current.fontScale >= GROSSE_SCHRIFT
