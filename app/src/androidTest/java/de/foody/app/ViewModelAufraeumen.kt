package de.foody.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * Beendet alle Coroutinen eines ViewModels – in Tests vor `db.close()` aufrufen. Sonst fragen `stateIn`-Flows
 * (`WhileSubscribed(5_000)`) noch Sekunden lang die geschlossene Datenbank ab, und die Ausnahme schlägt
 * zufällig in einem späteren Test auf („connection pool has been closed“).
 * Wartet, bis alles beendet ist: Eine Room-Abfrage, die beim Abbrechen schon läuft, liefe sonst nach `db.close()`
 * weiter (so in der CI bei `TagebuchScreenTest.packungImReiterFrei`).
 */
fun ViewModel.aufraeumen() = runBlocking { viewModelScope.coroutineContext.job.cancelAndJoin() }

/** Scan ohne Play-Dienste für Tests, die das Tagebuch ohne Scan prüfen. */
fun ohneScan() = de.foody.app.scan.PackungScan(
    leser = { de.foody.app.scan.StrichcodeStatus.Abgebrochen }, tabelle = { de.foody.app.scan.ScanStatus.Fehler },
    suche = { de.foody.app.scan.ProduktSuche.Antwort.Unbekannt }, katalog = { null }, play = { false }, online = { false },
)
