package de.foody.core.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Hält die ViewModels eines Tests wie ein Bildschirm. [schliessen] gehört in `@After` vor `db.close()`: Es beendet die
 * `viewModelScope`s. Sonst fragt ein Flow, den eine letzte Änderung angestoßen hat, die schon geschlossene
 * In-Memory-Datenbank ab (IllegalStateException aus dem SQLiteConnectionPool, oft erst im nächsten Test).
 */
class ViewModelHalter {
    private val store = ViewModelStore()
    private var anzahl = 0
    private var zuseher: CoroutineScope? = null

    /** Nimmt [vm] auf und gibt es zurück: `val vm = halter.behalten(MeinViewModel(…))`. */
    fun <T : ViewModel> behalten(vm: T): T {
        val fabrik = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = vm as V
        }
        return ViewModelProvider(store, fabrik)["vm${anzahl++}", vm.javaClass]
    }

    /**
     * Sammelt [flows] auf `Dispatchers.Main.immediate`, wie es die sichtbare Oberfläche tut. Zustände mit `SolangeSichtbar`
     * rechnen nur, solange jemand zusieht; ein Test, der `.value` liest, muss deshalb erst zusehen.
     */
    fun zusehen(vararg flows: Flow<*>) {
        val scope = zuseher ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { zuseher = it }
        flows.forEach { flow -> scope.launch { flow.collect {} } }
    }

    /** Beendet das Zusehen und alle aufgenommenen ViewModels (wie beim Verlassen des Bildschirms). */
    fun schliessen() {
        zuseher?.cancel()
        zuseher = null
        store.clear()
    }
}
