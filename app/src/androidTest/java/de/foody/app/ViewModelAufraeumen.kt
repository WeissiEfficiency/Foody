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
