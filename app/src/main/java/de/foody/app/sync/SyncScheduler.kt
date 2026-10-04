package de.foody.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.FoodyDatabase
import io.ktor.client.HttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import de.foody.app.data.RecipePhotoStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Liefert die [SyncEngine] für den aktuellen Sync-Zustand: `null`, wenn der Sync inaktiv ist oder URL bzw. Token
 * fehlen. Solange die Server-URL gleich bleibt, kommt dieselbe Instanz zurück, sodass ihr Mutex Läufe des
 * Workers und späterer „Jetzt synchronisieren“-Aufrufe hintereinander schaltet. (Das Token liest die API je
 * Aufruf aus dem [TokenStore], ein neues Token braucht keine neue Engine.)
 */
@Singleton
open class SyncEngineFactory @Inject constructor(
    private val db: FoodyDatabase,
    private val store: SyncLocalStore,
    private val applier: SyncApplier,
    private val tokenStore: TokenStore,
    private val photoIndex: PhotoIndex,
    private val photoStore: RecipePhotoStore,
) {
    /** Läuft gerade ein Sync-Lauf der aktuellen Engine? */
    open val isSyncRunning: Boolean get() = cached?.second?.isRunning == true

    private val httpClient: HttpClient by lazy { defaultHttpClient() }
    @Volatile private var cached: Pair<String, SyncEngine>? = null

    @Synchronized
    private fun engineFor(url: String): SyncEngine {
        cached?.takeIf { it.first == url }?.let { return it.second }
        val api = KtorSyncApi(url, httpClient) { tokenStore.load() }
        return SyncEngine(db, store, applier, api, Clock.systemUTC(), photoIndex, photoStore).also { cached = url to it }
    }

    open suspend fun create(): SyncEngine? {
        val state = db.syncDao().getState()
        val url = state?.serverUrl
        if (state == null || !state.active || url == null) return null
        if (tokenStore.load() == null) {
            // Aktiv, aber ohne Token (nicht entschlüsselbar, Gerätewechsel): sichtbar als abgemeldet markieren.
            if (state.lastError != "unauthorized") db.syncDao().upsertState(state.copy(lastError = "unauthorized"))
            return null
        }
        return engineFor(url)
    }
}

/** Plant den Hintergrund-Sync (WorkManager, nur mit Netzwerk). */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FoodyDatabase,
    private val engineFactory: SyncEngineFactory,
) {
    private var observeJob: Job? = null
    private val workManager get() = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Regelmäßiger Abgleich; ein bereits geplanter Auftrag bleibt unverändert. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Gleich (nach kurzer Verzögerung) abgleichen. Weitere Anfragen ersetzen den wartenden Auftrag und
     * verschieben ihn damit (Entprellen vieler Änderungen hintereinander). Mit [afterRun] (aus dem laufenden
     * Worker heraus) wird stattdessen hinter den laufenden Auftrag gehängt, damit er sich nicht selbst abbricht.
     */
    fun requestSoon(afterRun: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        val policy = if (afterRun) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(NOW_WORK, policy, request)
    }

    /**
     * Nach einem Lauf: Wurde während des Laufs (ab [runStart], ms) etwas vorgemerkt, gleich noch einmal abgleichen.
     * Einträge, die nur stehengeblieben sind (älter als der Lauf), lösen nichts aus.
     */
    suspend fun requestSoonIfQueuedSince(runStart: Long) {
        if (db.syncDao().hasQueuedSince(runStart)) requestSoon(afterRun = true)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
    }

    /**
     * Beobachtet die Outbox und fordert bei Zuwachs einen Abgleich an (nur bei aktivem Sync). Ein Rückgang
     * (Entfernen nach dem Push) löst nichts aus, sonst gäbe es eine Schleife. Ein zweiter Aufruf bei aktivem Collector tut nichts. Der erste Wert zählt als Zuwachs
     * gegenüber 0: Was beim Start noch offen ist, wird zeitnah gesendet.
     */
    @Synchronized
    fun startObservingOutbox(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            var previous = 0
            db.syncDao().observeOutboxCount().distinctUntilChanged().collect { count ->
                val increased = count > previous
                previous = count
                // Während eines Laufs vermerkt der Worker Änderungen selbst (requestSoonIfQueuedSince).
                if (increased && !engineFactory.isSyncRunning && db.syncDao().getState()?.active == true) requestSoon()
            }
        }
    }

    companion object {
        const val PERIODIC_WORK = "foody-sync-periodic"
        const val NOW_WORK = "foody-sync-now"
        private const val PERIOD_MINUTES = 15L
        private const val BACKOFF_SECONDS = 30L
        private const val DELAY_SECONDS = 5L
    }
}
