package de.foody.app.sync

import android.content.Context
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
) {
    private val httpClient: HttpClient by lazy { defaultHttpClient() }
    private var cached: Pair<String, SyncEngine>? = null

    @Synchronized
    private fun engineFor(url: String): SyncEngine {
        cached?.takeIf { it.first == url }?.let { return it.second }
        val api = KtorSyncApi(url, httpClient) { tokenStore.load() }
        return SyncEngine(db, store, applier, api, Clock.systemUTC()).also { cached = url to it }
    }

    open suspend fun create(): SyncEngine? {
        val state = db.syncDao().getState()
        val url = state?.serverUrl
        if (state == null || !state.active || url == null) return null
        if (tokenStore.load() == null) return null
        return engineFor(url)
    }
}

/** Plant den Hintergrund-Sync (WorkManager, nur mit Netzwerk). */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FoodyDatabase,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Regelmäßiger Abgleich; ein bereits geplanter Auftrag bleibt unverändert. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Gleich (nach kurzer Verzögerung) abgleichen. Weitere Anfragen ersetzen den wartenden Auftrag und
     * verschieben ihn damit (Entprellen vieler Änderungen hintereinander).
     */
    fun requestSoon() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
    }

    /**
     * Beobachtet die Outbox und fordert bei Zuwachs einen Abgleich an (nur bei aktivem Sync). Ein Rückgang
     * (Entfernen nach dem Push) löst nichts aus, sonst gäbe es eine Schleife. Der erste Wert zählt als Zuwachs
     * gegenüber 0: Was beim Start noch offen ist, wird zeitnah gesendet.
     */
    fun startObservingOutbox(scope: CoroutineScope) {
        scope.launch {
            var previous = 0
            db.syncDao().observeOutboxCount().distinctUntilChanged().collect { count ->
                val increased = count > previous
                previous = count
                if (increased && db.syncDao().getState()?.active == true) requestSoon()
            }
        }
    }

    companion object {
        const val PERIODIC_WORK = "foody-sync-periodic"
        const val NOW_WORK = "foody-sync-now"
        private const val PERIOD_HOURS = 1L
        private const val DELAY_SECONDS = 5L
    }
}
