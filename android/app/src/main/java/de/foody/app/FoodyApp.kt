package de.foody.app

import de.foody.app.data.RecipePhotoStore
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.sync.SyncScheduler
import de.foody.app.data.repo.IngredientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FoodyApp : Application(), Configuration.Provider {
    @Inject lateinit var ingredients: IngredientRepository
    @Inject lateinit var photos: RecipePhotoStore
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var db: FoodyDatabase

    // Der Standard-Initializer von WorkManager ist im Manifest entfernt; hier kommt die Hilt-Worker-Factory dazu.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            ingredients.seedIfNeeded()
            // Fotos aus älteren Versionen wurden in voller Kameraauflösung gespeichert – einmalig verkleinern
            photos.shrinkAllEinmal()
        }
        // Hintergrund-Sync nur planen, wenn er eingeschaltet ist; sonst bleibt alles aus.
        syncScope.launch {
            if (db.syncDao().getState()?.active == true) {
                syncScheduler.schedulePeriodic()
                syncScheduler.startObservingOutbox(syncScope)
            }
        }
    }
}
