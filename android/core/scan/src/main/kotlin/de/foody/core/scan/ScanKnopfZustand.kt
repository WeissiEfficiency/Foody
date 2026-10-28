package de.foody.core.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import de.foody.core.data.zutaten.Packungswerte

/**
 * Zustand des Scan-Knopfs ohne Oberfläche: Meldung, Foto-Angebot, gemerkter Strichcode und der Pfad eines laufenden
 * Kamerafotos. Liegt in [ScanLauf] (ViewModel) und übersteht ein Prozessende über [Saver]; [laeuft] nicht.
 */
class ScanKnopfZustand(
    meldung: String? = null,
    fotoAnbieten: Boolean = false,
    strichcode: String? = null,
    fotoPfad: String? = null,
) {
    /** Hinweis unter dem Knopf; `null`: keiner. */
    var meldung by mutableStateOf(meldung)
        private set

    /** Nach „nicht gefunden“ oder „keine Tabelle“ stehen die beiden Foto-Wege als Knöpfe da. */
    var fotoAnbieten by mutableStateOf(fotoAnbieten)
        private set

    /** Strichcode des letzten Versuchs; hängt sich an die Foto-Werte. */
    var strichcode by mutableStateOf(strichcode)
        private set

    /** Datei des Kamerafotos, solange die Kamera offen ist. */
    var fotoPfad by mutableStateOf(fotoPfad)
        private set

    /** Eine Erkennung läuft (Fortschritt statt Symbol, Knopf gesperrt). */
    var laeuft by mutableStateOf(false)
        private set

    /** Was der Knopf an den Aufrufer weitergibt. */
    sealed interface Ergebnis {
        data class Packung(val werte: Packungswerte) : Ergebnis
        data class ImKatalog(val treffer: ScanAusgang.ImKatalog) : Ergebnis
    }

    fun starten() {
        laeuft = true
    }

    /** Ein neuer Versuch über das Menü gehört zu keinem früheren Strichcode (vielleicht eine andere Packung). */
    fun menueVersuch() {
        strichcode = null
        fotoAnbieten = false
        meldung = null
    }

    fun kameraGestartet(pfad: String) {
        fotoPfad = pfad
    }

    /** Die Kamera ist zurück: liefert den Pfad ihres Fotos (falls einer gemerkt war) und vergisst ihn. */
    fun kameraZurueck(): String? = fotoPfad.also { fotoPfad = null }

    /** Wertet einen Scan aus; Treffer gibt der Knopf weiter, sonst steht die passende Meldung da (Spec 6e3, 4.). */
    fun auswerten(a: ScanAusgang): Ergebnis? {
        laeuft = false
        meldung = null
        return when (a) {
            is ScanAusgang.Gefunden -> {
                erledigt()
                Ergebnis.Packung(a.packung)
            }
            is ScanAusgang.ImKatalog -> {
                erledigt()
                Ergebnis.ImKatalog(a)
            }
            is ScanAusgang.NichtGefunden -> {
                strichcode = a.strichcode
                meldung = ScanTexte.nichtGefunden(a.grund)
                fotoAnbieten = true
                null
            }
            is ScanAusgang.KeineTabelle -> {
                meldung = ScanTexte.KEINE_TABELLE
                fotoAnbieten = true
                null
            }
            ScanAusgang.WirdGeladen -> null.also { meldung = ScanTexte.WIRD_GELADEN }
            ScanAusgang.NichtVerfuegbar -> null.also { meldung = ScanTexte.NICHT_VERFUEGBAR }
            ScanAusgang.Fehler -> null.also { meldung = ScanTexte.FEHLER }
            ScanAusgang.Abgebrochen -> null
        }
    }

    private fun erledigt() {
        fotoAnbieten = false
        strichcode = null
    }

    companion object {
        val Saver: Saver<ScanKnopfZustand, Any> = listSaver(
            save = { listOf(it.meldung, it.fotoAnbieten, it.strichcode, it.fotoPfad) },
            restore = { ScanKnopfZustand(it[0] as String?, it[1] as Boolean, it[2] as String?, it[3] as String?) },
        )
    }
}
