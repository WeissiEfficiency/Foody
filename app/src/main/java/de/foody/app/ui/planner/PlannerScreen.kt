package de.foody.app.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.pretty
import de.foody.domain.DateRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlannerUiState(
    val range: DateRange = DateRange.ofDays(LocalDate.now(), 7),
    val days: Int = 7,
    val slotsByDay: Map<LocalDate, List<MealSlotEntity>> = emptyMap(),
    val recipes: Map<String, RecipeEntity> = emptyMap(),
    val activeRecipes: List<RecipeEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val plan: PlanRepository,
    recipes: RecipeRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val start = saved.getStateFlow("start", LocalDate.now().toEpochDay())
    private val days = saved.getStateFlow("days", 7)

    private val rangeFlow = combine(start, days) { s, d -> DateRange.ofDays(LocalDate.ofEpochDay(s), d) }

    // Alle Rezepte (inkl. archivierte) für Anzeige bestehender Planpositionen.
    private val allRecipes = recipes.observe("", archived = false).combine(recipes.observe("", archived = true)) { a, b -> a + b }

    val state = combine(
        rangeFlow,
        rangeFlow.flatMapLatest { r -> plan.observeRange(r.start, r.endInclusive) },
        allRecipes,
        recipes.observeActive(),
    ) { range, slots, all, active ->
        PlannerUiState(range, range.days.size, slots.groupBy { it.date }, all.associateBy { it.id }, active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    fun shift(daysDelta: Long) { saved["start"] = start.value + daysDelta }
    fun today() { saved["start"] = LocalDate.now().toEpochDay() }
    fun setDays(d: Int) { saved["days"] = d.coerceIn(1, 31) }

    fun add(date: LocalDate, slotType: String, recipeId: String, servings: Int) =
        viewModelScope.launch { plan.add(date, slotType, recipeId, servings) }
    fun setServings(slot: MealSlotEntity, n: Int) = viewModelScope.launch { plan.update(slot.copy(servings = n)) }
    fun move(slot: MealSlotEntity, delta: Long) = viewModelScope.launch { plan.update(slot.copy(date = slot.date.plusDays(delta))) }
    fun delete(slot: MealSlotEntity) = viewModelScope.launch { plan.delete(slot.id) }
    fun cooked(slot: MealSlotEntity) = viewModelScope.launch { plan.markCooked(slot.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(onOpenRecipe: (String) -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var addForEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var customDays by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_planner)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.shift(-s.days.toLong()) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.planner_previous))
                    }
                    Text(
                        "${s.range.start.pretty()} – ${s.range.endInclusive.pretty()}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).clickable { vm.today() },
                    )
                    IconButton({ vm.shift(s.days.toLong()) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.planner_next))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 7).forEach { d ->
                        FilterChip(s.days == d && !customDays, { customDays = false; vm.setDays(d) },
                            label = { Text(stringResource(R.string.days_n, d)) })
                    }
                    FilterChip(customDays, { customDays = true }, label = { Text(stringResource(R.string.days_custom)) })
                }
                if (customDays) {
                    OutlinedTextField(
                        s.days.toString(), { v -> v.toIntOrNull()?.let(vm::setDays) },
                        label = { Text(stringResource(R.string.days_count)) }, singleLine = true,
                    )
                }
            }
            items(s.range.days, key = { it.toEpochDay() }) { day ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.pretty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            color = if (day == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        IconButton({ addForEpochDay = day.toEpochDay() }) {
                            Icon(Icons.Default.Add, stringResource(R.string.planner_add_for, day.pretty()))
                        }
                    }
                    s.slotsByDay[day].orEmpty().forEach { slot ->
                        SlotCard(slot, s.recipes[slot.recipeId], vm, onOpenRecipe)
                    }
                }
            }
        }
    }

    addForEpochDay?.let { epoch ->
        AddSlotDialog(LocalDate.ofEpochDay(epoch), s.activeRecipes, onDismiss = { addForEpochDay = null }) { type, recipe, servings ->
            vm.add(LocalDate.ofEpochDay(epoch), type, recipe, servings)
            addForEpochDay = null
        }
    }
}

@Composable
private fun SlotCard(slot: MealSlotEntity, recipe: RecipeEntity?, vm: PlannerViewModel, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpen(slot.recipeId) }) {
                    Text(slot.slotType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(recipe?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                }
                if (slot.cookedAt != null) {
                    Icon(Icons.Default.CheckCircle, stringResource(R.string.planner_cooked), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServingsStepper(slot.servings, { vm.setServings(slot, it) }, Modifier.weight(1f))
                IconButton({ vm.move(slot, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.planner_move_earlier)) }
                IconButton({ vm.move(slot, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.planner_move_later)) }
                if (slot.cookedAt == null) {
                    IconButton({ vm.cooked(slot) }) { Icon(Icons.Default.Restaurant, stringResource(R.string.planner_mark_cooked)) }
                }
                IconButton({ vm.delete(slot) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun AddSlotDialog(
    date: LocalDate,
    recipes: List<RecipeEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit,
) {
    val presets = listOf(
        stringResource(R.string.slot_breakfast), stringResource(R.string.slot_lunch),
        stringResource(R.string.slot_dinner), stringResource(R.string.slot_snack),
    )
    var slotType by rememberSaveable { mutableStateOf(presets[2]) }
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var servings by rememberSaveable { mutableStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_add_for, date.pretty())) },
        text = {
            FormColumn {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p -> FilterChip(slotType == p, { slotType = p }, label = { Text(p) }) }
                }
                OutlinedTextField(slotType, { slotType = it }, label = { Text(stringResource(R.string.field_slot)) }, singleLine = true)
                if (recipes.isEmpty()) {
                    Text(stringResource(R.string.planner_no_recipes))
                } else {
                    DropdownField(stringResource(R.string.field_recipe), recipes.firstOrNull { it.id == recipeId }, recipes, { it.name },
                        { recipeId = it.id; servings = it.defaultServings })
                }
                ServingsStepper(servings, { servings = it })
            }
        },
        confirmButton = {
            TextButton(enabled = recipeId != null && slotType.isNotBlank(), onClick = { onAdd(slotType.trim(), recipeId!!, servings) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
