package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.domain.ShoppingCatalog

/** Längster Artikelname (wie im Repository). */
private const val NAME_MAX = 60

/**
 * Details eines Artikels: Name und Notiz ändern, Abteilung wählen; die Menge eines Plan-Artikels nur lesen (sie bestimmt
 * der Abgleich); Herkunft; „Speichern“ und „Löschen“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailBlatt(
    a: EinkaufsArtikel,
    onSpeichern: (name: String, notiz: String, abteilung: String) -> Unit,
    onLoeschen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    var name by rememberSaveable(a.id) { mutableStateOf(a.name) }
    var notiz by rememberSaveable(a.id) { mutableStateOf(a.notiz.orEmpty()) }
    var abteilung by rememberSaveable(a.id) { mutableStateOf(a.abteilung) }
    val gueltig = name.trim().length in 1..NAME_MAX
    BlattRahmen(
        "${a.emoji} ${a.name}", onSchliessen,
        knopf = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodyKnopf("Löschen", onLoeschen, Modifier.weight(1f), art = KnopfArt.UMRISS)
                FoodyKnopf("Speichern", { onSpeichern(name, notiz, abteilung) }, Modifier.weight(1f), aktiv = gueltig)
            }
        },
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, isError = !gueltig)
        OutlinedTextField(notiz, { notiz = it }, Modifier.fillMaxWidth(), label = { Text("Notiz") }, singleLine = true)
        if (a.plan && a.menge != null) {
            Text("Menge: ${a.menge} (aus dem Plan)", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        }
        a.herkunft?.let {
            Augenbraue("Herkunft")
            Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
        }
        Augenbraue("Abteilung")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShoppingCatalog.sectionOrder.forEach { s -> FoodyChip(s, ausgewaehlt = s == abteilung, onKlick = { abteilung = s }) }
        }
    }
}
