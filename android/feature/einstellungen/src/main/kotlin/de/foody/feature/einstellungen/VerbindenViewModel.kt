package de.foody.feature.einstellungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.designsystem.SolangeAppOffen
import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Schritt { EINGABE, LAEUFT, WAHL }

/**
 * [fehler] als fertiger Klartext; das Passwort liegt nie hier, nur im Eingabefeld. [neuAnmelden]: Formular trotz
 * bestehender Verbindung (z. B. nach „Anmeldung abgelaufen“).
 */
data class VerbindenUi(val verbindung: VerbindungsZustand?, val schritt: Schritt, val fehler: String?, val neuAnmelden: Boolean = false)

@HiltViewModel
class VerbindenViewModel @Inject constructor(private val konto: KontoZugriff) : ViewModel() {
    private val schritt = MutableStateFlow(Schritt.EINGABE)
    private val fehler = MutableStateFlow<String?>(null)
    private val neuAnmelden = MutableStateFlow(false)

    val ui: StateFlow<VerbindenUi> = combine(konto.zustand, schritt, fehler, neuAnmelden) { z, s, f, n -> VerbindenUi(z, s, f, n) }
        .stateIn(viewModelScope, SolangeAppOffen, VerbindenUi(null, Schritt.EINGABE, null))

    /**
     * Meldet an. Wiederanmeldung (gleicher Server, gleiches Konto) und Geräte ohne lokale Daten werden sofort aktiviert;
     * mit lokalen Daten fragt die Wahl „Zusammenführen/Ersetzen“; ein Wechsel bei bestehender Verbindung verlangt Trennen.
     */
    fun anmelden(adresse: String, benutzer: String, passwort: String) {
        if (schritt.value == Schritt.LAEUFT) return
        schritt.value = Schritt.LAEUFT
        fehler.value = null
        viewModelScope.launch {
            when (val r = konto.anmelden(adresse.trim(), benutzer.trim(), passwort)) {
                is Ergebnis.Fehler -> abbrechenMit(r.kennung)
                is Ergebnis.Erfolg -> when {
                    // Gleicher Server und gleiches Konto: nur das Token erneuern, ohne Frage
                    konto.istWiederanmeldung() -> aktivieren(ErsterSync.ZUSAMMENFUEHREN)
                    // Wechsel bei bestehender Verbindung: erst trennen (sonst flössen alle Daten ungefragt ins andere Konto)
                    konto.zustand.first().verbunden -> abbrechenMit(Verbinden.ERST_TRENNEN)
                    konto.hatLokaleDaten() -> schritt.value = Schritt.WAHL
                    else -> aktivieren(ErsterSync.ZUSAMMENFUEHREN)
                }
            }
        }
    }

    fun waehlen(modus: ErsterSync) {
        schritt.value = Schritt.LAEUFT
        viewModelScope.launch { aktivieren(modus) }
    }

    fun neuAnmelden(an: Boolean) {
        neuAnmelden.value = an
        fehler.value = null
    }

    fun wahlAbbrechen() {
        schritt.value = Schritt.EINGABE
    }

    fun jetzt() = konto.jetztSynchronisieren()

    fun trennen() {
        viewModelScope.launch { konto.trennen() }
    }

    private suspend fun aktivieren(modus: ErsterSync) {
        when (val r = konto.aktivieren(modus)) {
            is Ergebnis.Fehler -> abbrechenMit(r.kennung)
            is Ergebnis.Erfolg -> {
                neuAnmelden.value = false
                schritt.value = Schritt.EINGABE
            }
        }
    }

    private fun abbrechenMit(kennung: String) {
        fehler.value = anmeldeFehlerText(kennung)
        schritt.value = Schritt.EINGABE
    }
}
