package de.foody.feature.zutaten

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.VerwerfenFrage
import de.foody.core.scan.PackungScanKnopf
import kotlinx.coroutines.launch

/** Offene Rückfrage des Editors. */
private sealed interface Frage {
    data object Loeschen : Frage

    data class InVerwendung(val rezepte: Int) : Frage

    data class NameVergeben(val id: String, val name: String) : Frage

    data class StrichcodeGehoert(val id: String, val name: String) : Frage

    data class Zusammenfuehren(val nachId: String, val nachName: String) : Frage
}

/**
 * Editor einer Zutat: Name, Kategorie, Nährwerte, Umrechnung, Strichcode; „Speichern“ unten fest. Menü:
 * „Zusammenführen mit …“ (Blatt mit Zielwahl, dann Rückfrage) und „Löschen“. [onFertig] schließt den Editor – nach dem
 * Speichern, Löschen, Zusammenführen oder Zurück ohne (oder mit verworfenen) Änderungen.
 */
@Composable
fun ZutatEditorRoute(id: String, onFertig: () -> Unit, vm: ZutatEditorViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.laden(id) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var frage by remember { mutableStateOf<Frage?>(null) }
    var zielwahl by rememberSaveable { mutableStateOf(false) }
    var verwerfen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.ereignisse.collect { e ->
            when (e) {
                EditorEreignis.Fertig -> onFertig()
                is EditorEreignis.NameVergeben -> frage = Frage.NameVergeben(e.id, e.name)
                is EditorEreignis.InVerwendung -> frage = Frage.InVerwendung(e.rezepte)
                is EditorEreignis.StrichcodeGehoert -> frage = Frage.StrichcodeGehoert(e.id, e.name)
                is EditorEreignis.Meldung -> scope.launch { snackbar.showSnackbar(e.text) }
            }
        }
    }
    val zurueck = { if (z.geaendert) verwerfen = true else onFertig() }
    BackHandler(enabled = z.geaendert) { verwerfen = true }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = INHALT_MAX).fillMaxSize().safeDrawingPadding()) {
            Kopf(z.vorher?.name.orEmpty(), zurueck, onZusammenfuehren = { zielwahl = true }, onLoeschen = { frage = Frage.Loeschen })
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                z.entwurf?.let { entwurf ->
                    EditorFelder(
                        entwurf, z.fehler, z.synonyme, z.verwendetIn, onAendern = vm::aendern, onFeldFertig = vm::feldFertig,
                        markiert = z.markiert, scanQuelle = z.scanQuelle,
                        onOnlineAbrufen = if (z.imKatalogSelbst != null && !z.laeuft) vm::onlineAbrufen else null,
                        scanKnopf = { PackungScanKnopf(vm.scan, onPackung = vm::packungUebernehmen, onImKatalog = vm::imKatalog) },
                    )
                }
            }
            FoodyKnopf(
                "Speichern", vm::speichern,
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = UEBER_LEISTE),
                aktiv = z.geladen && !z.laeuft,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = UEBER_LEISTE + 64.dp))
    }

    val name = z.vorher?.name.orEmpty()
    when (val f = frage) {
        Frage.Loeschen -> LoeschenFrage(name, onLoeschen = { frage = null; vm.loeschen() }, onAbbrechen = { frage = null })
        is Frage.InVerwendung -> InVerwendungDialog(
            f.rezepte, onZusammenfuehren = { frage = null; zielwahl = true }, onAbbrechen = { frage = null },
        )
        is Frage.NameVergeben -> NameVergebenDialog(
            f.name, onZusammenfuehren = { frage = Frage.Zusammenfuehren(f.id, f.name) }, onAbbrechen = { frage = null },
        )
        is Frage.StrichcodeGehoert -> StrichcodeGehoertDialog(
            f.name, onZusammenfuehren = { frage = Frage.Zusammenfuehren(f.id, f.name) }, onAbbrechen = { frage = null },
        )
        is Frage.Zusammenfuehren -> ZusammenfuehrenFrage(
            zusammenfuehrenFrage(name, f.nachName, z.rezepte),
            onJa = { frage = null; vm.zusammenfuehren(f.nachId) }, onNein = { frage = null },
        )
        null -> Unit
    }
    if (zielwahl) {
        ZielwahlBlatt(
            z.ziele, onGewaehlt = { ziel -> zielwahl = false; frage = Frage.Zusammenfuehren(ziel.id, ziel.name) },
            onSchliessen = { zielwahl = false },
        )
    }
    if (verwerfen) VerwerfenFrage(onVerwerfen = { verwerfen = false; onFertig() }, onWeiter = { verwerfen = false })
}

@Composable
private fun Kopf(titel: String, onZurueck: () -> Unit, onZusammenfuehren: () -> Unit, onLoeschen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onZurueck) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
        }
        Column(Modifier.weight(1f)) {
            Augenbraue("Zutat")
            Text(titel, style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr", tint = FoodyTheme.farben.text) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Zusammenführen mit …") }, onClick = { menu = false; onZusammenfuehren() })
                DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; onLoeschen() })
            }
        }
    }
}
