package de.foody.feature.woche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.domain.Mahlzeit

private const val MAX_NAME = 30

/** Mahlzeit für einen neuen Eintrag oder zum Umbenennen: die vier bekannten oder ein eigener Name (1–30 Zeichen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MahlzeitWahl(titel: String, onWahl: (String) -> Unit, onAbbrechen: () -> Unit) {
    var eigener by rememberSaveable { mutableStateOf("") }
    val gueltig = eigener.trim().length in 1..MAX_NAME
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN).forEach { m ->
                        FoodyChip(mahlzeitAnzeige(m), ausgewaehlt = false, onKlick = { onWahl(m.name) })
                    }
                }
                OutlinedTextField(
                    eigener, { eigener = it.take(MAX_NAME) }, Modifier.fillMaxWidth(), label = { Text("Eigener Name") }, singleLine = true,
                )
            }
        },
        confirmButton = { FoodyKnopf("Weiter", { onWahl(eigener.trim()) }, art = KnopfArt.TEXT, aktiv = gueltig) },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}
