package de.foody.app3

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.foody.core.sync.SyncAusloeser
import javax.inject.Inject

/**
 * Anwendung der V3: WorkManager mit Hilt-Workern, alte v1-Aufträge beim Start abmelden, Sync anstoßen, sobald die
 * App in den Vordergrund kommt.
 */
@HiltAndroidApp
class FoodyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var ausloeser: SyncAusloeser

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ausloeser.beimStart()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    ausloeser.appImVordergrund()
                }
            },
        )
    }
}
