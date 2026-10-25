package de.foody.core.data.einkauf

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Plan-Artikel, die der Abgleich einer Liste nicht mehr vorschlägt: in der Vorschau abgewählt, im Abgleich abgewählt
 * oder von Hand gelöscht. Nur lokal (Datei `einkauf`, je Liste `ignoriert_<listeId>`) – ein anderes Gerät kennt sie nicht.
 */
class IgnorierteSchluessel(private val prefs: SharedPreferences) {
    /** Zählt jedes Schreiben hoch, damit [fluss] neu liest. */
    private val stand = MutableStateFlow(0)

    fun lesen(listeId: String): Set<String> = prefs.getStringSet(schluessel(listeId), null).orEmpty().toSet()

    fun fluss(listeId: String): Flow<Set<String>> = stand.map { lesen(listeId) }.distinctUntilChanged()

    fun hinzufuegen(listeId: String, schluessel: Collection<String>) {
        if (schluessel.isEmpty()) return
        schreiben(listeId, lesen(listeId) + schluessel)
    }

    fun entfernen(listeId: String, schluessel: String) = schreiben(listeId, lesen(listeId) - schluessel)

    fun vergessen(listeId: String) {
        prefs.edit().remove(schluessel(listeId)).commit()
        stand.value++
    }

    private fun schreiben(listeId: String, neu: Set<String>) {
        // Eine neue Menge schreiben: SharedPreferences verlangt, die gelesene Menge nicht zu verändern
        prefs.edit().putStringSet(schluessel(listeId), HashSet(neu)).commit()
        stand.value++
    }

    private fun schluessel(listeId: String) = "ignoriert_$listeId"
}
