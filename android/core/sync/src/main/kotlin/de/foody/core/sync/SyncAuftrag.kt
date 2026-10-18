package de.foody.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Ein Sync-Lauf im Hintergrund (eingeplant vom [SyncAusloeser]). Wie der Worker der v1: Nur vorübergehende Fehler
 * (Netz, `5xx`, Drosselung, Cursor) werden mit Backoff wiederholt; dauerhafte Zustände (abgemeldet, Versionen) stehen in
 * `sync_state.lastError`, ein Wiederholen änderte nichts – daher `success`. Inaktiver Sync: `success`.
 */
@HiltWorker
class SyncAuftrag @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val lauf: SyncLauf,
    private val ausloeser: SyncAusloeser,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ausloeser.auftragBeginnt()
        val ergebnis = try {
            lauf.ausfuehren()
        } catch (e: Throwable) {
            // Abbruch (z. B. Trennen): abmelden; geplant wird dann nur, was während des Laufs ausgelöst wurde.
            withContext(NonCancellable) { ausloeser.nachLauf(null) }
            throw e
        }
        ausloeser.nachLauf(ergebnis)
        return when (ergebnis) {
            is SyncErgebnis.Fehlgeschlagen -> if (ergebnis.voruebergehend) Result.retry() else Result.success()
            SyncErgebnis.Inaktiv, is SyncErgebnis.Fertig -> Result.success()
        }
    }
}
