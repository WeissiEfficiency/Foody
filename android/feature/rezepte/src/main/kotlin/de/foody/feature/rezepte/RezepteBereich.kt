package de.foody.feature.rezepte

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.importieren.ImportBlatt
import de.foody.feature.rezepte.importieren.ImportViewModel
import de.foody.feature.rezepte.liste.RezeptListeRoute
import de.foody.feature.rezepte.liste.RezeptListeViewModel

/** Ab dieser Breite stehen Liste und Rezept nebeneinander. */
private val ZWEISPALTIG_AB = 840.dp
private val LISTENBREITE = 360.dp

private enum class Rechts { DETAIL, NEU, BEARBEITEN }

/**
 * Der Tab „Rezepte“. Unter 840 dp nur die Liste, Detail und Editor sind eigene Ziele ([onOeffnen], [onNeu]); ab 840 dp
 * Liste links und Detail bzw. Editor rechts, ohne Navigation. Der Import läuft als Blatt über der Liste.
 */
@Composable
fun RezepteBereich(
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
    onKochen: (String, Int) -> Unit,
    listeVm: RezeptListeViewModel = hiltViewModel(),
    detailVm: RezeptDetailViewModel = hiltViewModel(),
    importVm: ImportViewModel? = null,
) {
    var importOffen by rememberSaveable { mutableStateOf(false) }
    var ausgewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var rechts by rememberSaveable { mutableStateOf(Rechts.DETAIL) }
    // Je Editor-Auftrag ein eigenes ViewModel: ein zweites „Neues Rezept“ beginnt leer
    var auftrag by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < ZWEISPALTIG_AB) {
            RezeptListeRoute(onOeffnen, onNeu, onImport = { importOffen = true }, vm = listeVm)
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(LISTENBREITE).fillMaxHeight()) {
                    RezeptListeRoute(
                        onOeffnen = { ausgewaehlt = it; rechts = Rechts.DETAIL },
                        onNeu = { auftrag++; rechts = Rechts.NEU },
                        onImport = { importOffen = true }, ausgewaehlt = ausgewaehlt, vm = listeVm,
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val id = ausgewaehlt
                    when {
                        rechts != Rechts.DETAIL -> EditorRoute(
                            id = id.takeIf { rechts == Rechts.BEARBEITEN },
                            onFertig = { neu -> ausgewaehlt = neu; rechts = Rechts.DETAIL },
                            onZurueck = { rechts = Rechts.DETAIL },
                            vm = hiltViewModel<EditorViewModel>(key = "editor-$auftrag"),
                        )
                        id != null -> RezeptDetailRoute(
                            id, onKochen, onBearbeiten = { auftrag++; rechts = Rechts.BEARBEITEN },
                            onDupliziert = { ausgewaehlt = it }, onZurueck = { ausgewaehlt = null }, vm = detailVm,
                        )
                        else -> Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
                            LeererZustandFlaeche(
                                Icons.AutoMirrored.Outlined.MenuBook, "Wähle ein Rezept", "Links ein Rezept antippen, um es hier zu sehen.",
                                aktionText = null, onAktion = null,
                            )
                        }
                    }
                }
            }
        }
    }
    if (importOffen) ImportBlatt(onSchliessen = { importOffen = false }, vm = importVm ?: hiltViewModel())
}
