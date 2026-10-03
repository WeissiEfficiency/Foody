package de.foody.app

import de.foody.app.data.RecipePhotoStore
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.foody.app.data.repo.IngredientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FoodyApp : Application() {
    @Inject lateinit var ingredients: IngredientRepository
    @Inject lateinit var photos: RecipePhotoStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            ingredients.seedIfNeeded()
            // Fotos aus älteren Versionen wurden in voller Kameraauflösung gespeichert – einmalig verkleinern
            photos.shrinkAll()
        }
    }
}
