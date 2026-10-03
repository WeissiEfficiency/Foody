package de.foody.app.ui.planner

import de.foody.app.data.GoalPreferences
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.toDomain
import de.foody.domain.DayNutrition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import de.foody.app.data.repo.PantryRepository
import de.foody.domain.MealSuggestions
import de.foody.domain.PantryCoverage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Nährwerte je Tag pro Person (nur Tage mit Mahlzeiten). */
    val dayNutrition: Map<LocalDate, DayNutrition> = emptyMap(),
    /** Tagesziel in kcal aus den Einstellungen; null = kein Ziel. */
    val dailyGoalKcal: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val plan: PlanRepository,
    private val recipes: RecipeRepository,
    private val pantry: PantryRepository,
    ingredients: IngredientRepository,
    goals: GoalPreferences,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val start = saved.getStateFlow("start", LocalDate.now().toEpochDay())
    private val days = saved.getStateFlow("days", 7)

    private val rangeFlow = combine(start, days) { s, d -> DateRange.ofDays(LocalDate.ofEpochDay(s), d) }

    private val base = combine(
        rangeFlow,
        rangeFlow.flatMapLatest { r -> plan.observeRange(r.start, r.endInclusive) },
        // Eine Abfrage für alle Rezepte (archivierte für bestehende Planpositionen), aktive daraus im Speicher
        recipes.observeAll(),
    ) { range, slots, all ->
        PlannerUiState(range, range.days.size, slots.groupBy { it.date }, all.associateBy { it.id }, all.filter { it.archivedAt == null })
    }

    val state = combine(base, recipes.observeAllLines(), ingredients.observeAll(), goals.dailyKcal) { s, lines, all, goal ->
        val ingMap = all.associate { it.id to it.toDomain() }
        val byRecipe = lines.groupBy { it.recipeId }
        val nutrition = s.slotsByDay.mapNotNull { (day, slots) ->
            val meals = slots.mapNotNull { slot -> s.recipes[slot.recipeId]?.toDomain(byRecipe[slot.recipeId].orEmpty()) }
            DayNutrition.of(meals, ingMap)?.let { day to it }
        }.toMap()
        s.copy(dayNutrition = nutrition, dailyGoalKcal = goal)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    fun shift(daysDelta: Long) { saved["start"] = start.value + daysDelta }
    fun today() { saved["start"] = LocalDate.now().toEpochDay() }
    fun setDays(d: Int) { saved["days"] = d.coerceIn(1, 31) }

    fun add(date: LocalDate, slotType: String, recipeId: String, servings: Int) =
        viewModelScope.launch { plan.add(date, slotType, recipeId, servings) }
    fun setServings(slot: MealSlotEntity, n: Int) = viewModelScope.launch { plan.update(slot.copy(servings = n)) }
    fun move(slot: MealSlotEntity, delta: Long) = viewModelScope.launch { plan.update(slot.copy(date = slot.date.plusDays(delta))) }
    fun delete(slot: MealSlotEntity) = viewModelScope.launch { plan.delete(slot.id) }
    fun cooked(slot: MealSlotEntity) = viewModelScope.launch { plan.markCooked(slot.id) }

    /** Vorschlag für die leeren Tage ab heute; [seed] macht „Neu mischen“ reproduzierbar. */
    data class Proposal(val seed: Long, val entries: List<Pair<LocalDate, RecipeEntity>>)

    private val _proposal = MutableStateFlow<Proposal?>(null)
    val proposal = _proposal.asStateFlow()

    fun suggest(seed: Long = System.currentTimeMillis()) = viewModelScope.launch {
        val s = state.value
        val today = LocalDate.now()
        val empty = s.range.days.filter { it >= today && s.slotsByDay[it].isNullOrEmpty() }
        if (empty.isEmpty() || s.activeRecipes.isEmpty()) {
            _proposal.value = Proposal(seed, emptyList())
            return@launch
        }
        val inPantry = pantry.observeAll().first().filter { it.amount.signum() > 0 }.map { it.ingredientId }.toSet()
        val missing = PantryCoverage.missingByRecipe(recipes.observeRequired().first(), inPantry)
        // Auch die schon geplanten Tage des Zeitraums zählen: Was diese Woche dran ist, wird nicht noch einmal vorgeschlagen
        val lastPlanned = plan.lastPlannedByRecipe(empty.min().minusDays(MealSuggestions.REPEAT_GAP_DAYS), s.range.endInclusive)
        val candidates = s.activeRecipes.map { MealSuggestions.Candidate(it.id, it.favorite, missing[it.id], lastPlanned[it.id]) }
        val picks = MealSuggestions.suggest(candidates, empty, seed)
        _proposal.value = Proposal(seed, picks.entries.sortedBy { it.key }.mapNotNull { (day, id) -> s.recipes[id]?.let { day to it } })
    }

    fun reshuffle() { _proposal.value?.let { suggest(it.seed + 1) } }
    fun dismissProposal() { _proposal.value = null }

    fun acceptProposal(slotType: String) = viewModelScope.launch {
        val entries = _proposal.value?.entries.orEmpty()
        _proposal.value = null
        entries.forEach { (day, recipe) -> plan.add(day, slotType, recipe.id, recipe.defaultServings) }
    }
}
