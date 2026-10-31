package de.foody.core.designsystem.komponenten

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Rückfrage vor einer Aktion: [text], optional mit [titel]; [ja] löst [onJa] aus, [nein] (oder Danebentippen)
 * [onNein].
 */
@Composable
fun FoodyFrage(
    titel: String?,
    text: String,
    ja: String,
    onJa: () -> Unit,
    onNein: () -> Unit,
    nein: String = "Abbrechen",
) {
    AlertDialog(
        onDismissRequest = onNein,
        title = titel?.let { { Text(it) } },
        text = { Text(text) },
        confirmButton = { FoodyKnopf(ja, onJa, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf(nein, onNein, art = KnopfArt.TEXT) },
    )
}

/** „Änderungen verwerfen?“ – vor dem Verlassen eines Editors mit ungespeicherten Änderungen. */
@Composable
fun VerwerfenFrage(onVerwerfen: () -> Unit, onWeiter: () -> Unit) = FoodyFrage(
    "Änderungen verwerfen?", "Die ungespeicherten Änderungen gehen verloren.", "Verwerfen", onVerwerfen, onWeiter,
    nein = "Weiter bearbeiten",
)
