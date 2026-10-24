package de.foody.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Tagesziel in kcal aus den Prefs `foody` (`dailyKcalGoal`, wie v1); 0 oder fehlend = kein Ziel. */
class TageszielSpeicher @Inject constructor(@ApplicationContext kontext: Context) {
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    fun tagesziel(): Flow<Int?> = callbackFlow {
        fun senden() = trySend(prefs.getInt(Einstellungsschluessel.TAGESZIEL, 0).takeIf { it > 0 })
        val hoerer = SharedPreferences.OnSharedPreferenceChangeListener { _, schluessel ->
            if (schluessel == Einstellungsschluessel.TAGESZIEL) senden()
        }
        prefs.registerOnSharedPreferenceChangeListener(hoerer)
        senden()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(hoerer) }
    }.distinctUntilChanged()
}
