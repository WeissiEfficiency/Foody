package de.foody.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.toDomain
import de.foody.app.ui.RecipeDetailRoute
import de.foody.app.ui.common.SectionTitle
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.display
import de.foody.app.ui.common.formatAmount
import de.foody.app.ui.common.label
import de.foody.domain.Nutrient
import de.foody.domain.NutritionCalculator
import de.foody.domain.NutritionResult
import de.foody.domain.RecipeScaler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class DisplayLine(val name: String, val amountText: String, val note: String?, val optional: Boolean)

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val servings: Int = 1,
    val lines: List<DisplayLine> = emptyList(),
    val steps: List<InstructionStepEntity> = emptyList(),
    val nutrition: NutritionResult? = null,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val repo: RecipeRepository,
    ingredients: IngredientRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val id = saved.toRoute<RecipeDetailRoute>().id
    private val servingsOverride = saved.getStateFlow<Int?>("servings", null)

    val state = combine(
        repo.observeRecipe(id), repo.observeIngredients(id), repo.observeSteps(id), ingredients.observeAll(), servingsOverride,
    ) { recipe, lines, steps, allIngredients, override ->
        if (recipe == null) return@combine RecipeDetailUiState()
        val servings = override ?: recipe.defaultServings
        val ingMap = allIngredients.associate { it.id to it.toDomain() }
        RecipeDetailUiState(
            recipe = recipe,
            servings = servings,
            lines = lines.map { l ->
                val scaled = RecipeScaler.scale(l.amount, recipe.defaultServings, servings)
                DisplayLine(ingMap[l.ingredientId]?.name.orEmpty(), formatAmount(scaled, l.unit), l.preparationNote, l.optional)
            },
            steps = steps,
            nutrition = NutritionCalculator.calculate(recipe.toDomain(lines), ingMap, servings),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun setServings(n: Int) { saved["servings"] = n }
    fun archive(archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
    fun duplicate(suffix: String, onDone: (String) -> Unit) = viewModelScope.launch { repo.duplicate(id, suffix)?.let(onDone) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenOther: (String) -> Unit,
    vm: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.recipe_copy_suffix)
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name.orEmpty()) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                    IconButton({ vm.duplicate(copySuffix, onOpenOther) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.action_duplicate)) }
                    if (recipe?.archivedAt == null) {
                        IconButton({ vm.archive(true) }) { Icon(Icons.Default.Archive, stringResource(R.string.action_archive)) }
                    } else {
                        IconButton({ vm.archive(false) }) { Icon(Icons.Default.Unarchive, stringResource(R.string.action_unarchive)) }
                    }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { padding ->
        if (recipe == null) return@Scaffold
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            recipe.imageUri?.let { uri ->
                item {
                    AsyncImage(uri, stringResource(R.string.recipe_image), contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp))
                }
            }
            item {
                val parts = buildList {
                    recipe.prepMinutes?.let { add(stringResource(R.string.prep_minutes, it)) }
                    recipe.cookMinutes?.let { add(stringResource(R.string.cook_minutes, it)) }
                    if (recipe.tags.isNotBlank()) add(recipe.tags)
                }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), Modifier.padding(top = 8.dp))
                ServingsStepper(state.servings, vm::setServings)
            }
            item { SectionTitle(stringResource(R.string.recipe_ingredients)) }
            itemsIndexed(state.lines) { _, l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(l.amountText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.35f))
                    Column(Modifier.weight(0.65f)) {
                        Text(l.name + if (l.optional) " " + stringResource(R.string.optional_suffix) else "")
                        l.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.recipe_steps)) }
            itemsIndexed(state.steps) { i, s -> Text("${i + 1}. ${s.text}", Modifier.padding(vertical = 4.dp)) }
            state.nutrition?.let { n -> item { NutritionCard(n) } }
            recipe.notes?.let { notes ->
                item {
                    SectionTitle(stringResource(R.string.recipe_notes))
                    Text(notes)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_text)) },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun NutritionCard(n: NutritionResult) {
    SectionTitle(stringResource(R.string.nutrition_title))
    Card(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val pct = (n.overallCompleteness * 100).roundToInt()
            if (pct < 100) {
                Text(stringResource(R.string.nutrition_completeness, pct), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Row { Text("", Modifier.weight(1f)); Text(stringResource(R.string.nutrition_per_recipe), Modifier.weight(1f)); Text(stringResource(R.string.nutrition_per_serving), Modifier.weight(1f)) }
            Nutrient.entries.forEach { nut ->
                Row {
                    Text(nut.label(), Modifier.weight(1f))
                    val total = n.totals[nut]
                    val unknown = stringResource(R.string.unknown)
                    fun fmt(v: java.math.BigDecimal?) = when {
                        v == null -> unknown
                        nut == Nutrient.ENERGY_KJ -> "${v.display(0)} kJ / ${NutritionResult.kjToKcal(v).display(0)} kcal"
                        else -> "${v.display(1)} g"
                    }
                    val suffix = if (total != null && !n.isComplete(nut)) " *" else ""
                    Text(fmt(total) + suffix, Modifier.weight(1f))
                    Text(fmt(n.perServing(nut)) + suffix, Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.nutrition_disclaimer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
