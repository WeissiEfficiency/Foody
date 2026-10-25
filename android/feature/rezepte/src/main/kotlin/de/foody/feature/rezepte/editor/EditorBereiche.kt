package de.foody.feature.rezepte.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.feature.rezepte.detail.gangName
import de.foody.feature.rezepte.detail.mahlzeitName

private val MAHLZEITEN = listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN, Mahlzeit.SNACK)

@Composable
internal fun EditorFelder(z: EditorZustand, vm: EditorViewModel) {
    val e = z.eingaben
    Feld("Name", e.name) { v -> vm.aendern { it.copy(name = v) } }
    FotoWahl(e.bild, vm)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Feld("Portionen", e.portionen, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(portionen = v) } }
        Feld("Vorbereitung (Min.)", e.vorbereitung, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(vorbereitung = v) } }
        Feld("Kochzeit (Min.)", e.kochzeit, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(kochzeit = v) } }
    }
    Feld("Tags (mit Komma)", e.tags) { v -> vm.aendern { it.copy(tags = v) } }
    Einordnungswahl(z, vm)
    Feld("Zutaten (eine je Zeile)", e.zutatenText, einzeilig = false) { v -> vm.aendern { it.copy(zutatenText = v) } }
    ZeilenVorschau(z.zeilen)
    Feld("Schritte (einer je Zeile)", e.schritteText, einzeilig = false) { v -> vm.aendern { it.copy(schritteText = v) } }
    Feld("Notizen", e.notizen, einzeilig = false) { v -> vm.aendern { it.copy(notizen = v) } }
    Feld("Quelle (Link)", e.quelle, tastatur = KeyboardType.Uri) { v -> vm.aendern { it.copy(quelle = v) } }
}

@Composable
private fun Feld(
    beschriftung: String,
    wert: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    tastatur: KeyboardType = KeyboardType.Text,
    einzeilig: Boolean = true,
    onWert: (String) -> Unit,
) {
    OutlinedTextField(
        wert, onWert, modifier, label = { Text(beschriftung) }, singleLine = einzeilig, minLines = if (einzeilig) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = tastatur),
    )
}

/** Mahlzeit und Gang als Chips; ohne eigene Wahl vorbelegt mit der Vermutung, „Automatisch“ setzt zurück. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Einordnungswahl(z: EditorZustand, vm: EditorViewModel) {
    val e = z.einordnung
    Augenbraue(if (z.eingaben.mahlzeiten == null && z.eingaben.gaenge == null) "Einordnung (vermutet)" else "Einordnung")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MAHLZEITEN.forEach { m -> FoodyChip(mahlzeitName(m), m in e.mahlzeiten, { vm.mahlzeitUmschalten(m) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Gang.entries.forEach { g -> FoodyChip(gangName(g), g in e.gaenge, { vm.gangUmschalten(g) }) }
    }
    if (z.eingaben.mahlzeiten != null || z.eingaben.gaenge != null) {
        FoodyKnopf("Automatisch", vm::automatisch, art = KnopfArt.TEXT)
    } else {
        Text("Ohne eigene Wahl ordnet Foody nach Name und Tags ein.", style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
    }
}
