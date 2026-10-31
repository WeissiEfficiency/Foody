package de.foody.feature.heute.tagebuch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.SolangeSichtbar
import de.foody.domain.EintragWerte
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte
import de.foody.domain.Summe
import de.foody.domain.Tagebuch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Reihenfolge der Mahlzeiten im Tagebuch. */
internal val MAHLZEITEN = listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN)

data class TagAbschnitt(val mahlzeit: Mahlzeit, val eintraege: List<TagebuchEintragEntity>)

data class TagZustand(val datum: LocalDate, val summe: Summe, val ziel: Int?, val abschnitte: List<TagAbschnitt>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagebuchTagViewModel @Inject constructor(
    private val tagebuch: TagebuchRepository,
    ziel: TageszielSpeicher,
) : ViewModel() {
    private val datum = MutableStateFlow<LocalDate?>(null)

    val zustand: StateFlow<TagZustand?> = datum.filterNotNull().flatMapLatest { tag ->
        combine(tagebuch.amTag(tag), ziel.tagesziel()) { eintraege, kcalZiel ->
            TagZustand(
                datum = tag,
                summe = Tagebuch.bilanz(eintraege.map { EintragWerte(it.eintragMahlzeit(), it.werte()) }).tag,
                ziel = kcalZiel,
                abschnitte = MAHLZEITEN.map { m -> TagAbschnitt(m, eintraege.filter { it.eintragMahlzeit() == m }) },
            )
        }
    }.stateIn(viewModelScope, SolangeSichtbar, null)

    fun zeigen(tag: LocalDate) {
        datum.value = tag
    }

    fun aendern(id: String, menge: BigDecimal?, mahlzeit: Mahlzeit, tag: LocalDate, freiWerte: Naehrwerte?) {
        viewModelScope.launch { tagebuch.aendern(id, menge, mahlzeit, tag, freiWerte) }
    }

    /** Löscht und liefert den Eintrag für „Rückgängig“. */
    suspend fun loeschen(id: String): TagebuchEintragEntity? = tagebuch.loeschen(id)

    fun wiederherstellen(e: TagebuchEintragEntity) {
        viewModelScope.launch { tagebuch.wiederherstellen(e) }
    }
}
