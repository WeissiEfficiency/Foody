package de.foody.feature.rezepte.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.feature.rezepte.detail.gangName
import de.foody.feature.rezepte.detail.mahlzeitName

private val MAHLZEITEN = listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN, Mahlzeit.SNACK)

@Composable
internal fun EditorFelder(z: EditorZustand, vm: EditorViewModel) {
    val e = z.eingaben
    FoodyTextfeld("Name", e.name) { v -> vm.aendern { it.copy(name = v) } }
    FotoWahl(e.bild, vm)
    Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        FoodyTextfeld("Portionen", e.portionen, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(portionen = v) } }
        FoodyTextfeld("Vorbereitung (Min.)", e.vorbereitung, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(vorbereitung = v) } }
        FoodyTextfeld("Kochzeit (Min.)", e.kochzeit, Modifier.weight(1f), KeyboardType.Number) { v -> vm.aendern { it.copy(kochzeit = v) } }
    }
    FoodyTextfeld("Tags (mit Komma)", e.tags) { v -> vm.aendern { it.copy(tags = v) } }
    Einordnungswahl(z, vm)
    FoodyTextfeld("Zutaten (eine je Zeile)", e.zutatenText, einzeilig = false) { v -> vm.aendern { it.copy(zutatenText = v) } }
    ZeilenVorschau(z.zeilen)
    FoodyTextfeld("Schritte (einer je Zeile)", e.schritteText, einzeilig = false) { v -> vm.aendern { it.copy(schritteText = v) } }
    FoodyTextfeld("Notizen", e.notizen, einzeilig = false) { v -> vm.aendern { it.copy(notizen = v) } }
    FoodyTextfeld("Quelle (Link)", e.quelle, tastatur = KeyboardType.Uri) { v -> vm.aendern { it.copy(quelle = v) } }
}

/** Mahlzeit und Gang als Chips; ohne eigene Wahl vorbelegt mit der Vermutung, „Automatisch“ setzt zurück. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Einordnungswahl(z: EditorZustand, vm: EditorViewModel) {
    val e = z.einordnung
    Augenbraue(if (z.eingaben.mahlzeiten == null && z.eingaben.gaenge == null) "Einordnung (vermutet)" else "Einordnung")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        MAHLZEITEN.forEach { m -> FoodyChip(mahlzeitName(m), m in e.mahlzeiten, { vm.mahlzeitUmschalten(m) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Gang.entries.forEach { g -> FoodyChip(gangName(g), g in e.gaenge, { vm.gangUmschalten(g) }) }
    }
    if (z.eingaben.mahlzeiten != null || z.eingaben.gaenge != null) {
        FoodyKnopf("Automatisch", vm::automatisch, art = KnopfArt.TEXT)
    } else {
        Text("Ohne eigene Wahl ordnet Foody nach Name und Tags ein.", style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
    }
}
