package de.foody.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import javax.inject.Inject

/**
 * Geplante Mahlzeiten, bei denen der Nutzer auf „Gegessen?“ mit „Nein“ geantwortet hat: „Heute“ zeigt sie an diesem
 * Tag nicht mehr als Vorschlag. Nur auf diesem Gerät (Prefs `foody`, Schlüssel [Einstellungsschluessel.NICHT_GEGESSEN]),
 * je Eintrag `Datum|Plan-ID`; beim Merken fallen ältere Tage weg, die Liste bleibt klein.
 */
class NichtGegessenSpeicher @Inject constructor(@ApplicationContext kontext: Context) {
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    private fun alle(): Set<String> = prefs.getStringSet(Einstellungsschluessel.NICHT_GEGESSEN, null).orEmpty()

    /** Plan-IDs, die am [datum] als nicht gegessen gemerkt sind. */
    fun amTag(datum: LocalDate): Flow<Set<String>> = callbackFlow {
        val praefix = "$datum$TRENNER"
        fun senden() = trySend(alle().filter { it.startsWith(praefix) }.map { it.removePrefix(praefix) }.toSet())
        val hoerer = SharedPreferences.OnSharedPreferenceChangeListener { _, schluessel ->
            if (schluessel == Einstellungsschluessel.NICHT_GEGESSEN) senden()
        }
        prefs.registerOnSharedPreferenceChangeListener(hoerer)
        senden()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(hoerer) }
    }.distinctUntilChanged()

    /** Merkt [planEintragId] am [datum] als nicht gegessen und vergisst alles vor diesem Tag. */
    fun merken(datum: LocalDate, planEintragId: String) {
        val behalten = alle().filter { eintrag ->
            val tag = runCatching { LocalDate.parse(eintrag.substringBefore(TRENNER)) }.getOrNull()
            tag != null && !tag.isBefore(datum)
        }.toSet()
        prefs.edit().putStringSet(Einstellungsschluessel.NICHT_GEGESSEN, behalten + "$datum$TRENNER$planEintragId").apply()
    }
}

private const val TRENNER = '|'
