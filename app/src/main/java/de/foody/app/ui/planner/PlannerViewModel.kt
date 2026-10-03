package de.foody.app.ui.planner

import androidx.compose.foundation.layout.size
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.DateRange
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val state = combine(
        rangeFlow,
        rangeFlow.flatMapLatest { r -> plan.observeRange(r.start, r.endInclusive) },
        // Eine Abfrage für alle Rezepte (archivierte für bestehende Planpositionen), aktive daraus im Speicher
        recipes.observeAll(),
    ) { range, slots, all ->
        PlannerUiState(range, range.days.size, slots.groupBy { it.date }, all.associateBy { it.id }, all.filter { it.archivedAt == null })
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
