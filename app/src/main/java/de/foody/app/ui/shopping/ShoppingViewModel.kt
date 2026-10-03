package de.foody.app.ui.shopping

import de.foody.domain.ShoppingCatalog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.repo.NeedPreview
import de.foody.app.data.repo.ShoppingRepository
import de.foody.domain.DateRange
import de.foody.domain.ShoppingDiffEntry
import de.foody.domain.ShoppingListSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GenerateState(
    val start: LocalDate = LocalDate.now(),
    val days: Int = 7,
    val usePantry: Boolean = true,
    val excluded: Set<String> = emptySet(),
    val previews: List<NeedPreview> = emptyList(),
    val loading: Boolean = false,
)

data class DiffState(
    val entries: List<ShoppingDiffEntry<ShoppingItemEntity>>,
    val previews: List<NeedPreview>,
)

data class ShoppingUiState(
    val lists: List<ShoppingListEntity> = emptyList(),
    val selected: ShoppingListEntity? = null,
    val items: List<ShoppingItemEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repo: ShoppingRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val selectedId = saved.getStateFlow<String?>("listId", null)

    val state = combine(
        repo.observeLists(),
        selectedId,
        selectedId.flatMapLatest { id -> id?.let(repo::observeItems) ?: flowOf(emptyList()) },
    ) { lists, id, items ->
        val selected = lists.firstOrNull { it.id == id } ?: lists.firstOrNull()
        ShoppingUiState(lists, selected, if (selected?.id == id) items else emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    init {
        // Standardmäßig die neueste Liste anzeigen.
        viewModelScope.launch {
            repo.observeLists().collect { lists ->
                if (selectedId.value == null || lists.none { it.id == selectedId.value }) saved["listId"] = lists.firstOrNull()?.id
            }
        }
    }

    private val _generate = MutableStateFlow<GenerateState?>(null)
    val generate = _generate.asStateFlow()

    private val _diff = MutableStateFlow<DiffState?>(null)
    val diff = _diff.asStateFlow()

    private val _sources = MutableStateFlow<Pair<ShoppingItemEntity, List<ShoppingItemSourceEntity>>?>(null)
    val sources = _sources.asStateFlow()

    /** Zuletzt gelöschter Eintrag für „Rückgängig“. */
    private val _undo = MutableStateFlow<Pair<ShoppingItemEntity, List<ShoppingItemSourceEntity>>?>(null)
    val undo = _undo.asStateFlow()

    fun select(id: String) { saved["listId"] = id }

    fun openGenerate() { _generate.value = GenerateState(); refreshPreview() }
    fun closeGenerate() { _generate.value = null }
    fun updateGenerate(f: (GenerateState) -> GenerateState) { _generate.value = _generate.value?.let(f); refreshPreview() }
    fun toggleExcluded(ingredientId: String) {
        _generate.value = _generate.value?.let { g ->
            g.copy(excluded = if (ingredientId in g.excluded) g.excluded - ingredientId else g.excluded + ingredientId)
        }
    }

    private fun refreshPreview() {
        val g = _generate.value ?: return
        _generate.value = g.copy(loading = true)
        viewModelScope.launch {
            val previews = repo.preview(DateRange.ofDays(g.start, g.days), g.usePantry)
            _generate.value = _generate.value?.copy(previews = previews, loading = false)
        }
    }

    fun createList(name: String) {
        val g = _generate.value ?: return
        viewModelScope.launch {
            val range = DateRange.ofDays(g.start, g.days)
            val previews = repo.preview(range, g.usePantry, g.excluded)
            saved["listId"] = repo.createSnapshot(name, range, previews)
            _generate.value = null
        }
    }

    fun createEmpty(name: String) = viewModelScope.launch { saved["listId"] = repo.createEmptyList(name) }

    fun recalculate(usePantry: Boolean = true) {
        val id = state.value.selected?.id ?: return
        viewModelScope.launch {
            val (entries, previews) = repo.diff(id, usePantry)
            _diff.value = DiffState(entries, previews)
        }
    }
    fun applyDiff() {
        val id = state.value.selected?.id ?: return
        val d = _diff.value ?: return
        viewModelScope.launch { repo.applyDiff(id, d.entries, d.previews); _diff.value = null }
    }
    fun dismissDiff() { _diff.value = null }

    fun toggle(item: ShoppingItemEntity) = viewModelScope.launch { repo.setChecked(item, !item.checked) }

    /** Liste, in die Kacheln eintragen; ohne Liste wird beim ersten Antippen eine leere angelegt. */
    private suspend fun ensureList(defaultName: String): String =
        state.value.selected?.id ?: repo.createEmptyList(defaultName).also { saved["listId"] = it }

    /**
     * Antippen einer Katalog-Kachel: Nicht auf der Liste → hinzufügen; auf der Liste → gekauft;
     * zuletzt gekauft → wieder auf die Liste. Verglichen wird über den Artikel, nicht den genauen Namen.
     */
    fun tapCatalog(name: String, section: String, defaultListName: String) = viewModelScope.launch {
        val existing = state.value.items.filter { ShoppingCatalog.sameItem(it.name, name) }
        val open = existing.firstOrNull { !it.checked }
        val bought = existing.firstOrNull { it.checked }
        when {
            open != null -> repo.setChecked(open, true)
            bought != null -> repo.setChecked(bought, false)
            else -> repo.addManual(ensureList(defaultListName), name, section)
        }
    }

    /** Freitext aus „Was willst du einkaufen?“: bekannter Artikel wie seine Kachel, sonst als eigener Artikel. */
    fun addFromSearch(text: String, defaultListName: String) {
        val name = text.trim().takeIf { it.isNotEmpty() } ?: return
        val known = ShoppingCatalog.find(name)
        if (known != null) {
            tapCatalog(known.name, ShoppingCatalog.sectionFor(null, known.name), defaultListName)
        } else {
            viewModelScope.launch { repo.addManual(ensureList(defaultListName), name, ShoppingCatalog.OWN_ITEMS) }
        }
    }
    fun delete(item: ShoppingItemEntity) = viewModelScope.launch {
        val sources = repo.getSources(item.id)
        repo.deleteItem(item.id)
        _undo.value = item to sources
    }
    fun undoDelete() { _undo.value?.let { (i, s) -> viewModelScope.launch { repo.restoreItem(i, s) } }; _undo.value = null }
    fun undoShown() { _undo.value = null }
    fun deleteList() { state.value.selected?.id?.let { viewModelScope.launch { repo.deleteList(it) } } }

    fun showSources(item: ShoppingItemEntity) = viewModelScope.launch { _sources.value = item to repo.getSources(item.id) }
    fun hideSources() { _sources.value = null }

    suspend fun snapshot(): ShoppingListSnapshot? = state.value.selected?.id?.let { repo.snapshot(it) }
}
