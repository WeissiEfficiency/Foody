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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.VerwerfenFrage
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.importieren.ImportBlatt
import de.foody.feature.rezepte.importieren.ImportViewModel
import de.foody.feature.rezepte.liste.RezeptListeRoute
import de.foody.feature.rezepte.liste.RezeptListeViewModel

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
    /** „Ansehen“ nach „Auf die Einkaufsliste“ im rechts eingebetteten Detail (Tablet). */
    onEinkauf: () -> Unit = {},
    listeVm: RezeptListeViewModel = hiltViewModel(),
    detailVm: RezeptDetailViewModel = hiltViewModel(),
    importVm: ImportViewModel? = null,
    /** Nur für Tests: der Editor ohne Hilt. */
    editorVm: EditorViewModel? = null,
) {
    var importOffen by rememberSaveable { mutableStateOf(false) }
    var ausgewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var rechts by rememberSaveable { mutableStateOf(Rechts.DETAIL) }
    // Ein Editor-ViewModel für alle Aufträge (fester Schlüssel, sonst bliebe je Auftrag eines liegen); ein neuer Auftrag
    // beginnt darin frisch, ein zweites „Neues Rezept“ also leer
    var auftrag by rememberSaveable { mutableIntStateOf(0) }
    // Wechsel links, der wartet, bis „Änderungen verwerfen?“ beantwortet ist
    var wartenderWechsel by remember { mutableStateOf<(() -> Unit)?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < FoodyBreiten.zweispaltig) {
            RezeptListeRoute(onOeffnen, onNeu, onImport = { importOffen = true }, vm = listeVm)
        } else {
            val editor = if (rechts == Rechts.DETAIL) {
                null
            } else {
                editorVm ?: hiltViewModel<EditorViewModel>(key = "editor")
            }
            val ungespeichert = editor?.geaendert?.collectAsStateWithLifecycle()?.value == true
            val wechseln = { aktion: () -> Unit -> if (ungespeichert) wartenderWechsel = aktion else aktion() }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(LISTENBREITE).fillMaxHeight()) {
                    RezeptListeRoute(
                        onOeffnen = { neu -> wechseln { ausgewaehlt = neu; rechts = Rechts.DETAIL } },
                        onNeu = { wechseln { auftrag++; rechts = Rechts.NEU } },
                        onImport = { importOffen = true }, ausgewaehlt = ausgewaehlt, vm = listeVm,
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val id = ausgewaehlt
                    when {
                        editor != null -> EditorRoute(
                            id = id.takeIf { rechts == Rechts.BEARBEITEN },
                            onFertig = { neu -> ausgewaehlt = neu; rechts = Rechts.DETAIL },
                            onZurueck = { rechts = Rechts.DETAIL },
                            auftrag = auftrag,
                            vm = editor,
                        )
                        id != null -> RezeptDetailRoute(
                            id, onKochen, onBearbeiten = { auftrag++; rechts = Rechts.BEARBEITEN },
                            onDupliziert = { ausgewaehlt = it }, onZurueck = { ausgewaehlt = null }, onEinkauf = onEinkauf, vm = detailVm,
                        )
                        else -> Box(Modifier.fillMaxSize().safeDrawingPadding().padding(FoodyAbstaende.s6), contentAlignment = Alignment.Center) {
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
    wartenderWechsel?.let { aktion ->
        VerwerfenFrage(onVerwerfen = { wartenderWechsel = null; aktion() }, onWeiter = { wartenderWechsel = null })
    }
    if (importOffen) ImportBlatt(onSchliessen = { importOffen = false }, vm = importVm ?: hiltViewModel())
}
