package de.foody.feature.kochen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.kochen.KochenRepository
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.StepTimer
import de.foody.feature.kochen.timer.KochTimerSpeicher
import de.foody.feature.kochen.timer.LaufenderTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface KochZustand {
    data object Laden : KochZustand

    data object NichtGefunden : KochZustand

    /**
     * Ein Rezept beim Kochen. Seiten: „Bereitlegen“ (wenn es Zutaten gibt oder keine Schritte), dann je Schritt eine;
     * [seite] liegt immer im gültigen Bereich, [abgehakt] enthält nur vorhandene Zeilen.
     */
    data class Da(
        val name: String,
        val bild: String?,
        val rezeptPortionen: Int,
        val portionen: Int,
        val bereit: List<BereitZeile>,
        val schritte: List<KochSchritt>,
        val seite: Int,
        val abgehakt: Set<Int>,
    ) : KochZustand {
        val hatBereitlegen: Boolean get() = bereit.isNotEmpty() || schritte.isEmpty()
        val seitenzahl: Int get() = (if (hatBereitlegen) 1 else 0) + schritte.size

        /** Schritt auf [seite] (`null` = „Bereitlegen“). */
        fun schritt(seite: Int): KochSchritt? = schritte.getOrNull(seite - if (hatBereitlegen) 1 else 0)
    }
}

/** Was der Nutzer gewählt hat; [portionen] `null` = die des Rezepts. */
private data class Stand(val portionen: Int?, val seite: Int, val abgehakt: Set<Int>)

private const val TICK_MS = 1_000L
private const val REZEPT = "kochenRezept"
private const val FERTIG = "kochenFertig"
private const val MIN_PORTIONEN = 1
private const val MAX_PORTIONEN = 99

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KochenViewModel @Inject constructor(
    /** Welches Rezept offen ist und ob „Fertig“ schon getippt wurde; übersteht ein Prozessende. */
    private val gespeichert: SavedStateHandle,
    private val repo: RezeptRepository,
    private val kochen: KochenRepository,
    private val sitzungen: KochSitzungSpeicher,
    internal val timerSpeicher: KochTimerSpeicher,
    private val clock: Clock,
    private val ablage: FotoAblage,
) : ViewModel() {
    private val rezeptId = MutableStateFlow<String?>(null)
    private val stand = MutableStateFlow(Stand(null, 0, emptySet()))
    private val fertigGetippt = MutableStateFlow(false)

    /** Nach „Fertig“ oder „Schließen“ wird keine Sitzung mehr geschrieben. */
    @Volatile private var beendet = false

    private val blatt = MutableStateFlow(false)

    /** Das Blatt „Guten Appetit!“ ist offen. */
    val fertigOffen: StateFlow<Boolean> = blatt.asStateFlow()

    val timer: StateFlow<List<LaufenderTimer>> = timerSpeicher.timer

    /** Uhrzeit für die Restzeiten; tickt jede Sekunde, solange ein Timer läuft. */
    val jetzt: StateFlow<Long> = timer.flatMapLatest { liste ->
        flow {
            while (true) {
                val t = clock.millis()
                emit(t)
                if (liste.none { !it.abgelaufen(t) }) break
                delay(TICK_MS - t % TICK_MS)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TICK_MS), clock.millis())

    val zustand: StateFlow<KochZustand> = rezeptId.filterNotNull().flatMapLatest(repo::voll).combine(stand) { v, s ->
        if (v == null) return@combine KochZustand.NichtGefunden
        val portionen = s.portionen ?: v.rezept.defaultServings.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN)
        val bereit = bereitlegen(v, portionen)
        val schritte = kochSchritte(v, portionen)
        val roh = KochZustand.Da(v.rezept.name, v.rezept.imageUri, v.rezept.defaultServings, portionen, bereit, schritte, 0, emptySet())
        roh.copy(seite = s.seite.coerceIn(0, roh.seitenzahl - 1), abgehakt = s.abgehakt.filter { it < bereit.size }.toSet())
    }.onEach(::sitzungMerken).stateIn(viewModelScope, SharingStarted.Eagerly, KochZustand.Laden)

    /**
     * Öffnet [id]; Portionen aus [portionen] (begrenzt 1–99), sonst aus der Sitzung, sonst aus dem Rezept. Nach einem
     * Prozessende gilt die Sitzung vor [portionen] (hier geänderte Portionen bleiben), und ein schon getipptes „Fertig“
     * öffnet wieder das Blatt.
     */
    fun starten(id: String, portionen: Int?) {
        // Derselbe Aufruf nach dem Drehen ändert nichts: hier geänderte Portionen, Seite und Haken bleiben
        if (rezeptId.value == id) return
        val nachNeustart = gespeichert.get<String>(REZEPT) == id
        gespeichert[REZEPT] = id
        val s = sitzungen.lesen(id)
        val p = if (nachNeustart) s?.portionen ?: portionen else portionen ?: s?.portionen
        stand.value = Stand(p?.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN), s?.seite ?: 0, s?.abgehakt.orEmpty())
        if (nachNeustart && gespeichert.get<Boolean>(FERTIG) == true) {
            beendet = true
            fertigGetippt.value = true
            blatt.value = true
        }
        rezeptId.value = id
    }

    fun seiteWaehlen(i: Int) = stand.update { it.copy(seite = i) }

    fun abhaken(zeilenIndex: Int) = stand.update { s ->
        s.copy(abgehakt = if (zeilenIndex in s.abgehakt) s.abgehakt - zeilenIndex else s.abgehakt + zeilenIndex)
    }

    fun alleZuruecksetzen() = stand.update { it.copy(abgehakt = emptySet()) }

    fun portionenSchritt(mehr: Boolean) {
        val da = zustand.value as? KochZustand.Da ?: return
        stand.update { it.copy(portionen = (da.portionen + if (mehr) 1 else -1).coerceIn(MIN_PORTIONEN, MAX_PORTIONEN)) }
    }

    /** Startet [t] für das Rezept; liefert die Timer-ID (leer, solange kein Rezept da ist). */
    fun timerStarten(t: StepTimer): String {
        val da = zustand.value as? KochZustand.Da ?: return ""
        val id = rezeptId.value ?: return ""
        return timerSpeicher.starten(id, da.portionen, da.name, t.label, t.duration)
    }

    fun timerEntfernen(id: String) = timerSpeicher.entfernen(id)

    /**
     * Gekocht: markiert den heutigen Plan-Eintrag (falls es einen gibt), beendet die Sitzung und öffnet das Blatt.
     * `false` bei einem zweiten Tipp – es wird nie mehr als ein Eintrag je Kochen markiert.
     */
    suspend fun fertig(): Boolean {
        val id = rezeptId.value ?: return false
        if (!fertigGetippt.compareAndSet(expect = false, update = true)) return false
        kochen.gekochtMarkieren(id, LocalDate.now(clock))
        gespeichert[FERTIG] = true
        schliessen()
        blatt.value = true
        return true
    }

    /** Beendet die Sitzung (Fertig-Blatt „Schließen“); laufende Timer bleiben. */
    fun schliessen() {
        beendet = true
        rezeptId.value?.let(sitzungen::loeschen)
    }

    /**
     * Kamerabild verkleinert in die Fotoablage und als Rezeptfoto; scheitert es, bleibt alles, wie es war. Läuft zu
     * Ende, auch wenn der Kochmodus gleich danach geschlossen wird, und löscht danach die [kameraDatei].
     */
    fun fotoUebernehmen(quelle: Uri, kameraDatei: File) {
        val id = rezeptId.value ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repo.fotoSetzen(id, ablage.speichern(quelle))
                } catch (_: Exception) {
                    // Foto unlesbar: das alte bleibt
                } finally {
                    kameraDatei.delete()
                }
            }
        }
    }

    /** `true` nur beim ersten Mal: der Hinweis, dass Timer ohne Benachrichtigungen nur bei offener App melden. */
    fun hinweisOhneBenachrichtigungEinmal(): Boolean = timerSpeicher.hinweisEinmal()

    private fun sitzungMerken(z: KochZustand) {
        val id = rezeptId.value ?: return
        if (beendet) return
        when (z) {
            KochZustand.NichtGefunden -> sitzungen.loeschen(id)
            is KochZustand.Da -> sitzungen.schreiben(KochSitzung(id, z.portionen, z.seite, z.abgehakt, clock.millis()))
            KochZustand.Laden -> Unit
        }
    }
}
