package de.foody.feature.heute.tagebuch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import kotlinx.coroutines.launch

/** Test-Tag des Speichern-Knopfs (die Überschrift heißt auch „Eintragen“). */
const val EINTRAGEN_SPEICHERN = "eintragen-speichern"

/**
 * Eintragen als eigene Seite, in allen Stilen gleich aufgebaut: erst [Erfassen] (Foto, Scannen, Suche, Geplantes), dann
 * [Bestaetigen] mit dem Knopf „Eintragen“ fest unten. Zurück führt von Bestätigen nach Erfassen und von dort hinaus
 * ([onFertig]), ebenso erfolgreiches Speichern. Mit [EintragenStart.sofort] öffnet sie gleich Kamera bzw. Scanner und
 * bleibt bis zum Ergebnis leer; ein Abbruch schließt sie. [auftrag] unterscheidet ein neues Öffnen vom Neuaufbau.
 */
@Composable
fun EintragenRoute(start: EintragenStart, auftrag: String, onFertig: () -> Unit, vm: EintragenViewModel = hiltViewModel()) {
    // Zuerst und immer: Die Launcher müssen stehen, auch solange die Seite leer ist (Kameraergebnis nach Prozessende)
    val aufnahme = rememberAufnahme(vm)
    LaunchedEffect(auftrag) { vm.oeffnen(start.datum, start.mahlzeit, auftrag = auftrag, sofort = start.sofort) }
    val fertig by rememberUpdatedState(onFertig)
    // Nach einem abgebrochenen Sofort-Start bleibt die Seite leer, bis sie zu ist: Erfassen blitzt nicht auf
    var schliesst by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.schliessen.collect {
            schliesst = true
            fertig()
        }
    }
    val zurueck = { if (!vm.zurueck()) fertig() }
    BackHandler(onBack = zurueck)

    val z by vm.zustand.collectAsStateWithLifecycle()
    val moeglich by vm.speichernMoeglich.collectAsStateWithLifecycle()
    val e = z.eingaben
    // Vor dem ersten Zustand (Startwert, noch nicht geöffnet) gilt der Start, damit Erfassen auch dann nicht aufblitzt
    val sofort = if (e.geoeffnet) e.sofort else start.sofort
    if (schliesst || (sofort != null && e.schritt == EintragenSchritt.ERFASSEN)) {
        Box(Modifier.fillMaxSize())
        return
    }

    val bereich = rememberCoroutineScope()
    val erfassenScroll = rememberScrollState()
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = FoodyBreiten.inhalt)) {
            val auswahl = z.auswahlName.takeIf { e.schritt == EintragenSchritt.BESTAETIGEN }
            // Das Stilwort liest TalkBack als „Eintragen“, den Namen der Wahl so, wie er dasteht
            Seitenkopf(
                auswahl ?: eintragenTitel(),
                Modifier.fillMaxWidth().padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2),
                onZurueck = zurueck,
                beschreibung = if (auswahl == null) "Eintragen" else null,
            )
            when (e.schritt) {
                EintragenSchritt.ERFASSEN -> Schritt(erfassenScroll) { Erfassen(z, vm, aufnahme) }
                // Eigener Bildlauf je Bestätigen: Es beginnt immer oben
                EintragenSchritt.BESTAETIGEN -> Schritt(rememberScrollState()) { Bestaetigen(z, vm, aufnahme) }
            }
            if (e.schritt == EintragenSchritt.BESTAETIGEN) {
                FoodyKnopf(
                    "Eintragen",
                    { bereich.launch { if (vm.speichern() != null) fertig() } },
                    Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4).padding(bottom = FoodyAbstaende.s3).testTag(EINTRAGEN_SPEICHERN),
                    aktiv = moeglich,
                )
            }
        }
    }
}

/** Der Inhalt eines Schritts: füllt den Platz zwischen Kopf und Knopf und läuft darin. */
@Composable
private fun ColumnScope.Schritt(scroll: ScrollState, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3),
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        content = inhalt,
    )
}
