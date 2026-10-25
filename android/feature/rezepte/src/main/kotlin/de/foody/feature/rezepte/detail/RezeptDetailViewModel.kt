package de.foody.feature.rezepte.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.einkauf.EinkaufRepository
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.data.rezepte.RezeptVoll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

sealed interface DetailZustand {
    data object Laden : DetailZustand

    data object NichtGefunden : DetailZustand

    /** [portionen]: gewählte Portionen (Stepper), anfangs die des Rezepts. */
    data class Da(val voll: RezeptVoll, val portionen: Int) : DetailZustand
}

/** Menge für [auf] statt [von] Portionen, 6 Nachkommastellen, ohne Nullen am Ende. */
fun mengeSkaliert(menge: BigDecimal, von: Int, auf: Int): BigDecimal =
    if (von <= 0 || von == auf) menge else menge.multiply(BigDecimal(auf)).divide(BigDecimal(von), 6, RoundingMode.HALF_UP).stripTrailingZeros()

private const val MIN_PORTIONEN = 1
private const val MAX_PORTIONEN = 99

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RezeptDetailViewModel @Inject constructor(
    private val repo: RezeptRepository,
    private val einkauf: EinkaufRepository,
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    private val portionen = MutableStateFlow<Int?>(null)

    val zustand: StateFlow<DetailZustand> = id.filterNotNull().flatMapLatest(repo::voll).combine(portionen) { v, p ->
        if (v == null) DetailZustand.NichtGefunden else DetailZustand.Da(v, p ?: v.rezept.defaultServings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOPP_MS), DetailZustand.Laden)

    fun zeigen(rezeptId: String) {
        if (id.value == rezeptId) return
        id.value = rezeptId
        portionen.value = null
    }

    fun portionenSchritt(mehr: Boolean) {
        val da = zustand.value as? DetailZustand.Da ?: return
        portionen.update { ((it ?: da.portionen) + if (mehr) 1 else -1).coerceIn(MIN_PORTIONEN, MAX_PORTIONEN) }
    }

    fun favorit(an: Boolean) {
        val rezeptId = id.value ?: return
        viewModelScope.launch { repo.favorit(rezeptId, an) }
    }

    /** [rezeptId] ausdrücklich: „Rückgängig“ muss das archivierte Rezept treffen, auch wenn inzwischen ein anderes offen ist. */
    suspend fun archivieren(rezeptId: String, an: Boolean) = repo.archivieren(rezeptId, an)

    suspend fun duplizieren(): String? = id.value?.let { repo.duplizieren(it) }

    /** Zutaten für [portionen] auf die aktuelle Einkaufsliste; gibt die Zahl der Artikel zurück (0 = nichts zu kaufen). */
    suspend fun aufEinkaufsliste(portionen: Int): Int = id.value?.let { einkauf.rezeptAufListe(it, portionen) } ?: 0

    private companion object {
        const val STOPP_MS = 5_000L
    }
}
