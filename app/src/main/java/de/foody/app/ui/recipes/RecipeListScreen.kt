package de.foody.app.ui.recipes

import de.foody.app.ui.common.label
import de.foody.domain.Gang
import de.foody.domain.Diet
import de.foody.domain.RecipeSort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import de.foody.app.ui.theme.FoodyGlass
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.RecipeEntity
import de.foody.app.ui.common.EmptyState
import de.foody.app.ui.common.RecipeGridCard
import de.foody.app.ui.common.RecipeHeroCard
import de.foody.app.ui.theme.EyebrowStyle
import kotlinx.coroutines.launch

@Composable
fun RecipeListScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    selectedId: String? = null,
    vm: RecipeListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()
    val importProgress by vm.importProgress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    val importTag = stringResource(R.string.import_tag)
    val ideasTag = stringResource(R.string.import_tag_ideas)
    val notes: (String?, Int, Boolean) -> String = { source, servings, estimated ->
        listOfNotNull(
            source?.let { resources.getString(R.string.import_source, it) },
            resources.getString(if (estimated) R.string.import_estimated_servings else R.string.import_check_servings, servings),
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
        containerColor = Color.Transparent,
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
        val browsing = state.query.isBlank() && state.tag == null && !state.showArchived && !state.pantryOnly && state.diets.isEmpty() &&
            state.gaenge.isEmpty() && !state.nurNichtEingeordnet
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Feste Schlüssel: Verschwindet ein Eintrag darüber (Tagesauswahl beim Suchen), behält das Suchfeld
            // seine Identität – sonst würde es neu aufgebaut und verlöre nach dem ersten Buchstaben den Fokus.
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Header(onImportFiles = { filesLauncher.launch(arrayOf("text/*", "application/octet-stream")) }, onImportFolder = { folderLauncher.launch(null) })
            }
            importProgress?.let { (done, total) ->
                item(key = "import", span = { GridItemSpan(maxLineSpan) }) { ImportProgress(done, total) }
            }
            if (browsing && state.dailyPicks.isNotEmpty()) {
                item(key = "daily", span = { GridItemSpan(maxLineSpan) }) { DailyPicksPager(state.dailyPicks, onOpen) }
            }
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) { SearchBar(state.query, vm::onQuery) }
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { TagRow(state, vm::onTag, vm::onToggleArchived, vm::onToggleFavorites, vm::onTogglePantry, onOpenFilter = { showFilter = true }) }
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(if (state.showArchived) R.string.recipe_section_archive else R.string.recipe_section_all, state.recipes.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!state.loading && state.recipes.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        stringResource(
                            when {
                                browsing -> R.string.recipe_empty
                                state.pantryOnly -> R.string.recipe_pantry_none
                                else -> R.string.recipe_no_match
                            },
                        ),
                        Modifier.height(320.dp),
                        icon = when {
                            browsing -> Icons.AutoMirrored.Outlined.MenuBook
                            state.pantryOnly -> Icons.Outlined.Inventory2
                            else -> Icons.Default.Search
                        },
                        actionLabel = if (browsing) stringResource(R.string.import_folder) else null,
                        onAction = if (browsing) ({ folderLauncher.launch(null) }) else null,
                    )
                }
            }
            items(state.recipes, key = { it.id }) { r ->
                val badge = state.missing[r.id]?.let { n ->
                    if (n == 0) stringResource(R.string.pantry_all_there) else pluralStringResource(R.plurals.pantry_missing, n, n)
                }
                RecipeGridCard(
                    r, onClick = { onOpen(r.id) }, modifier = Modifier.animateItem(), selected = r.id == selectedId, badge = badge,
                    kcal = state.profiles[r.id]?.takeIf { it.reliable }?.kcalPerServing,
                )
            }
        }
        // Scrim hinter der Statusleiste, damit gescrollte Inhalte nicht mit der Uhrzeit kollidieren
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(FoodyGlass.fill))
    }
    if (showFilter) {
        DiscoverSheet(state, vm::onToggleDiet, vm::onToggleGang, vm::onToggleNichtEingeordnet, vm::onSort, vm::resetDiscover, onDismiss = { showFilter = false })
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
private fun TagRow(
    state: RecipeListUiState,
    onTag: (String?) -> Unit,
    onToggleArchived: () -> Unit,
    onToggleFavorites: () -> Unit,
    onTogglePantry: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            // Ernährung und Sortierung im Blatt – die Chip-Zeile bleibt kurz
            val active = state.diets.size + state.gaenge.size + (if (state.nurNichtEingeordnet) 1 else 0) + if (state.sort != RecipeSort.NAME) 1 else 0
            FilterChip(
                active > 0, onOpenFilter,
                label = { Text(if (active > 0) stringResource(R.string.filter_button_count, active) else stringResource(R.string.filter_button)) },
                leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) },
                colors = chipColors, shape = RoundedCornerShape(50),
            )
        }
        item {
            FilterChip(
                state.tag == null && !state.showArchived && !state.favoritesOnly && !state.pantryOnly && state.diets.isEmpty() &&
                    state.gaenge.isEmpty() && !state.nurNichtEingeordnet,
                {
                    onTag(null)
                    if (state.showArchived) onToggleArchived()
                    if (state.favoritesOnly) onToggleFavorites()
                    if (state.pantryOnly) onTogglePantry()
                },
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
        item {
            FilterChip(
                state.pantryOnly, onTogglePantry,
                label = { Text(stringResource(R.string.filter_pantry)) },
                leadingIcon = { Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp)) },
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

/** Ernährungsfilter und Sortierung als Blatt (wie Filter in Rezept-Apps), damit die Chip-Zeile übersichtlich bleibt. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DiscoverSheet(
    state: RecipeListUiState,
    onToggleDiet: (Diet) -> Unit,
    onToggleGang: (Gang) -> Unit,
    onToggleNichtEingeordnet: () -> Unit,
    onSort: (RecipeSort) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Gleiche Hervorhebung wie die Chip-Zeile: ausgewählt = dunkel, sonst wären Zustände kaum zu unterscheiden
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_diet_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onReset) { Text(stringResource(R.string.filter_reset)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Diet.VEGETARIAN to R.string.diet_vegetarian, Diet.VEGAN to R.string.diet_vegan,
                    Diet.HIGH_PROTEIN to R.string.diet_high_protein, Diet.LOW_CARB to R.string.diet_low_carb,
                    Diet.LIGHT to R.string.diet_light,
                ).forEach { (d, label) ->
                    FilterChip(d in state.diets, { onToggleDiet(d) }, label = { Text(stringResource(label)) }, shape = RoundedCornerShape(50), colors = chipColors)
                }
            }
            Text(stringResource(R.string.filter_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.einordnung_gang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Gang.entries.forEach { g ->
                    FilterChip(g in state.gaenge, { onToggleGang(g) }, label = { Text(stringResource(g.label())) }, shape = RoundedCornerShape(50), colors = chipColors)
                }
                FilterChip(state.nurNichtEingeordnet, onToggleNichtEingeordnet, label = { Text(stringResource(R.string.filter_nicht_eingeordnet)) },
                    shape = RoundedCornerShape(50), colors = chipColors)
            }
            Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RecipeSort.NAME to R.string.sort_name, RecipeSort.NEWEST to R.string.sort_newest,
                    RecipeSort.KCAL_ASC to R.string.sort_kcal, RecipeSort.PROTEIN_DESC to R.string.sort_protein,
                    RecipeSort.BEST_RATED to R.string.sort_rating, RecipeSort.MOST_COOKED to R.string.sort_cooked,
                ).forEach { (s, label) ->
                    FilterChip(state.sort == s, { onSort(s) }, label = { Text(stringResource(label)) }, shape = RoundedCornerShape(50), colors = chipColors)
                }
            }
            Text(
                pluralStringResource(R.plurals.filter_result_count, state.recipes.size, state.recipes.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
