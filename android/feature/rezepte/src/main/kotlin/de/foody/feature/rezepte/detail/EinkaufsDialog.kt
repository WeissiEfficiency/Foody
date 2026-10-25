package de.foody.feature.rezepte.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt

/** „Auf die Einkaufsliste“: Portionen wählen (1–99), dann „Hinzufügen“. */
@Composable
internal fun EinkaufsDialog(portionen: Int, onPortionen: (Int) -> Unit, onHinzufuegen: () -> Unit, onAbbrechen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Auf die Einkaufsliste") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPortionen((portionen - 1).coerceAtLeast(1)) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Eine Portion weniger", tint = FoodyTheme.farben.text)
                }
                Text(
                    if (portionen == 1) "1 Portion" else "$portionen Portionen",
                    style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text,
                )
                IconButton(onClick = { onPortionen((portionen + 1).coerceAtMost(99)) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Eine Portion mehr", tint = FoodyTheme.farben.text)
                }
            }
        },
        confirmButton = { FoodyKnopf("Hinzufügen", onHinzufuegen, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}
