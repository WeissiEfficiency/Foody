package de.foody.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Hell, dunkel oder wie das System. */
enum class Darstellung { SYSTEM, HELL, DUNKEL }

/**
 * Darstellung aus den Prefs `foody` (`themeMode`) mit den Werten der v1 (`SYSTEM`, `LIGHT`, `DARK`), damit sie das
 * Update überlebt; fehlend oder unbekannt = [Darstellung.SYSTEM].
 */
class DarstellungSpeicher @Inject constructor(@ApplicationContext kontext: Context) {
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    fun darstellung(): Flow<Darstellung> = callbackFlow {
        fun senden() = trySend(lesen())
        val hoerer = SharedPreferences.OnSharedPreferenceChangeListener { _, schluessel ->
            if (schluessel == Einstellungsschluessel.DARSTELLUNG) senden()
        }
        prefs.registerOnSharedPreferenceChangeListener(hoerer)
        senden()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(hoerer) }
    }.distinctUntilChanged()

    /** Der gespeicherte Wert sofort, etwa als Startwert vor dem ersten Wert von [darstellung]. */
    fun aktuell(): Darstellung = lesen()

    fun setzen(d: Darstellung) {
        val wert = when (d) {
            Darstellung.SYSTEM -> "SYSTEM"
            Darstellung.HELL -> "LIGHT"
            Darstellung.DUNKEL -> "DARK"
        }
        prefs.edit().putString(Einstellungsschluessel.DARSTELLUNG, wert).apply()
    }

    private fun lesen(): Darstellung = when (prefs.getString(Einstellungsschluessel.DARSTELLUNG, null)) {
        "LIGHT" -> Darstellung.HELL
        "DARK" -> Darstellung.DUNKEL
        else -> Darstellung.SYSTEM
    }
}
