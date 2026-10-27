package de.foody.feature.zutaten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.foody.core.data.zutaten.Angleichung
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt

/**
 * „Neue Zutat“: Name eingeben; gibt es ihn schon ([gibtEsSchon] = vorhandener Name), bietet der Dialog „Öffnen“ an.
 * Unter dem Namen der Scan-[knopf]; nach einem Scan [packungHinweis] („Werte aus … werden übernommen“), und
 * [namensVorschlag] füllt das Namensfeld, solange es leer ist.
 */
@Composable
internal fun NeueZutatDialog(
    gibtEsSchon: String?,
    onAnlegen: (String) -> Unit,
    onOeffnen: () -> Unit,
    onAbbrechen: () -> Unit,
    knopf: @Composable () -> Unit = {},
    packungHinweis: String? = null,
    namensVorschlag: String? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(namensVorschlag) { if (!namensVorschlag.isNullOrBlank() && name.isBlank()) name = namensVorschlag }
    val anlegen = { if (name.isNotBlank()) onAnlegen(name) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Neue Zutat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { anlegen() }),
                )
                knopf()
                packungHinweis?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.primaer)
                }
                if (gibtEsSchon != null) Text("„$gibtEsSchon“ gibt es schon.")
            }
        },
        confirmButton = {
            if (gibtEsSchon != null) {
                FoodyKnopf("Öffnen", onOeffnen, art = KnopfArt.TEXT)
            } else {
                FoodyKnopf("Anlegen", anlegen, art = KnopfArt.TEXT, aktiv = name.isNotBlank())
            }
        },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}

/** Vorschau von „Namen vereinheitlichen“; ohne Schritte nur der Hinweis „Alle Namen sind schon einheitlich.“ */
@Composable
internal fun VereinheitlichenDialog(schritte: List<Angleichung>, onAnwenden: () -> Unit, onAbbrechen: () -> Unit) {
    if (schritte.isEmpty()) {
        AlertDialog(
            onDismissRequest = onAbbrechen,
            title = { Text("Namen vereinheitlichen") },
            text = { Text("Alle Namen sind schon einheitlich.") },
            confirmButton = { FoodyKnopf("OK", onAbbrechen, art = KnopfArt.TEXT) },
        )
        return
    }
    val kategorien = schritte.count { it is Angleichung.KategorieErgaenzen }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Namen vereinheitlichen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vorschauZeilen(schritte).forEach { Text(it) }
                if (kategorien > 0) Text(kategorienText(kategorien))
            }
        },
        confirmButton = { FoodyKnopf("Anwenden", onAnwenden, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}
