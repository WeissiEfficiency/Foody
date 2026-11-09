package de.foody.core.erkennung

import androidx.lifecycle.SavedStateHandle
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Zustand des Foto-Knopfs ohne Oberfläche (Phase 3 zeichnet ihn). */
data class ErkennungZustand(
    /** Eine Erkennung läuft: Fortschritt mit „Abbrechen“ statt Knopf. */
    val laeuft: Boolean = false,
    /** Hinweis nach einer Erkennung ohne Vorschlag; `null`: keiner. */
    val meldung: String? = null,
    /** Datei des Kamerafotos, solange die Kamera offen ist. */
    val fotoPfad: String? = null,
    /** Widget „KI-Foto“: Der Knopf soll gleich die Kamera öffnen (einmal, dann [ErkennungLauf.kameraAnfrageErledigt]). */
    val kameraAngefragt: Boolean = false,
)

/**
 * Ablauf hinter dem Foto-Knopf, gehalten vom ViewModel (wie `ScanLauf`): Die Erkennung läuft in [scope]
 * (`viewModelScope`) und bricht beim Drehen nicht ab. Bild und Kamerafoto liegen in [gespeichert]; endet der Prozess
 * mitten in der Erkennung, startet sie beim nächsten Mal von selbst neu. Ein Vorschlag geht an [onVorschlag] (füllt
 * „Frei“ vor), sonst steht eine Meldung da.
 */
class ErkennungLauf(
    private val erkenner: MahlzeitErkenner,
    private val scope: CoroutineScope,
    private val gespeichert: SavedStateHandle,
    private val onVorschlag: suspend (FotoVorschlag) -> Unit,
) {
    private val _zustand = MutableStateFlow(
        ErkennungZustand(meldung = gespeichert[MELDUNG], fotoPfad = gespeichert[FOTO], kameraAngefragt = gespeichert[ANFRAGE] ?: false),
    )
    val zustand: StateFlow<ErkennungZustand> = _zustand.asStateFlow()

    private var arbeit: Job? = null

    init {
        gespeichert.get<String>(BILD)?.let { starten(it, gespeichert[LOESCHEN]) }
    }

    /** Die Kamera soll aufgehen, sobald der Knopf zu sehen ist; übersteht Drehen und Prozessende bis dahin. */
    fun kameraAnfragen() = anfrage(true)

    /** Angenommen (vor dem Start der Kamera, damit ein Neuaufbau sie nicht noch einmal öffnet) oder verworfen. */
    fun kameraAnfrageErledigt() = anfrage(false)

    private fun anfrage(an: Boolean) {
        gespeichert[ANFRAGE] = an
        _zustand.value = _zustand.value.copy(kameraAngefragt = an)
    }

    fun kameraGestartet(pfad: String) {
        gespeichert[FOTO] = pfad
        _zustand.value = _zustand.value.copy(fotoPfad = pfad)
    }

    /** Die Kamera ist zurück: liefert den Pfad ihres Fotos (falls einer gemerkt war) und vergisst ihn. */
    fun kameraZurueck(): String? = _zustand.value.fotoPfad.also {
        gespeichert.remove<String>(FOTO)
        _zustand.value = _zustand.value.copy(fotoPfad = null)
    }

    /** Erkennt das Foto [bildUri]; [loeschen] (das Kamerafoto) wird danach gelöscht. Läuft schon eine, passiert nichts. */
    fun erkennen(bildUri: String, loeschen: String? = null) {
        if (arbeit?.isActive == true) return
        gespeichert[BILD] = bildUri
        gespeichert[LOESCHEN] = loeschen
        starten(bildUri, loeschen)
    }

    fun abbrechen() {
        arbeit?.cancel()
    }

    private fun starten(bildUri: String, loeschen: String?) {
        meldung(null)
        _zustand.value = _zustand.value.copy(laeuft = true)
        arbeit = scope.launch {
            try {
                when (val status = erkenner.erkennen(bildUri)) {
                    is ErkennungStatus.Erkannt -> onVorschlag(status.vorschlag)
                    else -> meldung(ErkennungTexte.meldung(status))
                }
            } finally {
                gespeichert.remove<String>(BILD)
                gespeichert.remove<String>(LOESCHEN)
                loeschen?.let { File(it).delete() }
                _zustand.value = _zustand.value.copy(laeuft = false)
            }
        }
    }

    private fun meldung(text: String?) {
        gespeichert[MELDUNG] = text
        _zustand.value = _zustand.value.copy(meldung = text)
    }

    private companion object {
        const val BILD = "fotoErkennung.bild"
        const val LOESCHEN = "fotoErkennung.loeschen"
        const val FOTO = "fotoErkennung.kamera"
        const val MELDUNG = "fotoErkennung.meldung"
        const val ANFRAGE = "fotoErkennung.kameraAnfrage"
    }
}

/** Beschriftungen und Meldungen des Foto-Knopfs. */
object ErkennungTexte {
    const val KNOPF = "Foto erkennen"
    const val KAMERA = "Foto aufnehmen"
    const val GALERIE = "Aus der Galerie"
    const val LAEUFT = "Erkennt das Foto … (meist unter einer Minute)"
    const val ABBRECHEN = "Abbrechen"
    const val BITTE_PRUEFEN = "Geschätzt aus dem Foto – bitte prüfen"
    const val WIE_LETZTES_MAL = "Wie beim letzten Mal – bitte prüfen"

    const val KEIN_MODELL = "Kein Modell für die Fotoerkennung installiert (Einstellungen)."
    const val ZU_WENIG_SPEICHER = "Zu wenig freier Arbeitsspeicher. Andere Apps schließen und noch einmal versuchen."
    const val ZEITLIMIT = "Die Erkennung hat zu lange gedauert. Noch einmal versuchen."
    const val UNLESBAR = "Auf dem Foto wurde nichts erkannt. Bitte von Hand eintragen."
    const val FEHLER = "Die Erkennung hat nicht geklappt. Bitte von Hand eintragen."

    fun meldung(status: ErkennungStatus): String? = when (status) {
        is ErkennungStatus.Erkannt -> null
        ErkennungStatus.KeinModell -> KEIN_MODELL
        ErkennungStatus.ZuWenigSpeicher -> ZU_WENIG_SPEICHER
        ErkennungStatus.Zeitlimit -> ZEITLIMIT
        ErkennungStatus.Unlesbar -> UNLESBAR
        ErkennungStatus.Fehler -> FEHLER
    }
}
