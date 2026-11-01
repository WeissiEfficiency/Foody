package de.foody.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.designsystem.AppOffen
import de.foody.core.sync.SyncAusloeser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Anwendung der V3: WorkManager mit Hilt-Workern, alte v1-Aufträge beim Start abmelden, Startdaten laden. Ob die App
 * offen ist, gibt sie an Bildschirmzustände ([AppOffen]) und Sync weiter: Beim Öffnen wird synchronisiert, nach dem
 * Schließen läuft nichts mehr (außer einem Kochtimer).
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
                    AppOffen.setzen(true)
                    ausloeser.appImVordergrund()
                }

                override fun onStop(owner: LifecycleOwner) {
                    AppOffen.setzen(false)
                    ausloeser.appImHintergrund()
                }
            },
        )
    }
}
