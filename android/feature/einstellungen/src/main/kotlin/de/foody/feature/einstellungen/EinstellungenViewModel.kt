package de.foody.feature.einstellungen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Darstellung
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.TAGESZIEL_MAX
import de.foody.core.data.TAGESZIEL_MIN
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.sicherung.LoeschErgebnis
import de.foody.core.data.sicherung.Sicherung
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Was gerade läuft; solange eine Aktion läuft, werden weitere ignoriert. */
enum class Aktion { ERSTELLEN, WIEDERHERSTELLEN, LOESCHEN }

/** Zustand der Einstellungsseite; [gesperrt]: Wiederherstellen und Löschen nicht erlaubt (Sync verbunden). */
data class EinstellungenZustand(
    val darstellung: Darstellung = Darstellung.SYSTEM,
    val tagesziel: Int? = null,
    val konto: VerbindungsZustand? = null,
    val laeuft: Aktion? = null,
) {
    val gesperrt: Boolean get() = konto?.verbunden == true
}

/**
 * Einstellungen: Darstellung und Tagesziel sofort speichern; Sicherung erstellen, wiederherstellen und alles löschen
 * im [viewModelScope] (Drehen bricht sie nicht ab), Ergebnis als Meldung. Die Dateidialoge liegen in der Route; hier
 * kommt nur die Uri an.
 */
@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val darstellungen: DarstellungSpeicher,
    private val ziel: TageszielSpeicher,
    private val sicherung: Sicherung,
    konto: KontoZugriff,
    private val clock: Clock,
) : ViewModel() {
    private val laufend = MutableStateFlow<Aktion?>(null)
    val laeuft: StateFlow<Aktion?> = laufend.asStateFlow()

    val zustand: StateFlow<EinstellungenZustand> =
        combine(darstellungen.darstellung(), ziel.tagesziel(), konto.zustand, laufend) { d, t, k, l ->
            EinstellungenZustand(d, t, k, l)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EinstellungenZustand())

    private val kanal = Channel<String>(Channel.BUFFERED)
    val meldungen: Flow<String> = kanal.receiveAsFlow()

    fun darstellungSetzen(d: Darstellung) = darstellungen.setzen(d)

    /** Leer = kein Ziel; Tausenderpunkte und Leerzeichen erlaubt. `false` = ungültig, nichts gespeichert. */
    fun tageszielEingabe(text: String): Boolean {
        val ziffern = text.replace(".", "").replace(" ", "").trim()
        if (ziffern.isEmpty()) {
            ziel.setzen(null)
            return true
        }
        val kcal = ziffern.toIntOrNull()?.takeIf { it in TAGESZIEL_MIN..TAGESZIEL_MAX } ?: return false
        ziel.setzen(kcal)
        return true
    }

    fun dateiname(): String = "foody-sicherung-${LocalDate.now(clock)}.zip"

    fun sicherungErstellen(uri: Uri) = starten(Aktion.ERSTELLEN) {
        try {
            withContext(Dispatchers.IO) {
                val aus = kontext.contentResolver.openOutputStream(uri) ?: error("Datei lässt sich nicht öffnen")
                aus.use { sicherung.schreiben(it) }
            }
            "Sicherung gespeichert"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Sicherung fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
        }
    }

    fun wiederherstellen(uri: Uri) = starten(Aktion.WIEDERHERSTELLEN) {
        val ergebnis = withContext(Dispatchers.IO) {
            kontext.contentResolver.openInputStream(uri)?.use { sicherung.lesen(it) }
        }
        ergebnis?.let(::importMeldung) ?: "Die Datei lässt sich nicht öffnen"
    }

    fun allesLoeschen() = starten(Aktion.LOESCHEN) {
        when (sicherung.allesLoeschen()) {
            LoeschErgebnis.Fertig -> "Alle Daten gelöscht"
            LoeschErgebnis.SyncAktiv -> ERST_TRENNEN
        }
    }

    private fun starten(aktion: Aktion, arbeit: suspend () -> String) {
        if (!laufend.compareAndSet(null, aktion)) return
        viewModelScope.launch {
            try {
                kanal.send(arbeit())
            } finally {
                laufend.value = null
            }
        }
    }
}
