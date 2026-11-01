package de.foody.feature.rezepte.liste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.SolangeAppOffen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [keineRezepte]: es gibt überhaupt keine (auch keine archivierten) – dann „Anlegen“/„Importieren“. */
data class ListeZustand(
    val geladen: Boolean = false,
    val filter: ListeFilter = ListeFilter(),
    val treffer: List<RezeptKarte> = emptyList(),
    val keineRezepte: Boolean = false,
    val tags: List<String> = emptyList(),
    /** Rezepte ohne Archiv (Überzeile „296 Rezepte“). */
    val anzahl: Int = 0,
)

@HiltViewModel
class RezeptListeViewModel @Inject constructor(repo: RezeptRepository) : ViewModel() {
    private val filter = MutableStateFlow(ListeFilter())

    val zustand: StateFlow<ListeZustand> = combine(repo.karten(), filter) { karten, f ->
        ListeZustand(true, f, filtern(karten, f), karten.isEmpty(), haeufigeTags(karten), karten.count { !it.archiviert })
    }.stateIn(viewModelScope, SolangeAppOffen, ListeZustand())

    fun filter(aendern: (ListeFilter) -> ListeFilter) = filter.update(aendern)

    fun zuruecksetzen() = filter.update { ListeFilter(sortierung = it.sortierung) }
}
