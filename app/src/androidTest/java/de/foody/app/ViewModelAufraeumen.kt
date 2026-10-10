package de.foody.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

/**
 * Beendet alle Coroutinen eines ViewModels – in Tests vor `db.close()` aufrufen. Sonst fragen `stateIn`-Flows
 * (`WhileSubscribed(5_000)`) noch Sekunden lang die geschlossene Datenbank ab, und die Ausnahme schlägt
 * zufällig in einem späteren Test auf („connection pool has been closed“).
 */
fun ViewModel.aufraeumen() = viewModelScope.cancel()

/** Scan ohne Play-Dienste für Tests, die das Tagebuch ohne Scan prüfen. */
fun ohneScan() = de.foody.app.scan.PackungScan(
    leser = { de.foody.app.scan.StrichcodeStatus.Abgebrochen }, tabelle = { de.foody.app.scan.ScanStatus.Fehler },
    suche = { de.foody.app.scan.ProduktSuche.Antwort.Unbekannt }, katalog = { null }, play = { false }, online = { false },
)
