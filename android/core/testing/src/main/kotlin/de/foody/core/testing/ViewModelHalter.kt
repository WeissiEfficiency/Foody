package de.foody.core.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * Hält die ViewModels eines Tests wie ein Bildschirm. [schliessen] gehört in `@After` vor `db.close()`: Es beendet die
 * `viewModelScope`s. Sonst fragt ein Flow, den eine letzte Änderung angestoßen hat, die schon geschlossene
 * In-Memory-Datenbank ab (IllegalStateException aus dem SQLiteConnectionPool, oft erst im nächsten Test).
 */
class ViewModelHalter {
    private val store = ViewModelStore()
    private var anzahl = 0

    /** Nimmt [vm] auf und gibt es zurück: `val vm = halter.behalten(MeinViewModel(…))`. */
    fun <T : ViewModel> behalten(vm: T): T {
        val fabrik = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = vm as V
        }
        return ViewModelProvider(store, fabrik)["vm${anzahl++}", vm.javaClass]
    }

    /** Beendet alle aufgenommenen ViewModels (wie beim Verlassen des Bildschirms). */
    fun schliessen() = store.clear()
}
