package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.scan.PackungScanKnopf
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.slotName
import kotlinx.coroutines.launch

/** Test-Tag des Speichern-Knopfs (die Überschrift heißt auch „Eintragen“). */
const val EINTRAGEN_SPEICHERN = "eintragen-speichern"

private val WEGE = listOf(
    EintragenWeg.REZEPT to "Rezept",
    EintragenWeg.ZUTAT to "Zutat",
    EintragenWeg.PLAN to "Aus dem Plan",
    EintragenWeg.FREI to "Frei",
)

/** Blatt von unten zum Eintragen (siehe [BlattAuftrag]). Schließt nach erfolgreichem Speichern. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EintragenBlatt(auftrag: BlattAuftrag, onFertig: () -> Unit, vm: EintragenViewModel) {
    LaunchedEffect(auftrag.id) { vm.oeffnen(auftrag.datum, auftrag.mahlzeit, auftrag = auftrag.id) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    val moeglich by vm.speichernMoeglich.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val e = z.eingaben
    ModalBottomSheet(onDismissRequest = onFertig, containerColor = FoodyTheme.farben.grund) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Eintragen", style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MAHLZEITEN.forEach { m -> FoodyChip(m.anzeigename(), m == e.mahlzeit, { vm.mahlzeit(m) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WEGE.forEach { (w, text) -> FoodyChip(text, w == e.weg, { vm.weg(w) }) }
            }
            when (e.weg) {
                EintragenWeg.REZEPT -> RezeptWahl(z, vm)
                EintragenWeg.ZUTAT -> ZutatWahl(z, vm)
                EintragenWeg.PLAN -> PlanWahl(z, vm)
                EintragenWeg.FREI -> FreiEingabe(z, vm)
            }
            FoodyKnopf(
                "Eintragen",
                { bereich.launch { if (vm.speichern() != null) onFertig() } },
                Modifier.fillMaxWidth().testTag(EINTRAGEN_SPEICHERN),
                aktiv = moeglich,
            )
        }
    }
}

@Composable
private fun Suche(text: String, beschriftung: String, onText: (String) -> Unit) {
    OutlinedTextField(text, onText, Modifier.fillMaxWidth(), label = { Text(beschriftung) }, singleLine = true)
}

/** Auswählbare Trefferzeile mit Haken, mindestens 48 dp hoch. */
@Composable
private fun Treffer(text: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(gewaehlt, onClick = onWahl, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        if (gewaehlt) Icon(Icons.Outlined.Check, contentDescription = null, tint = FoodyTheme.farben.primaer)
    }
}

@Composable
private fun RezeptWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Rezept suchen", vm::suche)
    z.rezepte.forEach { r -> Treffer(r.titel, r.id == z.eingaben.rezeptId) { vm.rezeptWaehlen(r.id) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.portionenSchritt(mehr = false) }) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen") }
        OutlinedTextField(
            z.eingaben.portionen, vm::portionen, Modifier.width(120.dp), label = { Text("Portionen") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        IconButton(onClick = { vm.portionenSchritt(mehr = true) }) { Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZutatWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Zutat suchen", vm::suche)
    z.zutaten.forEach { t -> Treffer(t.name, t.id == z.eingaben.zutatId) { vm.zutatWaehlen(t.id) } }
    // Nach „im Katalog“ (Scan in „Frei“) geht der Fokus auf die Menge
    val mengeFokus = remember { FocusRequester() }
    LaunchedEffect(vm) { vm.mengeFokus.collect { mengeFokus.requestFocus() } }
    Zahlfeld("Menge", z.eingaben.menge, focusRequester = mengeFokus, onWert = vm::menge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EINTRAGEN_EINHEITEN.forEach { u -> FoodyChip(u.symbol, u == z.eingaben.einheit, { vm.einheit(u) }) }
    }
}

@Composable
private fun PlanWahl(z: EintragenZustand, vm: EintragenViewModel) {
    if (z.plan.isEmpty()) {
        Text("Für diesen Tag ist nichts Geplantes mehr offen.", color = FoodyTheme.farben.nebentext)
    }
    z.plan.forEach { p ->
        Treffer("${p.titel} · ${slotName(p.slot)}", p.planEintragId == z.eingaben.planEintragId) { vm.planWaehlen(p.planEintragId) }
    }
}

/** „Frei“: oben der Scan-Knopf; nach einem Scan die Packung ([PackungEingabe]) statt der freien Felder. */
@Composable
private fun FreiEingabe(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    if (e.packung != null) {
        PackungEingabe(e, z.vorhandeneZutat, vm)
        return
    }
    PackungScanKnopf(vm.scan, onPackung = vm::packungUebernehmen, onImKatalog = vm::imKatalog)
    OutlinedTextField(e.freiName, vm::freiName, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
    Zahlfeld("kcal", e.freiKcal, ganzzahlig = true, onWert = vm::freiKcal)
    Zahlfeld("Eiweiß (g, optional)", e.freiEiweiss, onWert = vm::freiEiweiss)
    Zahlfeld("Kohlenhydrate (g, optional)", e.freiKh, onWert = vm::freiKh)
    Zahlfeld("Fett (g, optional)", e.freiFett, onWert = vm::freiFett)
}
