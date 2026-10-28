package de.foody.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.sync.SyncAusloeser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Anwendung der V3: WorkManager mit Hilt-Workern, alte v1-Aufträge beim Start abmelden, Startdaten laden, Sync
 * anstoßen, sobald die App in den Vordergrund kommt.
 */
@HiltAndroidApp
class FoodyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var ausloeser: SyncAusloeser
    @Inject lateinit var startdaten: StartdatenLader

    /** Lebt so lange wie der Prozess; Fehler eines Auftrags beenden die anderen nicht. */
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ausloeser.beimStart()
        bereich.launch { startdaten.ladenWennNoetig() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    ausloeser.appImVordergrund()
                }
            },
        )
    }
}
