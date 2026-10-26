package de.foody.feature.zutaten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.zutaten.Angleichung
import de.foody.core.data.zutaten.ZutatErgebnis
import de.foody.core.data.zutaten.ZutatFilter
import de.foody.core.data.zutaten.ZutatKurz
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.data.zutaten.filtern
import javax.inject.Inject
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

/** Zustand der Katalogliste; [treffer] = nach Suche und Filtern. */
data class ListeZustand(
    val suche: String = "",
    val filter: Set<ZutatFilter> = emptySet(),
    val treffer: List<ZutatKurz> = emptyList(),
    val geladen: Boolean = false,
    val laeuft: Boolean = false,
)

/** Einmalige Ereignisse für die Oberfläche. */
sealed interface ListeEreignis {
    data class Oeffnen(val id: String) : ListeEreignis

    /** „Neue Zutat“ mit einem Namen, den es schon gibt. */
    data class GibtEsSchon(val id: String, val name: String) : ListeEreignis

    data class Meldung(val text: String) : ListeEreignis
}

/** Katalogliste: Suche, Filter, „Neue Zutat“ und „Namen vereinheitlichen“ (Vorschau, dann anwenden). */
@HiltViewModel
class ZutatenListeViewModel @Inject constructor(
    private val repo: ZutatenRepository,
    meldungen: ZutatenMeldungen,
) : ViewModel() {
    private val suche = MutableStateFlow("")
    private val filter = MutableStateFlow(emptySet<ZutatFilter>())
    private val laufend = MutableStateFlow(false)

    val zustand: StateFlow<ListeZustand> = combine(repo.liste(), suche, filter, laufend) { liste, s, f, l ->
        ListeZustand(s, f, filtern(liste, s, f), geladen = true, laeuft = l)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ListeZustand())

    private val vorschauStand = MutableStateFlow<List<Angleichung>?>(null)

    /** Vorschau von „Namen vereinheitlichen“; `null` = kein Dialog. */
    val vorschau: StateFlow<List<Angleichung>?> = vorschauStand.asStateFlow()

    private val kanal = Channel<ListeEreignis>(Channel.BUFFERED)
    val ereignisse: Flow<ListeEreignis> = kanal.receiveAsFlow()

    init {
        viewModelScope.launch { meldungen.fluss.collect { kanal.send(ListeEreignis.Meldung(it)) } }
    }

    fun suchen(text: String) {
        suche.value = text
    }

    fun filterUmschalten(f: ZutatFilter) {
        filter.value = if (f in filter.value) filter.value - f else filter.value + f
    }

    fun vorschauLaden() {
        viewModelScope.launch { vorschauStand.value = repo.vereinheitlichenVorschau() }
    }

    fun vorschauSchliessen() {
        vorschauStand.value = null
    }

    /** Wendet „Namen vereinheitlichen“ an; nur aus der offenen Vorschau, ein zweiter Tipp wird ignoriert. */
    fun anwenden() = starten {
        if (vorschauStand.value == null) return@starten
        vorschauStand.value = null
        kanal.send(ListeEreignis.Meldung(angepasstText(repo.vereinheitlichen())))
    }

    fun anlegen(name: String) = starten {
        when (val e = repo.anlegen(name)) {
            is ZutatErgebnis.Gespeichert -> kanal.send(ListeEreignis.Oeffnen(e.id))
            is ZutatErgebnis.NameVergeben -> kanal.send(ListeEreignis.GibtEsSchon(e.id, e.name))
            else -> Unit // leerer Name: Der Dialog lässt „Anlegen“ dann nicht zu
        }
    }

    private fun starten(arbeit: suspend () -> Unit) {
        if (!laufend.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                arbeit()
            } finally {
                laufend.value = false
            }
        }
    }
}
