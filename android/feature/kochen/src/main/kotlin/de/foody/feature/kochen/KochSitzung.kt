package de.foody.feature.kochen

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock

/** Sitzung und abgelaufene Timer gelten so lange ab der letzten Änderung. */
const val ZWOELF_STUNDEN_MS = 43_200_000L

/** Wo man beim Kochen eines Rezepts steht; [abgehakt] sind Zeilenindizes der Seite „Bereitlegen“. */
data class KochSitzung(val rezeptId: String, val portionen: Int, val seite: Int, val abgehakt: Set<Int>, val geaendertAm: Long)

/** Eine Sitzung je Rezept, 12 Stunden fortsetzbar; danach beim Lesen verworfen. */
class KochSitzungSpeicher(private val prefs: SharedPreferences, private val clock: Clock) {
    fun lesen(rezeptId: String): KochSitzung? {
        val json = prefs.getString(schluessel(rezeptId), null) ?: return null
        val s = runCatching { ausJson(rezeptId, JSONObject(json)) }.getOrNull()
        if (s == null || clock.millis() - s.geaendertAm > ZWOELF_STUNDEN_MS) {
            loeschen(rezeptId)
            return null
        }
        return s
    }

    fun schreiben(s: KochSitzung) = prefs.edit {
        putString(
            schluessel(s.rezeptId),
            JSONObject()
                .put("portionen", s.portionen)
                .put("seite", s.seite)
                .put("abgehakt", JSONArray(s.abgehakt.sorted()))
                .put("geaendertAm", s.geaendertAm)
                .toString(),
        )
    }

    fun loeschen(rezeptId: String) = prefs.edit { remove(schluessel(rezeptId)) }

    private fun schluessel(rezeptId: String) = "sitzung_$rezeptId"

    private fun ausJson(rezeptId: String, o: JSONObject): KochSitzung {
        val haken = o.getJSONArray("abgehakt")
        return KochSitzung(
            rezeptId, o.getInt("portionen"), o.getInt("seite"), (0 until haken.length()).map(haken::getInt).toSet(),
            o.getLong("geaendertAm"),
        )
    }
}
