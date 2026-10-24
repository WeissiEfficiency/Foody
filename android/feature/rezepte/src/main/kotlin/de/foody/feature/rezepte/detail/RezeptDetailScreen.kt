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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import kotlinx.coroutines.launch

/** Lesebreite des Detail-Inhalts. */
private val INHALT_MAX = 720.dp

/** Was das Detail auslösen kann; die Navigation liegt in der App. */
internal class DetailAktionen(
    val onKochen: (Int) -> Unit,
    val onBearbeiten: () -> Unit,
    val onDuplizieren: () -> Unit,
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
    vm: RezeptDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.zeigen(id) }
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val bereich = rememberCoroutineScope()
    val aktionen = DetailAktionen(
        onKochen = { p -> onKochen(id, p) },
        onBearbeiten = { onBearbeiten(id) },
        onDuplizieren = { bereich.launch { vm.duplizieren()?.let(onDupliziert) } },
        onArchivieren = { an ->
            bereich.launch {
                vm.archivieren(an)
                if (an) {
                    val antwort = snackbar.showSnackbar("Rezept archiviert", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
                    if (antwort == SnackbarResult.ActionPerformed) vm.archivieren(false)
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
                aktionText = "Zurück", onAktion = onZurueck, modifier = Modifier.safeDrawingPadding().padding(16.dp),
            )
            is DetailZustand.Da -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = INHALT_MAX).fillMaxWidth().safeDrawingPadding().padding(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { DetailInhalt(z, aktionen) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 88.dp))
    }
}
