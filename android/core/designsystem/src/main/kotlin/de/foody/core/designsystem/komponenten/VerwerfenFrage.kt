package de.foody.core.designsystem.komponenten

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** „Änderungen verwerfen?“ – vor dem Verlassen eines Editors mit ungespeicherten Änderungen. */
@Composable
fun VerwerfenFrage(onVerwerfen: () -> Unit, onWeiter: () -> Unit) {
    AlertDialog(
        onDismissRequest = onWeiter,
        title = { Text("Änderungen verwerfen?") },
        text = { Text("Die ungespeicherten Änderungen gehen verloren.") },
        confirmButton = { FoodyKnopf("Verwerfen", onVerwerfen, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Weiter bearbeiten", onWeiter, art = KnopfArt.TEXT) },
    )
}
