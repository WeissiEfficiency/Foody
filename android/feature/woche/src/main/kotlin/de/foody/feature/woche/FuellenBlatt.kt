package de.foody.feature.woche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.domain.Mahlzeit
import java.time.LocalDate

/** Was das Füllen-Blatt auslösen kann. */
internal class FuellenAktionen(
    val onMahlzeit: (Mahlzeit) -> Unit,
    val onAbwaehlen: (LocalDate) -> Unit,
    val onTagNeu: (LocalDate) -> Unit,
    val onNeuMischen: () -> Unit,
    val onUebernehmen: () -> Unit,
)

/** „DI 27.10.“ */
internal fun tagKurz(d: LocalDate): String =
    "${listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")[d.dayOfWeek.value - 1]} ${d.dayOfMonth}.${d.monthValue}."

/**
 * „Leere Tage füllen“: Mahlzeit wählen, je Tag ein Vorschlag (abwählbar, „↻“ zieht neu), „Neu mischen“ und
 * „Übernehmen (n)“. [namen] = Rezept-ID → Name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FuellenBlatt(z: FuellenZustand, namen: Map<String, String>, a: FuellenAktionen, onSchliessen: () -> Unit) {
    val anzahl = (z.vorschlag.keys - z.abgewaehlt).size
    // Ganz geöffnet und scrollbar: die Knöpfe unten sollen ohne Ziehen erreichbar sein
    ModalBottomSheet(
        onDismissRequest = onSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FoodyTheme.farben.grund,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leere Tage füllen", style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN).forEach { m ->
                    FoodyChip(mahlzeitAnzeige(m), ausgewaehlt = m == z.mahlzeit, onKlick = { a.onMahlzeit(m) })
                }
            }
            z.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
            z.tage.forEach { tag -> VorschlagZeile(tag, z.vorschlag[tag]?.let { namen[it] }, tag !in z.abgewaehlt, a) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodyKnopf("Neu mischen", a.onNeuMischen, Modifier.weight(1f), art = KnopfArt.UMRISS, aktiv = z.hinweis == null)
                FoodyKnopf("Übernehmen ($anzahl)", a.onUebernehmen, Modifier.weight(1f), aktiv = anzahl > 0)
            }
        }
    }
}

@Composable
private fun VorschlagZeile(tag: LocalDate, name: String?, gewaehlt: Boolean, a: FuellenAktionen) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = name != null && gewaehlt, onCheckedChange = { a.onAbwaehlen(tag) }, enabled = name != null,
            modifier = Modifier.semantics { contentDescription = "${tagKurz(tag)} übernehmen" },
        )
        Text(
            "${tagKurz(tag)} · ${name ?: "kein Vorschlag"}", Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge, color = if (name != null) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
        )
        IconButton(onClick = { a.onTagNeu(tag) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Neuer Vorschlag für ${tagKurz(tag)}", tint = FoodyTheme.farben.text)
        }
    }
}
