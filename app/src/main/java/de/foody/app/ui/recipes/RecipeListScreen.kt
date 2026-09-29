package de.foody.app.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.common.EmptyState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecipeListUiState(
    val query: String = "",
    val showArchived: Boolean = false,
    val recipes: List<RecipeEntity> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    repo: RecipeRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val query = saved.getStateFlow("query", "")
    private val archived = saved.getStateFlow("archived", false)

    val state = combine(query, archived) { q, a -> q to a }
        .flatMapLatest { (q, a) -> repo.observe(q.trim(), a).map { RecipeListUiState(q, a, it, false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState())

    fun onQuery(q: String) { saved["query"] = q }
    fun onToggleArchived() { saved["archived"] = !archived.value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(onOpen: (String) -> Unit, onCreate: () -> Unit, vm: RecipeListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_recipes)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, stringResource(R.string.recipe_new)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.recipe_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(state.showArchived, vm::onToggleArchived, label = { Text(stringResource(R.string.recipe_show_archived)) })
            }
            if (!state.loading && state.recipes.isEmpty()) {
                EmptyState(stringResource(if (state.query.isBlank()) R.string.recipe_empty else R.string.recipe_no_match))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.recipes, key = { it.id }) { r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = {
                                val time = (r.prepMinutes ?: 0) + (r.cookMinutes ?: 0)
                                Text(
                                    buildList {
                                        add(stringResource(R.string.servings_count, r.defaultServings))
                                        if (time > 0) add(stringResource(R.string.minutes_total, time))
                                        if (r.tags.isNotBlank()) add(r.tags)
                                    }.joinToString(" · "),
                                )
                            },
                            leadingContent = if (r.imageUri != null) {
                                { AsyncImage(r.imageUri, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp)) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable { onOpen(r.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
