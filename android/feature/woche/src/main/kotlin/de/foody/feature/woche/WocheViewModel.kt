package de.foody.feature.woche

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.data.woche.WocheRepository
import de.foody.domain.Mahlzeit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** „Leere Tage füllen“: Vorschlag für [tage]; [abgewaehlt] wird nicht angelegt; [hinweis] statt Vorschlag. */
data class FuellenZustand(
    val mahlzeit: Mahlzeit,
    val tage: List<LocalDate>,
    val vorschlag: Map<LocalDate, String>,
    val abgewaehlt: Set<LocalDate>,
    val hinweis: String?,
)

private const val MONTAG = "montag"
private const val VERSUCHE_JE_TAG = 20
private const val STOPP_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WocheViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val repo: WocheRepository,
    ziel: TageszielSpeicher,
    private val clock: Clock,
) : ViewModel() {
    private val montag = saved.getStateFlow(MONTAG, montagVon(LocalDate.now(clock)).toEpochDay())

    val woche: StateFlow<WochenAnsicht?> = montag.flatMapLatest { repo.woche(LocalDate.ofEpochDay(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tagesziel: StateFlow<Int?> = ziel.tagesziel().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val zeigtHeute: StateFlow<Boolean> = montag.map { it == montagVon(LocalDate.now(clock)).toEpochDay() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Rezepte für „+ Mahlzeit“ (ohne archivierte). */
    val rezeptAuswahl: StateFlow<List<RezeptKarte>> = repo.rezeptAuswahl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOPP_MS), emptyList())

    private val fuellenStand = MutableStateFlow<FuellenZustand?>(null)
    val fuellen: StateFlow<FuellenZustand?> = fuellenStand.asStateFlow()
    private var seed = 0L
    private val uebernehmenGesperrt = MutableStateFlow(false)

    /** Heutiges Datum nach der Uhr (für die Hervorhebung). */
    val heuteDatum: LocalDate get() = LocalDate.now(clock)

    fun blaettern(wochen: Long) {
        saved[MONTAG] = montag.value + wochen * 7
    }

    fun heute() {
        saved[MONTAG] = montagVon(LocalDate.now(clock)).toEpochDay()
    }

    /** [portionen] `null` = die des Rezepts. */
    fun hinzufuegen(datum: LocalDate, mahlzeit: String, rezeptId: String, portionen: Int? = null) =
        viewModelScope.launch { repo.hinzufuegen(datum, mahlzeit, rezeptId, portionen) }

    fun portionen(id: String, n: Int) = viewModelScope.launch { repo.portionen(id, n) }

    fun verschieben(id: String, datum: LocalDate) = viewModelScope.launch { repo.verschieben(id, datum) }

    fun umbenennen(id: String, mahlzeit: String) = viewModelScope.launch { repo.umbenennen(id, mahlzeit) }

    fun gekocht(id: String, an: Boolean) = viewModelScope.launch { repo.gekocht(id, an) }

    suspend fun entfernen(id: String): PlanEintragEntity? = repo.entfernen(id)

    fun wiederherstellen(e: PlanEintragEntity) = viewModelScope.launch { repo.wiederherstellen(e) }

    /** Öffnet „Leere Tage füllen“ für die gezeigte Woche; „heute“ gilt zum Zeitpunkt des Aufrufs. */
    fun fuellenOeffnen(mahlzeit: Mahlzeit = Mahlzeit.ABENDESSEN) = viewModelScope.launch {
        uebernehmenGesperrt.value = false
        val tage = repo.leereTage(LocalDate.ofEpochDay(montag.value), mahlzeit, LocalDate.now(clock))
        seed = clock.millis()
        val vorschlag = repo.vorschlagen(tage, mahlzeit, seed)
        val hinweis = when {
            tage.isEmpty() -> "Alle Tage ab heute haben schon ein ${mahlzeitAnzeige(mahlzeit)}"
            vorschlag.isEmpty() -> "Keine passenden Rezepte – alles schon in den letzten 14 Tagen geplant"
            else -> null
        }
        fuellenStand.value = FuellenZustand(mahlzeit, tage, vorschlag, emptySet(), hinweis)
    }

    /** Neuer Vorschlag für alle Tage; abgewählte bleiben abgewählt. */
    fun neuMischen() = viewModelScope.launch {
        val z = fuellenStand.value ?: return@launch
        seed++
        fuellenStand.value = z.copy(vorschlag = repo.vorschlagen(z.tage, z.mahlzeit, seed))
    }

    /** Neuer Vorschlag nur für [datum]; die übrigen Tage bleiben, kein Rezept doppelt. */
    fun tagNeu(datum: LocalDate) = viewModelScope.launch {
        val z = fuellenStand.value ?: return@launch
        val andere = (z.vorschlag - datum).values.toSet()
        val bisher = z.vorschlag[datum]
        val neu = (1..VERSUCHE_JE_TAG).firstNotNullOfOrNull {
            seed++
            repo.vorschlagen(listOf(datum), z.mahlzeit, seed)[datum]?.takeIf { it !in andere && it != bisher }
        } ?: return@launch
        fuellenStand.value = z.copy(vorschlag = z.vorschlag + (datum to neu))
    }

    fun abwaehlen(datum: LocalDate) {
        val z = fuellenStand.value ?: return
        fuellenStand.value = z.copy(abgewaehlt = if (datum in z.abgewaehlt) z.abgewaehlt - datum else z.abgewaehlt + datum)
    }

    /** Legt die gewählten Vorschläge an und schließt; `null` bei einem zweiten Tipp. */
    suspend fun uebernehmen(): Int? {
        val z = fuellenStand.value ?: return null
        if (!uebernehmenGesperrt.compareAndSet(expect = false, update = true)) return null
        val n = repo.uebernehmen(z.vorschlag - z.abgewaehlt, z.mahlzeit)
        fuellenStand.value = null
        return n
    }

    fun fuellenSchliessen() {
        fuellenStand.value = null
    }
}
