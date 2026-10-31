package de.foody.feature.zutaten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.zutaten.ZutatFilter
import de.foody.core.data.zutaten.ZutatKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.core.scan.PackungScanKnopf
import de.foody.core.scan.ScanTexte
import kotlinx.coroutines.launch

/** Abstand zum unteren Rand für Knöpfe und Snackbars: darunter schwebt auf dem Handy die Navigationsleiste. */
internal val UEBER_LEISTE = 88.dp

private val FILTER = listOf(
    ZutatFilter.OHNE_NAEHRWERTE to "Ohne Nährwerte", ZutatFilter.EIGENE to "Eigene",
    ZutatFilter.UNBENUTZT to "Unbenutzt", ZutatFilter.MIT_STRICHCODE to "Mit Strichcode",
)

/**
 * Zutatenkatalog: Suche, Filterchips, Zählzeile und Liste; „+“ legt eine Zutat an, das Menü bietet „Namen
 * vereinheitlichen“ mit Vorschau. Meldungen des Editors erscheinen hier als Snackbar.
 */
@Composable
fun ZutatenListeRoute(onZutat: (String) -> Unit, onZurueck: () -> Unit, vm: ZutatenListeViewModel = hiltViewModel()) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val vorschau by vm.vorschau.collectAsStateWithLifecycle()
    val packung by vm.neuPackung.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var suche by rememberSaveable { mutableStateOf(z.suche) }
    // Nach einem Prozessende stellt das Feld den Text wieder her, das ViewModel beginnt leer
    LaunchedEffect(Unit) { if (suche != z.suche) vm.suchen(suche) }
    var neu by rememberSaveable { mutableStateOf(false) }
    var gibtEsSchon by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(vm) {
        vm.ereignisse.collect { e ->
            when (e) {
                is ListeEreignis.Oeffnen -> {
                    neu = false
                    onZutat(e.id)
                }
                is ListeEreignis.GibtEsSchon -> gibtEsSchon = e.id to e.name
                is ListeEreignis.Meldung -> scope.launch { snackbar.showSnackbar(e.text) }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxSize().safeDrawingPadding()) {
            Kopf(onZurueck, onVereinheitlichen = vm::vorschauLaden)
            OutlinedTextField(
                suche, { suche = it; vm.suchen(it) }, Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
                label = { Text("Suchen") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            LazyRow(contentPadding = PaddingValues(horizontal = FoodyAbstaende.s4), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                items(FILTER) { (f, text) -> FoodyChip(text, f in z.filter, { vm.filterUmschalten(f) }) }
            }
            if (z.geladen) {
                Text(
                    zaehlzeile(z.treffer), Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
                    style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
                )
            }
            if (z.geladen && z.treffer.isEmpty()) {
                Text(
                    "Keine Zutat gefunden", Modifier.padding(FoodyAbstaende.s4),
                    style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.nebentext,
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = UEBER_LEISTE + 72.dp)) {
                items(z.treffer, key = { it.id }) { zutat ->
                    ZutatZeile(zutat) { onZutat(zutat.id) }
                    HorizontalDivider(Modifier.padding(horizontal = FoodyAbstaende.s4), color = FoodyTheme.farben.nebentext.copy(alpha = 0.2f))
                }
            }
        }
        FloatingActionButton(
            onClick = { gibtEsSchon = null; neu = true },
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = FoodyAbstaende.s4, bottom = UEBER_LEISTE),
        ) { Icon(Icons.Outlined.Add, contentDescription = "Neue Zutat") }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = UEBER_LEISTE + 72.dp))
    }

    if (neu) {
        NeueZutatDialog(
            gibtEsSchon = gibtEsSchon?.second,
            onAnlegen = vm::anlegen,
            onOeffnen = { gibtEsSchon?.first?.let(vm::vorhandeneOeffnen) },
            onAbbrechen = { neu = false; gibtEsSchon = null; vm.neuVerwerfen() },
            knopf = { PackungScanKnopf(vm.scanLauf) },
            packungHinweis = packung?.let { ScanTexte.werdenUebernommen(it.quelle) },
            namensVorschlag = packung?.let(::nameAusPackung),
        )
    }
    vorschau?.let { VereinheitlichenDialog(it, onAnwenden = vm::anwenden, onAbbrechen = vm::vorschauSchliessen) }
}

@Composable
private fun Kopf(onZurueck: () -> Unit, onVereinheitlichen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(end = FoodyAbstaende.s1, top = FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onZurueck) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
        }
        Column(Modifier.weight(1f)) {
            Augenbraue("Einstellungen")
            Text("Zutatenkatalog", Modifier.ueberschrift(), style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr", tint = FoodyTheme.farben.text) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Namen vereinheitlichen") }, onClick = { menu = false; onVereinheitlichen() })
            }
        }
    }
}

@Composable
private fun ZutatZeile(z: ZutatKurz, onKlick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onKlick)
            .padding(horizontal = FoodyAbstaende.s4, vertical = 10.dp),
    ) {
        z.kategorie?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(z.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            Text(kcalText(z.kcal), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
        }
        Text(verwendungText(z.rezepte), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
    }
}
