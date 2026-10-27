package de.foody.feature.zutaten

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt

@Composable
private fun Frage(titel: String?, text: String, ja: String, onJa: () -> Unit, onNein: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNein,
        title = titel?.let { { Text(it) } },
        text = { Text(text) },
        confirmButton = { FoodyKnopf(ja, onJa, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Abbrechen", onNein, art = KnopfArt.TEXT) },
    )
}

@Composable
internal fun LoeschenFrage(name: String, onLoeschen: () -> Unit, onAbbrechen: () -> Unit) =
    Frage("„$name“ löschen?", "Die Zutat wird aus dem Katalog entfernt.", "Löschen", onLoeschen, onAbbrechen)

/** Die Zutat steckt noch in Rezepten: statt löschen zusammenführen. */
@Composable
internal fun InVerwendungDialog(rezepte: Int, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    Frage(null, inVerwendungText(rezepte), "Zusammenführen …", onZusammenfuehren, onAbbrechen)

/** Der neue Name gehört einer anderen Zutat. */
@Composable
internal fun NameVergebenDialog(name: String, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    Frage(null, "„$name“ gibt es schon. Diese Zutat damit zusammenführen?", "Zusammenführen", onZusammenfuehren, onAbbrechen)

/** Der gescannte Strichcode gehört schon einer anderen Zutat. */
@Composable
internal fun StrichcodeGehoertDialog(name: String, onZusammenfuehren: () -> Unit, onAbbrechen: () -> Unit) =
    Frage(null, "Diesen Strichcode hat schon „$name“.", "Zusammenführen …", onZusammenfuehren, onAbbrechen)

@Composable
internal fun ZusammenfuehrenFrage(text: String, onJa: () -> Unit, onNein: () -> Unit) =
    Frage(null, text, "Zusammenführen", onJa, onNein)
