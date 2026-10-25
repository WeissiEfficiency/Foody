package de.foody.feature.kochen.timer

import android.content.SharedPreferences
import androidx.core.content.edit
import de.foody.feature.kochen.KochenModul
import de.foody.feature.kochen.ZWOELF_STUNDEN_MS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Laufende und abgelaufene Kochtimer, gespeichert in den Einstellungen – sie überstehen einen Neustart der App.
 * Abgelaufene bleiben, bis man sie wegtippt, höchstens 12 Stunden nach ihrem Ende.
 */
@Singleton
class KochTimerSpeicher @Inject constructor(
    @param:Named(KochenModul.TIMER_PREFS) private val prefs: SharedPreferences,
    private val clock: Clock,
) {
    private val liste = MutableStateFlow(lesen())
    val timer: StateFlow<List<LaufenderTimer>> = liste.asStateFlow()

    /** Startet einen Timer und liefert seine ID; derselbe Timer zweimal läuft zweimal (z. B. zwei Bleche). */
    fun starten(rezeptId: String, portionen: Int, rezeptName: String, label: String, dauer: Duration): String {
        val neu = LaufenderTimer(UUID.randomUUID().toString(), rezeptId, portionen, rezeptName, label, clock.millis() + dauer.toMillis())
        aendern { it + neu }
        return neu.id
    }

    fun entfernen(id: String) = aendern { l -> l.filterNot { it.id == id } }

    fun gemeldet(id: String) = aendern { l -> l.map { if (it.id == id) it.copy(gemeldet = true) else it } }

    /** Ob der Timer-Dienst laufen muss: ein Timer läuft noch oder ist abgelaufen, aber noch nicht gemeldet. */
    fun brauchtDienst(): Boolean = clock.millis().let { jetzt -> liste.value.any { !it.abgelaufen(jetzt) || !it.gemeldet } }

    fun jetzt(): Long = clock.millis()

    /** `true` nur beim ersten Aufruf überhaupt (für einen einmaligen Hinweis). */
    fun hinweisEinmal(): Boolean {
        if (prefs.getBoolean(HINWEIS, false)) return false
        prefs.edit { putBoolean(HINWEIS, true) }
        return true
    }

    private fun aendern(f: (List<LaufenderTimer>) -> List<LaufenderTimer>) {
        liste.update(f)
        prefs.edit { putString(SCHLUESSEL, JSONArray(liste.value.map(::alsJson)).toString()) }
    }

    private fun lesen(): List<LaufenderTimer> {
        val json = prefs.getString(SCHLUESSEL, null) ?: return emptyList()
        val jetzt = clock.millis()
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map { ausJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList()).filter { jetzt - it.endeUm <= ZWOELF_STUNDEN_MS }
    }

    private fun alsJson(t: LaufenderTimer) = JSONObject()
        .put("id", t.id).put("rezeptId", t.rezeptId).put("portionen", t.portionen).put("rezeptName", t.rezeptName)
        .put("label", t.label).put("endeUm", t.endeUm).put("gemeldet", t.gemeldet)

    private fun ausJson(o: JSONObject) = LaufenderTimer(
        o.getString("id"), o.getString("rezeptId"), o.getInt("portionen"), o.getString("rezeptName"), o.getString("label"),
        o.getLong("endeUm"), o.optBoolean("gemeldet"),
    )

    private companion object {
        const val SCHLUESSEL = "timer"
        const val HINWEIS = "hinweis_ohne_benachrichtigung"
    }
}
