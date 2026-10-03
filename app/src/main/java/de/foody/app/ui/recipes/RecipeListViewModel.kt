package de.foody.app.ui.recipes

import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.toDomain
import de.foody.domain.Diet
import de.foody.domain.RecipeProfile
import de.foody.domain.RecipeProfiles
import de.foody.domain.RecipeSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
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
    /** Gewählte Ernährungsfilter (alle müssen zutreffen) und Sortierung. */
    val diets: Set<Diet> = emptySet(),
    val sort: RecipeSort = RecipeSort.NAME,
    /** Kurzprofil je Rezept-ID (kcal je Portion, Ernährungsform) – für Filter, Sortierung und Karten. */
    val profiles: Map<String, RecipeProfile> = emptyMap(),
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
    ingredients: IngredientRepository,
    plan: PlanRepository,
    private val importer: RecipeImportRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val query = saved.getStateFlow("query", "")
    private val archived = saved.getStateFlow("archived", false)
    private val tag = saved.getStateFlow<String?>("tag", null)
    private val favoritesOnly = saved.getStateFlow("favorites", false)
    private val pantryOnly = saved.getStateFlow("pantry", false)
    // Als Text gespeichert, damit SavedStateHandle sie ohne eigenen Saver übersteht
    private val dietsRaw = saved.getStateFlow("diets", "")
    private val sortRaw = saved.getStateFlow("sort", RecipeSort.NAME.name)

    /** Tag-, Favoriten-, Vorrats- und Ernährungsfilter wirken im Speicher auf das Suchergebnis. */
    private data class LocalFilter(
        val tag: String?, val favoritesOnly: Boolean, val pantryOnly: Boolean, val diets: Set<Diet>, val sort: RecipeSort,
    )
    private val localFilter = combine(tag, favoritesOnly, pantryOnly, dietsRaw, sortRaw) { t, fav, pan, d, s ->
        LocalFilter(t, fav, pan, d.split(',').mapNotNull { runCatching { Diet.valueOf(it) }.getOrNull() }.toSet(),
            runCatching { RecipeSort.valueOf(s) }.getOrDefault(RecipeSort.NAME))
    }

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

    /**
     * Kurzprofil (kcal je Portion, Ernährungsform) aller aktiven Rezepte. Bei rund hundert Rezepten ein paar
     * Millisekunden – im Hintergrund-Thread, und nur neu, wenn sich Rezepte, Zeilen oder Zutaten ändern.
     */
    private val profiles = combine(active, repo.observeAllLines(), ingredients.observeAll()) { recipes, lines, all ->
        val ingMap = all.associate { it.id to it.toDomain() }
        val byRecipe = lines.groupBy { it.recipeId }
        recipes.associate { r -> r.id to RecipeProfiles.profile(r.toDomain(byRecipe[r.id].orEmpty()), ingMap) }
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Vorratsabgleich nur berechnen, solange der Filter an ist
    private val missingIfNeeded = pantryOnly.flatMapLatest { on -> if (on) missing else flowOf(emptyMap()) }

    private data class ListInputs(
        val list: List<RecipeEntity>, val missing: Map<String, Int>, val profiles: Map<String, RecipeProfile>, val cooked: Map<String, Int>,
    )

    val state = combine(
        query, archived, localFilter,
        combine(filtered, missingIfNeeded, profiles.onStart { emit(emptyMap()) }, plan.observeCookedCounts(), ::ListInputs), active,
    ) { q, a, f, (list, missing, profiles, cooked), active ->
        val matching = list.filter { r ->
            (f.tag == null || f.tag in r.tagList()) && (!f.favoritesOnly || r.favorite) &&
                (!f.pantryOnly || (missing[r.id] ?: Int.MAX_VALUE) <= MAX_MISSING) &&
                (f.diets.isEmpty() || profiles[r.id]?.diets?.containsAll(f.diets) == true)
        }.let { sortRecipes(it, f.sort, profiles, cooked) }
        RecipeListUiState(
            query = q,
            showArchived = a,
            tag = f.tag,
            favoritesOnly = f.favoritesOnly,
            pantryOnly = f.pantryOnly,
            // Stabil sortiert: erst „Alles da“, dann „1 fehlt“ – innerhalb jeweils die gewohnte Reihenfolge
            recipes = if (f.pantryOnly) matching.sortedBy { missing[it.id] } else matching,
            missing = if (f.pantryOnly) missing else emptyMap(),
            diets = f.diets,
            sort = f.sort,
            profiles = profiles,
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
    fun onToggleDiet(d: Diet) {
        val now = dietsRaw.value.split(',').filter { it.isNotEmpty() }.toSet()
        saved["diets"] = (if (d.name in now) now - d.name else now + d.name).joinToString(",")
    }
    fun onSort(s: RecipeSort) { saved["sort"] = s.name }
    fun resetDiscover() { saved["diets"] = ""; saved["sort"] = RecipeSort.NAME.name }

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

/** Sortierung; Rezepte ohne verlässliche Nährwerte stehen bei Nährwert-Sortierungen hinten. */
private fun sortRecipes(
    list: List<RecipeEntity>,
    sort: RecipeSort,
    profiles: Map<String, RecipeProfile>,
    cooked: Map<String, Int>,
): List<RecipeEntity> =
    when (sort) {
        RecipeSort.NAME -> list
        RecipeSort.NEWEST -> list.sortedByDescending { it.createdAt }
        RecipeSort.KCAL_ASC -> list.sortedBy { r -> profiles[r.id]?.takeIf { it.reliable }?.kcalPerServing ?: Int.MAX_VALUE }
        RecipeSort.PROTEIN_DESC -> list.sortedByDescending { r -> profiles[r.id]?.takeIf { it.reliable }?.proteinPerServing ?: -1 }
        // Unbewertete hinten; bei gleicher Bewertung bleibt die Namensreihenfolge (stabile Sortierung)
        RecipeSort.BEST_RATED -> list.sortedByDescending { it.rating ?: 0 }
        RecipeSort.MOST_COOKED -> list.sortedByDescending { cooked[it.id] ?: 0 }
    }
