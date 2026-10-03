package de.foody.app.ui.recipes

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.ImportResult
import de.foody.app.data.repo.PantryRepository
import de.foody.app.data.repo.RecipeImportRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.DailyPicks
import de.foody.domain.PantryCoverage
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeListUiState(
    val query: String = "",
    val showArchived: Boolean = false,
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    /** Nur Rezepte, für die im Vorrat höchstens eine Pflichtzutat fehlt. */
    val pantryOnly: Boolean = false,
    val recipes: List<RecipeEntity> = emptyList(),
    /** Fehlende Pflichtzutaten je Rezept-ID – nur bei [pantryOnly] gefüllt, für das Abzeichen auf der Karte. */
    val missing: Map<String, Int> = emptyMap(),
    /** Tags aller aktiven Rezepte, häufigste zuerst – für die Filter-Chips. */
    val tags: List<String> = emptyList(),
    /** Tagesauswahl aus allen aktiven Rezepten, unabhängig von Suche und Filter. */
    val dailyPicks: List<RecipeEntity> = emptyList(),
    val loading: Boolean = true,
)

private fun RecipeEntity.tagList() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    repo: RecipeRepository,
    pantry: PantryRepository,
    private val importer: RecipeImportRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val query = saved.getStateFlow("query", "")
    private val archived = saved.getStateFlow("archived", false)
    private val tag = saved.getStateFlow<String?>("tag", null)
    private val favoritesOnly = saved.getStateFlow("favorites", false)
    private val pantryOnly = saved.getStateFlow("pantry", false)

    /** Tag-, Favoriten- und Vorratsfilter wirken im Speicher auf das Suchergebnis. */
    private data class LocalFilter(val tag: String?, val favoritesOnly: Boolean, val pantryOnly: Boolean)
    private val localFilter = combine(tag, favoritesOnly, pantryOnly, ::LocalFilter)

    /** Fehlende Pflichtzutaten je Rezept; nur Vorratseinträge mit Menge zählen als vorhanden. */
    private val missing = combine(repo.observeRequired(), pantry.observeAll()) { required, items ->
        PantryCoverage.missingByRecipe(required, items.filter { it.amount.signum() > 0 }.map { it.ingredientId }.toSet())
    }

    /** Aktive Rezepte – Grundlage für Tagesauswahl und Tags, beim Stöbern auch für das Raster. */
    private val active = repo.observeActive().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Ohne Suchbegriff und ohne Archiv ist das Suchergebnis gleich den aktiven Rezepten:
    // dann dieselbe Abfrage teilen statt eine zweite (mit Zutaten-Unterabfrage) zu beobachten.
    private val filtered = combine(query, archived) { q, a -> q.trim() to a }
        .distinctUntilChanged()
        .flatMapLatest { (q, a) -> if (q.isEmpty() && !a) active else repo.observe(q, a) }

    // Vorratsabgleich nur berechnen, solange der Filter an ist
    private val missingIfNeeded = pantryOnly.flatMapLatest { on -> if (on) missing else flowOf(emptyMap()) }

    val state = combine(query, archived, localFilter, combine(filtered, missingIfNeeded, ::Pair), active) { q, a, f, (list, missing), active ->
        val matching = list.filter { r ->
            (f.tag == null || f.tag in r.tagList()) && (!f.favoritesOnly || r.favorite) &&
                (!f.pantryOnly || (missing[r.id] ?: Int.MAX_VALUE) <= MAX_MISSING)
        }
        RecipeListUiState(
            query = q,
            showArchived = a,
            tag = f.tag,
            favoritesOnly = f.favoritesOnly,
            pantryOnly = f.pantryOnly,
            // Stabil sortiert: erst „Alles da“, dann „1 fehlt“ – innerhalb jeweils die gewohnte Reihenfolge
            recipes = if (f.pantryOnly) matching.sortedBy { missing[it.id] } else matching,
            missing = if (f.pantryOnly) missing else emptyMap(),
            tags = active.flatMap { it.tagList() }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }.take(8),
            dailyPicks = DailyPicks.pick(active, LocalDate.now()) { it.id },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun onQuery(q: String) { saved["query"] = q }
    fun onToggleArchived() { saved["archived"] = !archived.value }
    fun onTag(t: String?) { saved["tag"] = if (tag.value == t) null else t }
    fun onToggleFavorites() { saved["favorites"] = !favoritesOnly.value }
    fun onTogglePantry() { saved["pantry"] = !pantryOnly.value }

    /** Einmaliges Import-Ergebnis; die UI quittiert es nach Anzeige. */
    data class ImportMessage(val imported: Int, val failed: Int, val skipped: Int, val openId: String?)

    private val _importMessage = MutableStateFlow<ImportMessage?>(null)
    val importMessage = _importMessage.asStateFlow()
    fun importMessageShown() { _importMessage.value = null }

    /** Fortschritt eines laufenden Imports (erledigt, gesamt); null, wenn keiner läuft. */
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress = _importProgress.asStateFlow()

    fun import(uris: List<Uri>, tag: String, notes: (String?, Int, Boolean) -> String) = launchImport {
        // Rezept-Exporte enthalten keine Portionenzahl → 4 als Vorgabe, im Hinweis zur Prüfung markiert
        importer.import(uris, DEFAULT_IMPORT_SERVINGS, tag, notes, ::onProgress)
    }

    fun importFolder(tree: Uri, tag: String, notes: (String?, Int, Boolean) -> String) = launchImport {
        importer.importFolder(tree, DEFAULT_IMPORT_SERVINGS, tag, notes, ::onProgress)
    }

    private fun onProgress(done: Int, total: Int) { _importProgress.value = done to total }

    private fun launchImport(block: suspend () -> ImportResult) = viewModelScope.launch {
        _importProgress.value = 0 to 0
        try {
            val result = block()
            _importMessage.value = ImportMessage(result.importedIds.size, result.failed, result.skipped, result.importedIds.singleOrNull())
        } finally {
            _importProgress.value = null
        }
    }

    companion object {
        const val DEFAULT_IMPORT_SERVINGS = 4
        /** Eine fehlende Zutat ist meist schnell besorgt; strenger bliebe die Liste fast immer leer. */
        const val MAX_MISSING = 1
    }
}
