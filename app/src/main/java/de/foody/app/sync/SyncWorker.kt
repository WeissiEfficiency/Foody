package de.foody.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Führt einen Sync-Lauf im Hintergrund aus. Fachliche Zustände (abgemeldet, kein Haushalt, Protokoll-Konflikt)
 * stehen in `sync_state`; ein Wiederholen würde daran nichts ändern, daher `success`. Nur vorübergehende
 * Fehler (Netzwerk, 5xx, Drosselung) werden mit Backoff wiederholt.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engineFactory: SyncEngineFactory,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = engineFactory.create() ?: return Result.success()
        val start = System.currentTimeMillis()
        val outcome = engine.run()
        // Änderungen während des Laufs (z. B. Voll-Abgleich) gleich nachziehen, ohne den eigenen Auftrag abzubrechen.
        scheduler.requestSoonIfQueuedSince(start)
        return when (outcome) {
            is SyncOutcome.Success, SyncOutcome.Unauthorized, is SyncOutcome.ProtocolMismatch,
            SyncOutcome.NoHousehold,
            -> Result.success()
            is SyncOutcome.Failed -> if (outcome.transient) Result.retry() else Result.failure()
        }
    }
}
