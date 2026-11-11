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

/** Gespeicherte Wahl unter Einstellungen › Darstellung › Stil, als Namen; `null` = nicht gewählt. */
data class StilWahl(val stil: String? = null, val variante: String? = null, val akzent: String? = null)

/**
 * Darstellung aus den Prefs `foody` (`themeMode`) mit den Werten der v1 (`SYSTEM`, `LIGHT`, `DARK`), damit sie das
 * Update überlebt; fehlend oder unbekannt = [Darstellung.SYSTEM]. Dazu die gewählte Farbwelt (`farbwelt`) als Name;
 * was der Name bedeutet, weiß das Designsystem (`Farbwelt.ausName`). Ebenso Stil, Variante und Akzent ([StilWahl]).
 */
class DarstellungSpeicher @Inject constructor(@ApplicationContext kontext: Context) {
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    fun darstellung(): Flow<Darstellung> = beobachten(Einstellungsschluessel.DARSTELLUNG, ::lesen)

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

    /** Name der gewählten Farbwelt; `null`, solange keine gewählt ist. */
    fun farbwelt(): Flow<String?> = beobachten(Einstellungsschluessel.FARBWELT, ::farbweltAktuell)

    fun farbweltAktuell(): String? = prefs.getString(Einstellungsschluessel.FARBWELT, null)

    fun farbweltSetzen(name: String) {
        prefs.edit().putString(Einstellungsschluessel.FARBWELT, name).apply()
    }

    /** Gewählter Stil, Variante und Akzent als Namen; `null` = nichts gewählt. Was sie bedeuten, weiß das Designsystem. */
    fun stil(): Flow<StilWahl> = beobachten(STIL_SCHLUESSEL, ::stilAktuell)

    fun stilAktuell(): StilWahl = StilWahl(
        stil = prefs.getString(Einstellungsschluessel.STIL, null),
        variante = prefs.getString(Einstellungsschluessel.STIL_VARIANTE, null),
        akzent = prefs.getString(Einstellungsschluessel.STIL_AKZENT, null),
    )

    fun stilSetzen(name: String) = schreiben(Einstellungsschluessel.STIL, name)

    fun stilVarianteSetzen(name: String) = schreiben(Einstellungsschluessel.STIL_VARIANTE, name)

    fun stilAkzentSetzen(name: String) = schreiben(Einstellungsschluessel.STIL_AKZENT, name)

    private fun schreiben(schluessel: String, wert: String) {
        prefs.edit().putString(schluessel, wert).apply()
    }

    private fun <T> beobachten(schluessel: String, lesen: () -> T): Flow<T> = beobachten(setOf(schluessel), lesen)

    private fun <T> beobachten(schluessel: Set<String>, lesen: () -> T): Flow<T> = callbackFlow {
        fun senden() = trySend(lesen())
        val hoerer = SharedPreferences.OnSharedPreferenceChangeListener { _, geaendert ->
            if (geaendert in schluessel) senden()
        }
        prefs.registerOnSharedPreferenceChangeListener(hoerer)
        senden()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(hoerer) }
    }.distinctUntilChanged()

    private companion object {
        val STIL_SCHLUESSEL = setOf(Einstellungsschluessel.STIL, Einstellungsschluessel.STIL_VARIANTE, Einstellungsschluessel.STIL_AKZENT)
    }

    private fun lesen(): Darstellung = when (prefs.getString(Einstellungsschluessel.DARSTELLUNG, null)) {
        "LIGHT" -> Darstellung.HELL
        "DARK" -> Darstellung.DUNKEL
        else -> Darstellung.SYSTEM
    }
}
