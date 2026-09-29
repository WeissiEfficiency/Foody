package de.foody.app

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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { ingredients.seedIfNeeded() }
    }
}
