package de.foody.app.ui.recipes

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.outlined.MenuBook
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.ImportResult
import de.foody.app.data.repo.RecipeImportRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.common.EmptyState
import de.foody.app.ui.common.RecipeGridCard
import de.foody.app.ui.common.RecipeHeroCard
import de.foody.app.ui.theme.EyebrowStyle
import de.foody.domain.DailyPicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecipeListUiState(
    val query: String = "",
    val showArchived: Boolean = false,
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    val recipes: List<RecipeEntity> = emptyList(),
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
    private val importer: RecipeImportRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val query = saved.getStateFlow("query", "")
    private val archived = saved.getStateFlow("archived", false)
    private val tag = saved.getStateFlow<String?>("tag", null)
    private val favoritesOnly = saved.getStateFlow("favorites", false)

    /** Tag- und Favoritenfilter wirken im Speicher auf das Suchergebnis. */
    private data class LocalFilter(val tag: String?, val favoritesOnly: Boolean)
    private val localFilter = combine(tag, favoritesOnly, ::LocalFilter)

    /** Aktive Rezepte – Grundlage für Tagesauswahl und Tags, beim Stöbern auch für das Raster. */
    private val active = repo.observeActive().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Ohne Suchbegriff und ohne Archiv ist das Suchergebnis gleich den aktiven Rezepten:
    // dann dieselbe Abfrage teilen statt eine zweite (mit Zutaten-Unterabfrage) zu beobachten.
    private val filtered = combine(query, archived) { q, a -> q.trim() to a }
        .distinctUntilChanged()
        .flatMapLatest { (q, a) -> if (q.isEmpty() && !a) active else repo.observe(q, a) }

    val state = combine(query, archived, localFilter, filtered, active) { q, a, f, list, active ->
        RecipeListUiState(
            query = q,
            showArchived = a,
            tag = f.tag,
            favoritesOnly = f.favoritesOnly,
            recipes = list.filter { r -> (f.tag == null || f.tag in r.tagList()) && (!f.favoritesOnly || r.favorite) },
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

    /** Einmaliges Import-Ergebnis; die UI quittiert es nach Anzeige. */
    data class ImportMessage(val imported: Int, val failed: Int, val skipped: Int, val openId: String?)

    private val _importMessage = MutableStateFlow<ImportMessage?>(null)
    val importMessage = _importMessage.asStateFlow()
    fun importMessageShown() { _importMessage.value = null }

    /** Fortschritt eines laufenden Imports (erledigt, gesamt); null, wenn keiner läuft. */
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress = _importProgress.asStateFlow()

    fun import(uris: List<Uri>, tag: String, notes: (String?) -> String) = launchImport {
        // Rezept-Exporte enthalten keine Portionenzahl → 4 als Vorgabe, im Hinweis zur Prüfung markiert
        importer.import(uris, DEFAULT_IMPORT_SERVINGS, tag, notes, ::onProgress)
    }

    fun importFolder(tree: Uri, tag: String, notes: (String?) -> String) = launchImport {
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
    }
}

@Composable
fun RecipeListScreen(onOpen: (String) -> Unit, onCreate: () -> Unit, vm: RecipeListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()
    val importProgress by vm.importProgress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val importTag = stringResource(R.string.import_tag)
    val ideasTag = stringResource(R.string.import_tag_ideas)
    val notes: (String?) -> String = { source ->
        listOfNotNull(
            source?.let { resources.getString(R.string.import_source, it) },
            resources.getString(R.string.import_check_servings, RecipeListViewModel.DEFAULT_IMPORT_SERVINGS),
        ).joinToString("\n")
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.import(uris, importTag, notes)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { vm.importFolder(it, ideasTag, notes) }
    }
    LaunchedEffect(importMessage) {
        val m = importMessage ?: return@LaunchedEffect
        vm.importMessageShown()
        m.openId?.let(onOpen)
        val text = buildList {
            add(resources.getQuantityString(R.plurals.import_done, m.imported, m.imported))
            if (m.skipped > 0) add(resources.getString(R.string.import_skipped, m.skipped))
            if (m.failed > 0) add(resources.getQuantityString(R.plurals.import_failed, m.failed, m.failed))
        }.joinToString(", ")
        snackbar.showSnackbar(text)
    }
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.recipe_new)) },
                // Eingeklappt, sobald gescrollt wird – verdeckt dann keine Kartentitel
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        val browsing = state.query.isBlank() && state.tag == null && !state.showArchived
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(onImportFiles = { filesLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, onImportFolder = { folderLauncher.launch(null) })
            }
            importProgress?.let { (done, total) ->
                item(span = { GridItemSpan(maxLineSpan) }) { ImportProgress(done, total) }
            }
            if (browsing && state.dailyPicks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { DailyPicksPager(state.dailyPicks, onOpen) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SearchBar(state.query, vm::onQuery) }
            item(span = { GridItemSpan(maxLineSpan) }) { TagRow(state, vm::onTag, vm::onToggleArchived, vm::onToggleFavorites) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(if (state.showArchived) R.string.recipe_section_archive else R.string.recipe_section_all, state.recipes.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!state.loading && state.recipes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(if (browsing) R.string.recipe_empty else R.string.recipe_no_match),
                        Modifier.height(320.dp),
                        icon = if (browsing) Icons.Outlined.MenuBook else Icons.Default.Search,
                        actionLabel = if (browsing) stringResource(R.string.import_folder) else null,
                        onAction = if (browsing) ({ folderLauncher.launch(null) }) else null,
                    )
                }
            }
            items(state.recipes, key = { it.id }) { r -> RecipeGridCard(r, onClick = { onOpen(r.id) }, modifier = Modifier.animateItem()) }
        }
        // Scrim hinter der Statusleiste, damit gescrollte Inhalte nicht mit der Uhrzeit kollidieren
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)))
    }
}

@Composable
private fun Header(onImportFiles: () -> Unit, onImportFolder: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_eyebrow).uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        }
        Box {
            FilledTonalIconButton({ menu = true }) { Icon(Icons.Outlined.FileDownload, stringResource(R.string.import_action)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_files)) },
                    leadingIcon = { Icon(Icons.Outlined.Description, null) },
                    onClick = { menu = false; onImportFiles() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                    onClick = { menu = false; onImportFolder() },
                )
            }
        }
    }
}

@Composable
private fun DailyPicksPager(picks: List<RecipeEntity>, onOpen: (String) -> Unit) {
    val pager = rememberPagerState { picks.size }
    val eyebrow = stringResource(R.string.daily_picks)
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(end = 28.dp),
            modifier = Modifier.fillMaxWidth().height(300.dp),
        ) { page ->
            val r = picks[page]
            RecipeHeroCard(r, "$eyebrow · ${page + 1}/${picks.size}", onClick = { onOpen(r.id) }, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(picks.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (active) 20.dp else 6.dp).clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(stringResource(R.string.recipe_search)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun TagRow(state: RecipeListUiState, onTag: (String?) -> Unit, onToggleArchived: () -> Unit, onToggleFavorites: () -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                state.tag == null && !state.showArchived && !state.favoritesOnly,
                { onTag(null); if (state.showArchived) onToggleArchived(); if (state.favoritesOnly) onToggleFavorites() },
                label = { Text(stringResource(R.string.filter_all)) }, colors = chipColors, shape = RoundedCornerShape(50),
            )
        }
        item {
            FilterChip(
                state.favoritesOnly, onToggleFavorites,
                label = { Text(stringResource(R.string.filter_favorites)) },
                leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(16.dp)) },
                colors = chipColors, shape = RoundedCornerShape(50),
            )
        }
        items(state.tags) { t ->
            FilterChip(state.tag == t, { onTag(t) }, label = { Text(t) }, colors = chipColors, shape = RoundedCornerShape(50))
        }
        item {
            FilterChip(state.showArchived, onToggleArchived, label = { Text(stringResource(R.string.recipe_show_archived)) },
                colors = chipColors, shape = RoundedCornerShape(50))
        }
        item { Spacer(Modifier.size(4.dp)) }
    }
}

@Composable
private fun ImportProgress(done: Int, total: Int) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (total == 0) stringResource(R.string.import_running) else stringResource(R.string.import_progress, done, total),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (total == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        }
    }
}
