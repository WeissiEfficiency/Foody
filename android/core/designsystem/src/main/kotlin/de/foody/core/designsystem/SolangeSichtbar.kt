package de.foody.core.designsystem

import kotlinx.coroutines.flow.SharingStarted

/**
 * Für `stateIn`/`shareIn` in ViewModels: Ein Bildschirmzustand läuft, solange jemand zusieht, und noch 5 s danach.
 * Drehen und kurze Wechsel starten nichts neu; ein geschlossener Bildschirm rechnet im Hintergrund nicht weiter.
 */
val SolangeSichtbar: SharingStarted = SharingStarted.WhileSubscribed(5_000L)
