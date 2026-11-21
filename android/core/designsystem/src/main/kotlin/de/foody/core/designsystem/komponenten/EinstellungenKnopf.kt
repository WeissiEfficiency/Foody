package de.foody.core.designsystem.komponenten

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Zahnrad oben rechts im Kopf von Heute, in allen Stilen gleich groß. */
@Composable
fun EinstellungenKnopf(onKlick: () -> Unit, modifier: Modifier = Modifier) {
    RunderKnopf(Icons.Outlined.Settings, "Einstellungen", onKlick, modifier)
}
