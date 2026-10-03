package de.foody.app.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import de.foody.app.ui.common.HeaderAction
import de.foody.app.ui.common.MetaPill
import de.foody.app.ui.common.ScreenHeader
import de.foody.app.ui.theme.EyebrowStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.foody.app.ui.common.RecipeImage
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.compactRange
import de.foody.app.ui.common.pretty
import androidx.compose.ui.res.pluralStringResource
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

@Composable
fun PlannerScreen(onOpenRecipe: (String) -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var addForEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var customDays by rememberSaveable { mutableStateOf(false) }

    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding)) {
            // Kopf bleibt stehen, damit Vor/Zurück beim Scrollen durch die Woche erreichbar ist
            ScreenHeader(stringResource(R.string.planner_eyebrow), compactRange(s.range.start, s.range.endInclusive)) {
                HeaderAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.planner_previous)) { vm.shift(-s.days.toLong()) }
                HeaderAction(Icons.Outlined.Today, stringResource(R.string.planner_today)) { vm.today() }
                HeaderAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.planner_next)) { vm.shift(s.days.toLong()) }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 7).forEach { d ->
                            FilterChip(s.days == d && !customDays, { customDays = false; vm.setDays(d) },
                                label = { Text(pluralStringResource(R.plurals.days_n, d, d)) }, shape = RoundedCornerShape(50), colors = chipColors)
                        }
                        FilterChip(customDays, { customDays = true }, label = { Text(stringResource(R.string.days_custom)) },
                            shape = RoundedCornerShape(50), colors = chipColors)
                    }
                    if (customDays) {
                        OutlinedTextField(
                            s.days.toString(), { v -> v.toIntOrNull()?.let(vm::setDays) },
                            label = { Text(stringResource(R.string.days_count)) }, singleLine = true,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(s.range.days, key = { it.toEpochDay() }) { day ->
                    DaySection(
                        day, s.slotsByDay[day].orEmpty(), s.recipes, vm, onOpenRecipe,
                        onAdd = { addForEpochDay = day.toEpochDay() },
                    )
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
private fun DaySection(
    day: LocalDate,
    slots: List<MealSlotEntity>,
    recipes: Map<String, RecipeEntity>,
    vm: PlannerViewModel,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val today = day == LocalDate.now()
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.pretty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (today) MetaPill(stringResource(R.string.planner_today))
            if (slots.isNotEmpty()) {
                IconButton(onAdd) { Icon(Icons.Default.Add, stringResource(R.string.planner_add_for, day.pretty())) }
            }
        }
        slots.forEach { slot -> SlotCard(slot, recipes[slot.recipeId], vm, onOpen) }
        if (slots.isEmpty()) {
            // Gestrichelte Fläche lädt zum Planen ein, statt nur ein kleines Plus zu zeigen
            val outline = MaterialTheme.colorScheme.outline
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium)
                    .drawBehind {
                        drawRoundRect(
                            outline, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                        )
                    }
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.planner_add_short), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: MealSlotEntity, recipe: RecipeEntity?, vm: PlannerViewModel, onOpen: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = { onOpen(slot.recipeId) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecipeImage(recipe?.imageUri, recipe?.name.orEmpty(), Modifier.size(72.dp).clip(MaterialTheme.shapes.small), emojiSize = 32.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(slot.slotType.uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.primary)
                Text(recipe?.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServingsStepper(slot.servings, { vm.setServings(slot, it) })
                    if (slot.cookedAt != null) MetaPill(stringResource(R.string.planner_cooked), icon = Icons.Default.CheckCircle)
                }
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                DropdownMenu(menu, { menu = false }) {
                    if (slot.cookedAt == null) {
                        DropdownMenuItem({ Text(stringResource(R.string.planner_mark_cooked)) }, { menu = false; vm.cooked(slot) },
                            leadingIcon = { Icon(Icons.Default.Restaurant, null) })
                    }
                    DropdownMenuItem({ Text(stringResource(R.string.planner_move_earlier)) }, { menu = false; vm.move(slot, -1) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.planner_move_later)) }, { menu = false; vm.move(slot, 1) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_delete)) }, { menu = false; vm.delete(slot) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
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
                    RecipePicker(recipes, recipeId) { recipeId = it.id; servings = it.defaultServings }
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

/** Suchbare Rezeptauswahl mit Vorschaubild – bei großen Sammlungen statt eines langen Dropdowns. */
@Composable
private fun RecipePicker(recipes: List<RecipeEntity>, selectedId: String?, onSelect: (RecipeEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query, recipes) {
        val q = query.trim()
        recipes.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.tags.contains(q, ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text(stringResource(R.string.recipe_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(matches, key = { it.id }) { r ->
                val selected = r.id == selectedId
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(r) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecipeImage(r.imageUri, r.name, Modifier.size(44.dp).clip(MaterialTheme.shapes.small), emojiSize = 22.sp)
                    Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
