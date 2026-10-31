package de.foody.feature.einkauf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.einkauf.EinkaufRepository
import de.foody.core.data.einkauf.GeloeschterArtikel
import de.foody.core.data.einkauf.ListenKopf
import de.foody.core.data.einkauf.ZuletztArtikel
import de.foody.core.designsystem.SolangeAppOffen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val LISTE = "liste"
private const val ROUTE = "route"
private const val BLATT = "blatt"
private const val VON = "von"
private const val BIS = "bis"
private const val SUCHE = "suche"

/** Längster Zeitraum einer Liste aus dem Plan. */
private const val ZEITRAUM_MAX_TAGE = 28L
private const val VORSCHAU_TAGE = 7L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EinkaufViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val repo: EinkaufRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Verlangte Liste; `null` = die aktuelle. */
    private val verlangt = saved.getStateFlow<String?>(LISTE, null)
    private val suchText = saved.getStateFlow(SUCHE, "")

    /** Angezeigte Liste: die verlangte oder die aktuelle; in Aktionen frisch gelesen statt im Hintergrund beobachtet. */
    private suspend fun angezeigt(): String? = verlangt.value ?: repo.aktuelleListeId().first()

    val zustand: StateFlow<EinkaufZustand> = combine(
        verlangt.flatMapLatest { v ->
            (if (v != null) flowOf(v) else repo.aktuelleListeId()).flatMapLatest { id ->
                if (id == null) {
                    flowOf(EinkaufZustand(geladen = true))
                } else {
                    combine(repo.liste(id), repo.abgleich(id)) { ansicht, abgleich ->
                        EinkaufZustand(ansicht = ansicht, geladen = true, listeFehlt = ansicht == null, abgleich = abgleich)
                    }
                }
            }
        },
        repo.zuletzt(),
        suchText,
    ) { z, zuletzt, suche -> z.copy(zuletzt = zuletzt, suche = suche) }
        .stateIn(viewModelScope, SolangeAppOffen, EinkaufZustand())

    val fruehere: StateFlow<List<ListenKopf>> = repo.fruehere().stateIn(viewModelScope, SolangeAppOffen, emptyList())

    val blatt: StateFlow<Blatt?> = saved.getStateFlow<String?>(BLATT, null).map { Blatt.ausText(it) }
        .stateIn(viewModelScope, SolangeAppOffen, Blatt.ausText(saved[BLATT]))

    private val vorschauStand = MutableStateFlow<VorschauZustand?>(null)
    val vorschau: StateFlow<VorschauZustand?> = vorschauStand.asStateFlow()
    private var vorschauAuftrag: Job? = null

    private val abgleichAus = MutableStateFlow<Set<String>>(emptySet())
    val abgleichAbgewaehlt: StateFlow<Set<String>> = abgleichAus.asStateFlow()

    private val gesperrt = MutableStateFlow(false)

    init {
        // Nach Prozessende: offene Vorschau mit dem gemerkten Zeitraum neu rechnen
        if (blatt.value == Blatt.Vorschau) {
            val von = saved.get<Long>(VON)?.let(LocalDate::ofEpochDay)
            val bis = saved.get<Long>(BIS)?.let(LocalDate::ofEpochDay)
            if (von != null && bis != null) zeitraum(von, bis)
        }
    }

    /** Aus der Route: `null` = aktuelle Liste. Wiederholt (Drehen, Prozessende) ändert es nichts. */
    fun anzeigen(listeId: String?) {
        if (saved.contains(ROUTE) && saved.get<String?>(ROUTE) == listeId) return
        saved[ROUTE] = listeId
        saved[LISTE] = listeId
    }

    fun suche(text: String) {
        saved[SUCHE] = text
    }

    fun eingeben() {
        val text = suchText.value
        if (text.isBlank()) return
        saved[SUCHE] = ""
        viewModelScope.launch { repo.eingeben(angezeigt(), text) }
    }

    fun abhaken(id: String, an: Boolean) = viewModelScope.launch { repo.abhaken(id, an) }

    fun antippen(name: String, abteilung: String, ingredientId: String?) =
        viewModelScope.launch { repo.antippen(angezeigt(), name, abteilung, ingredientId) }

    fun zuletztAntippen(z: ZuletztArtikel) = viewModelScope.launch { repo.zuletztAntippen(angezeigt(), z) }

    fun details(id: String, name: String, notiz: String, abteilung: String) = viewModelScope.launch {
        // Die Oberfläche lässt einen ungültigen Namen nicht zu; kommt er doch, bleibt der Artikel wie er war
        runCatching { repo.details(id, name, notiz, abteilung) }
    }

    suspend fun loeschen(id: String): GeloeschterArtikel? = repo.loeschen(id)

    fun wiederherstellen(g: GeloeschterArtikel) = viewModelScope.launch { repo.wiederherstellen(g) }

    /** Löscht [listeId] (Vorgabe: die angezeigte); war sie angezeigt, folgt der Bereich danach der aktuellen Liste. */
    fun listeLoeschen(listeId: String? = null) = viewModelScope.launch {
        val angezeigt = angezeigt()
        val id = listeId ?: angezeigt ?: return@launch
        if (id == angezeigt) saved[LISTE] = null
        repo.listeLoeschen(id)
    }

    fun blattOeffnen(b: Blatt) {
        saved[BLATT] = b.text
        when (b) {
            Blatt.Vorschau -> LocalDate.now(clock).let { heute -> zeitraum(heute, heute.plusDays(VORSCHAU_TAGE - 1)) }
            Blatt.Abgleich -> abgleichAus.value = emptySet()
            else -> Unit
        }
    }

    fun blattSchliessen() {
        vorschauAuftrag?.cancel()
        vorschauStand.value = null
        saved[BLATT] = null
    }

    /** Neuer Zeitraum der Vorschau: Ende nicht vor dem Anfang, höchstens 28 Tage; rechnet neu, Abwahlen bleiben. */
    fun zeitraum(von: LocalDate, bis: LocalDate) {
        val ende = bis.coerceIn(von, von.plusDays(ZEITRAUM_MAX_TAGE - 1))
        saved[VON] = von.toEpochDay()
        saved[BIS] = ende.toEpochDay()
        vorschauStand.update { VorschauZustand(von, ende, null, it?.abgewaehlt.orEmpty()) }
        vorschauAuftrag?.cancel()
        vorschauAuftrag = viewModelScope.launch {
            val zeilen = repo.planVorschau(von, ende)
            // Nur in den Zustand, für den gerechnet wurde; geschlossen bleibt geschlossen
            vorschauStand.update { if (it != null && it.von == von && it.bis == ende) it.copy(zeilen = zeilen) else it }
        }
    }

    fun vorschauUmschalten(schluessel: String) = vorschauStand.update { v ->
        v?.copy(abgewaehlt = if (schluessel in v.abgewaehlt) v.abgewaehlt - schluessel else v.abgewaehlt + schluessel)
    }

    /** Legt die Liste an und zeigt sie; `null` bei einem zweiten Tipp, solange der erste läuft, oder wenn nichts übrig bleibt. */
    suspend fun anlegen(): String? {
        if (!gesperrt.compareAndSet(expect = false, update = true)) return null
        try {
            val v = vorschauStand.value ?: return null
            val id = repo.ausPlanAnlegen(v.von, v.bis, v.abgewaehlt) ?: return null
            saved[LISTE] = null
            blattSchliessen()
            return id
        } finally {
            gesperrt.value = false
        }
    }

    fun abgleichUmschalten(schluessel: String) = abgleichAus.update { if (schluessel in it) it - schluessel else it + schluessel }

    /** Übernimmt die angehakten Zeilen; `null` bei einem zweiten Tipp, solange der erste läuft. */
    suspend fun abgleichen(): Int? {
        if (!gesperrt.compareAndSet(expect = false, update = true)) return null
        try {
            val id = angezeigt() ?: return null
            val n = repo.abgleichen(id, abgleichAus.value)
            blattSchliessen()
            return n
        } finally {
            gesperrt.value = false
        }
    }
}
