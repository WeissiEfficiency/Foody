package de.foody.core.erkennung

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.domain.FotoErkennung
import de.foody.domain.FotoPortion
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Gedächtnis eigener Korrekturen (Phase 6, Regel 29): Was nach einem Foto gespeichert wurde, liegt unter dem Namen, den
 * das Modell genannt hat. Nennt es denselben Namen wieder, schlägt das Blatt diese Werte statt der Schätzung vor.
 * Höchstens [MAX_EINTRAEGE], der älteste fällt weg. Dazu für die Abnahme (Phase 5) ein Protokoll je Speichern:
 * Vorschlag gegen Gespeichertes. Beides liegt nur auf diesem Gerät (`filesDir/fotoerkennung/`), nicht gesynct, nicht in
 * der Sicherung; eine kaputte Datei zählt als leer.
 */
@Singleton
class FotoGedaechtnis(
    private val ordner: File,
    private val io: CoroutineContext = Dispatchers.IO,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "fotoerkennung"))

    private data class Eintrag(val werte: FotoVorschlag, val zeit: Long)

    private val datei = File(ordner, "gedaechtnis.json")
    private val protokoll = File(ordner, "protokoll.jsonl")
    private val sperre = Mutex()
    private var eintraege: MutableMap<String, Eintrag>? = null

    /** Gespeicherte Werte zum Modellnamen [name] (bereinigt verglichen); `null`: noch nie gespeichert. */
    suspend fun nachschlagen(name: String): FotoVorschlag? {
        if (name.isBlank()) return null
        return withContext(io) { sperre.withLock { geladen()[FotoErkennung.schluessel(name)]?.werte } }
    }

    /**
     * Merkt [gespeichert] unter dem Namen aus [modell] (ersetzt einen älteren Wert) und hängt eine Zeile ans Protokoll.
     * Ohne Modellnamen wird nichts gemerkt, protokolliert aber schon. Schreibfehler gehen verloren statt das
     * Speichern im Tagebuch zu stören.
     */
    suspend fun merken(modell: FotoVorschlag, ausGedaechtnis: Boolean, portion: FotoPortion, gespeichert: FotoVorschlag) = withContext(io) {
        sperre.withLock {
            val jetzt = uhr()
            try {
                ordner.mkdirs()
                if (modell.name.isNotBlank()) {
                    val alle = geladen()
                    alle[FotoErkennung.schluessel(modell.name)] = Eintrag(gespeichert, jetzt)
                    while (alle.size > MAX_EINTRAEGE) alle.remove(alle.minBy { it.value.zeit }.key)
                    schreiben(alle)
                }
                protokollieren(
                    JSONObject()
                        .put("zeit", jetzt)
                        .put("vorschlag", modell.json())
                        .put("ausGedaechtnis", ausGedaechtnis)
                        .put("portion", portion.name)
                        .put("gespeichert", gespeichert.json()),
                )
            } catch (_: IOException) {
                // Nur ein Vorschlag fürs nächste Mal; der Tagebucheintrag ist schon gespeichert
            }
        }
    }

    private fun geladen(): MutableMap<String, Eintrag> = eintraege ?: lesen().also { eintraege = it }

    private fun lesen(): MutableMap<String, Eintrag> {
        val alle = mutableMapOf<String, Eintrag>()
        val text = try {
            datei.takeIf(File::isFile)?.readText() ?: return alle
        } catch (_: IOException) {
            return alle
        }
        try {
            val o = JSONObject(text).getJSONObject("eintraege")
            for (schluessel in o.keys()) {
                val e = o.getJSONObject(schluessel)
                alle[schluessel] = Eintrag(vorschlag(e.getJSONObject("werte")), e.getLong("zeit"))
            }
        } catch (_: JSONException) {
            return mutableMapOf()
        } catch (_: NumberFormatException) {
            return mutableMapOf()
        }
        return alle
    }

    private fun schreiben(alle: Map<String, Eintrag>) {
        val o = JSONObject()
        for ((schluessel, e) in alle) o.put(schluessel, JSONObject().put("werte", e.werte.json()).put("zeit", e.zeit))
        val teil = File(ordner, "gedaechtnis.json.teil")
        teil.writeText(JSONObject().put("eintraege", o).toString())
        if (!teil.renameTo(datei)) {
            datei.delete()
            if (!teil.renameTo(datei)) throw IOException("Gedächtnis nicht geschrieben")
        }
    }

    private fun protokollieren(zeile: JSONObject) {
        protokoll.appendText(zeile.toString() + "\n")
        val zeilen = protokoll.readLines()
        if (zeilen.size > MAX_PROTOKOLL + PROTOKOLL_PUFFER) protokoll.writeText(zeilen.takeLast(MAX_PROTOKOLL).joinToString("\n", postfix = "\n"))
    }

    private fun FotoVorschlag.json() = JSONObject()
        .put("name", name)
        .put("kcal", kcal ?: JSONObject.NULL)
        .put("eiweiss", eiweiss?.toPlainString() ?: JSONObject.NULL)
        .put("kohlenhydrate", kohlenhydrate?.toPlainString() ?: JSONObject.NULL)
        .put("fett", fett?.toPlainString() ?: JSONObject.NULL)

    private fun vorschlag(o: JSONObject) = FotoVorschlag(
        name = o.getString("name"),
        kcal = if (o.isNull("kcal")) null else o.getInt("kcal"),
        eiweiss = zahl(o, "eiweiss"),
        kohlenhydrate = zahl(o, "kohlenhydrate"),
        fett = zahl(o, "fett"),
    )

    private fun zahl(o: JSONObject, schluessel: String) = if (o.isNull(schluessel)) null else o.getString(schluessel).toBigDecimal()

    companion object {
        const val MAX_EINTRAEGE = 300
        const val MAX_PROTOKOLL = 500

        /** Gekürzt wird erst, wenn so viele Zeilen zu viel sind (nicht bei jedem Speichern). */
        private const val PROTOKOLL_PUFFER = 50
    }
}
