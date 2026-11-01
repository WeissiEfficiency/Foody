package de.foody.feature.einstellungen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten

/** Dateidialoge der Seite: Sicherung anlegen und Datei zum Wiederherstellen wählen (in Tests ersetzbar). */
class Dateiwahl(val erstellen: () -> Unit, val oeffnen: () -> Unit)

/** Die Dateidialoge des Systems (SAF): ZIP anlegen mit [dateiname], ZIP oder JSON öffnen; Abbrechen tut nichts. */
@Composable
fun rememberDateiwahl(dateiname: String, onErstellt: (Uri) -> Unit, onGewaehlt: (Uri) -> Unit): Dateiwahl {
    val anlegen = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(onErstellt) }
    val oeffnen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onGewaehlt) }
    return remember(anlegen, oeffnen, dateiname) {
        Dateiwahl(
            erstellen = { anlegen.launch(dateiname) },
            oeffnen = { oeffnen.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) },
        )
    }
}

/**
 * Einstellungen: Konto (→ „Server verbinden“), Darstellung mit Farbwelt, Tagesziel, Zutatenkatalog und Online-Produktsuche, Sicherung,
 * alle Daten löschen, Hinweise.
 * Wiederherstellen und Löschen fragen nach; bei verbundenem Sync sind beide gesperrt.
 */
@Composable
fun EinstellungenRoute(
    onServer: () -> Unit,
    onZutaten: () -> Unit,
    onZurueck: () -> Unit,
    vm: EinstellungenViewModel = hiltViewModel(),
    dateiwahl: @Composable (onErstellt: (Uri) -> Unit, onGewaehlt: (Uri) -> Unit) -> Dateiwahl = { erstellt, gewaehlt ->
        rememberDateiwahl(vm.dateiname(), erstellt, gewaehlt)
    },
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.meldungen.collect { snackbar.showSnackbar(it) } }
    // Datei, die nach der Rückfrage wiederhergestellt wird; als Text, damit sie Drehen übersteht
    var ersetzen by rememberSaveable { mutableStateOf<String?>(null) }
    var loeschen by rememberSaveable { mutableStateOf(false) }
    // Eigene Eingabe; bis zur ersten Eingabe zeigt das Feld das gespeicherte Ziel (schon im ersten Bild)
    var zielText by rememberSaveable { mutableStateOf<String?>(null) }
    // Ungültig ist sofort bekannt; gezeigt wird der Fehler erst nach „Fertig“ oder beim Verlassen des Feldes
    var zielUngueltig by rememberSaveable { mutableStateOf(false) }
    var zielFehler by rememberSaveable { mutableStateOf(false) }
    val dateien = dateiwahl({ vm.sicherungErstellen(it) }, { ersetzen = it.toString() })
    // Ohne „Fertig“ verlassen: die letzte gültige Eingabe trotzdem speichern
    val letzteEingabe by rememberUpdatedState(zielText)
    DisposableEffect(vm) { onDispose { letzteEingabe?.let(vm::tageszielSpeichern) } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                    .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
                    }
                    Text("Einstellungen", Modifier.ueberschrift(), style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
                }
                KontoAbschnitt(z.konto, System.currentTimeMillis(), onServer)
                DarstellungAbschnitt(z.darstellung, z.farbwelt, vm::darstellungSetzen, vm::farbweltSetzen)
                TageszielAbschnitt(
                    zielText ?: z.tagesziel?.toString().orEmpty(), zielFehler,
                    onFertig = {
                        zielFehler = zielUngueltig
                        zielText?.let(vm::tageszielSpeichern)
                    },
                ) { neu ->
                    zielText = neu
                    zielUngueltig = !vm.tageszielEingabe(neu)
                    if (!zielUngueltig) zielFehler = false
                }
                ZutatenAbschnitt(z.zutaten, z.onlineSuche, onZutaten, vm::onlineSucheSetzen)
                SicherungAbschnitt(z, onErstellen = dateien.erstellen, onWiederherstellen = dateien.oeffnen)
                DatenAbschnitt(z, onLoeschen = { loeschen = true })
                HinweiseAbschnitt()
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 88.dp))
    }

    ersetzen?.let { uri ->
        FoodyFrage(
            "Alle Daten ersetzen?", "Alle Daten auf diesem Gerät werden durch den Inhalt der Datei ersetzt.", "Ersetzen",
            onJa = { ersetzen = null; vm.wiederherstellen(uri.toUri()) }, onNein = { ersetzen = null },
        )
    }
    if (loeschen) {
        FoodyFrage(
            "Alle Daten löschen?",
            "Alle Rezepte, Pläne, Einkaufslisten, Tagebuch und Fotos auf diesem Gerät löschen? Der Startkatalog wird neu eingespielt.",
            "Löschen",
            onJa = { loeschen = false; vm.allesLoeschen() }, onNein = { loeschen = false },
        )
    }
}
