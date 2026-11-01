package de.foody.feature.rezepte.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import kotlinx.coroutines.launch

/** Was das Detail auslösen kann; die Navigation liegt in der App. */
internal class DetailAktionen(
    val onKochen: (Int) -> Unit,
    val onBearbeiten: () -> Unit,
    val onDuplizieren: () -> Unit,
    /** Mit den Portionen des Steppers. */
    val onEinkauf: (Int) -> Unit,
    val onArchivieren: (Boolean) -> Unit,
    val onFavorit: (Boolean) -> Unit,
    val onPortionen: (Boolean) -> Unit,
    val onZurueck: () -> Unit,
)

/**
 * Rezeptdetail: Foto mit Titelleiste, Aktionen, Portionen-Stepper (skaliert Zutaten und Nährwerte je Rezept), Zutaten,
 * Schritte, Notizen, Quelle, Einordnung, Nährwerte. Archivieren mit „Rückgängig“; unbekannte ID → „nicht gefunden“.
 */
@Composable
fun RezeptDetailRoute(
    id: String,
    onKochen: (String, Int) -> Unit,
    onBearbeiten: (String) -> Unit,
    onDupliziert: (String) -> Unit,
    onZurueck: () -> Unit,
    onEinkauf: () -> Unit = {},
    vm: RezeptDetailViewModel = hiltViewModel(),
) {
    // Portionen im Dialog „Auf die Einkaufsliste“; null = zu
    var einkaufPortionen by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { vm.zeigen(id) }
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val bereich = rememberCoroutineScope()
    val aktionen = DetailAktionen(
        onKochen = { p -> onKochen(id, p) },
        onBearbeiten = { onBearbeiten(id) },
        onDuplizieren = { bereich.launch { vm.duplizieren()?.let(onDupliziert) } },
        onEinkauf = { einkaufPortionen = it },
        onArchivieren = { an ->
            bereich.launch {
                vm.archivieren(id, an)
                if (an) {
                    val antwort = snackbar.showSnackbar("Rezept archiviert", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
                    if (antwort == SnackbarResult.ActionPerformed) vm.archivieren(id, false)
                }
            }
        },
        onFavorit = vm::favorit,
        onPortionen = vm::portionenSchritt,
        onZurueck = onZurueck,
    )
    Box(Modifier.fillMaxSize()) {
        when (val z = zustand) {
            DetailZustand.Laden -> Unit
            DetailZustand.NichtGefunden -> LeererZustandFlaeche(
                Icons.AutoMirrored.Outlined.MenuBook, "Rezept nicht gefunden", "Es wurde vielleicht gelöscht.",
                aktionText = "Zurück", onAktion = onZurueck, modifier = Modifier.safeDrawingPadding().padding(FoodyAbstaende.s4),
            )
            is DetailZustand.Da -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding().padding(bottom = ueberLeiste(104.dp)),
                    verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4),
                ) { DetailInhalt(z, aktionen) }
            }
        }
        einkaufPortionen?.let { p ->
            EinkaufsDialog(
                p, onPortionen = { einkaufPortionen = it }, onAbbrechen = { einkaufPortionen = null },
                onHinzufuegen = {
                    einkaufPortionen = null
                    bereich.launch {
                        val anzahl = vm.aufEinkaufsliste(p)
                        if (anzahl == 0) {
                            snackbar.showSnackbar("Nichts einzukaufen")
                        } else {
                            val antwort = snackbar.showSnackbar(
                                "$anzahl Artikel auf der Einkaufsliste", actionLabel = "Ansehen", duration = SnackbarDuration.Long,
                            )
                            if (antwort == SnackbarResult.ActionPerformed) onEinkauf()
                        }
                    }
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = ueberLeiste(88.dp)))
    }
}
