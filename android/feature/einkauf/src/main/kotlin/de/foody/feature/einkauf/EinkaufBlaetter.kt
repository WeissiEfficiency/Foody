package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.ListenKopf
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.RunderKnopf
import kotlinx.coroutines.launch

/** Menü ⋮ im Kopf: neue Liste, abgleichen (nur mit Zeitraum), frühere Listen, Liste löschen. */
@Composable
internal fun EinkaufMenue(ansicht: EinkaufsAnsicht?, onBlatt: (Blatt) -> Unit, onLoeschen: () -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Box {
        RunderKnopf(Icons.Outlined.MoreVert, "Weitere Aktionen", { offen = true })
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            val punkt = @Composable { text: String, aktion: () -> Unit ->
                DropdownMenuItem(text = { Text(text) }, onClick = { offen = false; aktion() })
            }
            punkt("Neue Liste aus dem Plan") { onBlatt(Blatt.Vorschau) }
            if (ansicht?.von != null) punkt("Mit Plan abgleichen") { onBlatt(Blatt.Abgleich) }
            punkt("Frühere Listen") { onBlatt(Blatt.Frueher) }
            if (ansicht != null) punkt("Liste löschen", onLoeschen)
        }
    }
}

/** Das offene Blatt des Einkaufs; Löschen eines Artikels mit Snackbar „Rückgängig“. */
@Composable
internal fun EinkaufBlaetter(vm: EinkaufViewModel, z: EinkaufZustand, snackbar: SnackbarHostState, onListe: (String) -> Unit) {
    val blatt by vm.blatt.collectAsStateWithLifecycle()
    val vorschau by vm.vorschau.collectAsStateWithLifecycle()
    val abgewaehlt by vm.abgleichAbgewaehlt.collectAsStateWithLifecycle()
    val fruehere by vm.fruehere.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    var loeschen by rememberSaveable { mutableStateOf<String?>(null) }

    when (val b = blatt) {
        Blatt.Vorschau -> vorschau?.let { v ->
            VorschauBlatt(v, vm::zeitraum, vm::vorschauUmschalten, onAnlegen = { bereich.launch { vm.anlegen() } }, onSchliessen = vm::blattSchliessen)
        }
        Blatt.Abgleich -> AbgleichBlatt(
            z.abgleich, abgewaehlt, vm::abgleichUmschalten, onUebernehmen = { bereich.launch { vm.abgleichen() } }, onSchliessen = vm::blattSchliessen,
        )
        is Blatt.Details -> {
            val artikel = z.ansicht?.alle?.firstOrNull { it.id == b.id }
            // Artikel inzwischen weg (gelöscht, per Sync): Blatt schließen
            if (artikel == null) {
                LaunchedEffect(b.id, z.geladen) { if (z.geladen) vm.blattSchliessen() }
            } else {
                DetailBlatt(
                    artikel,
                    onSpeichern = { n, notiz, abteilung -> vm.details(artikel.id, n, notiz, abteilung); vm.blattSchliessen() },
                    onLoeschen = {
                        vm.blattSchliessen()
                        bereich.launch {
                            val weg = vm.loeschen(artikel.id) ?: return@launch
                            val antwort = snackbar.showSnackbar("„${artikel.name}“ gelöscht", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
                            if (antwort == SnackbarResult.ActionPerformed) vm.wiederherstellen(weg)
                        }
                    },
                    onSchliessen = vm::blattSchliessen,
                )
            }
        }
        Blatt.Frueher -> FruehereBlatt(
            fruehere, onOeffnen = { vm.blattSchliessen(); onListe(it) }, onLoeschen = { l: ListenKopf -> loeschen = l.id }, onSchliessen = vm::blattSchliessen,
        )
        null -> Unit
    }
    loeschen?.let { id ->
        val name = fruehere.firstOrNull { it.id == id }?.name ?: id
        LoeschenFrage(name, onLoeschen = { loeschen = null; vm.listeLoeschen(id) }, onAbbrechen = { loeschen = null })
    }
}
