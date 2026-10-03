package de.foody.app.ui.recipes

import kotlinx.coroutines.flow.map
import de.foody.domain.LineGap
import de.foody.app.timer.CookTimerRepository
import de.foody.app.timer.RunningTimer
import de.foody.domain.StepTimer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.AddRecipeResult
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.ShoppingRepository
import de.foody.app.data.repo.toDomain
import de.foody.app.ui.RecipeDetailRoute
import de.foody.app.ui.common.display
import de.foody.app.ui.common.formatAmount
import de.foody.domain.NutritionCalculator
import de.foody.domain.NutritionResult
import de.foody.domain.RecipeScaler
import de.foody.domain.StepIngredientMatcher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DisplayLine(
    /** ID der Rezeptzeile – dieselbe Zutat kann zweimal vorkommen (Teig und Streusel). */
    val lineId: String,
    val ingredientId: String,
    val name: String,
    val amountText: String?,
    val note: String?,
    val optional: Boolean,
    /** Energie dieser Zeile für die gewählten Portionen (Menge × kcal je 100 g); null = nicht berechenbar. */
    val kcal: Int? = null,
    val gap: LineGap? = null,
)

/** Zutat im Kochmodus; [stepAmountText] ist die im Schritt genannte Teilmenge, falls sie von der Gesamtmenge abweicht. */
data class StepLine(val line: DisplayLine, val stepAmountText: String?)

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val servings: Int = 1,
    val lines: List<DisplayLine> = emptyList(),
    val steps: List<InstructionStepEntity> = emptyList(),
    /** Je Schritt die darin erwähnten Zutaten mit skalierter Menge (Kochmodus). */
    val stepLines: List<List<StepLine>> = emptyList(),
    val nutrition: NutritionResult? = null,
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val repo: RecipeRepository,
    ingredients: IngredientRepository,
    private val shopping: ShoppingRepository,
    private val photos: RecipePhotoStore,
    private val cookTimers: CookTimerRepository,
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
        val nutrition = NutritionCalculator.calculate(recipe.toDomain(lines), ingMap, servings)
        val display = lines.map { l ->
            val scaled = RecipeScaler.scale(l.amount, recipe.defaultServings, servings)
            DisplayLine(
                l.id, l.ingredientId, ingMap[l.ingredientId]?.name.orEmpty(),
                if (scaled.signum() == 0) null else formatAmount(scaled, l.unit), l.preparationNote, l.optional,
                kcal = nutrition.lineEnergyKj[l.id]?.let { NutritionResult.kjToKcal(it).toInt() },
                gap = nutrition.lineGaps[l.id],
            )
        }
        val names = display.associate { it.ingredientId to it.name }
        val byId = display.associateBy { it.ingredientId }
        val unitById = lines.associate { it.ingredientId to it.unit }
        RecipeDetailUiState(
            recipe = recipe,
            servings = servings,
            lines = display,
            steps = steps,
            stepLines = steps.map { s ->
                StepIngredientMatcher.mentions(s.text, names).mapNotNull { m ->
                    val line = byId[m.ingredientId] ?: return@mapNotNull null
                    // „2 Eier“ hat kein Einheitswort: dann gilt die Stück-Einheit der Rezeptzeile
                    val unit = m.unit ?: unitById[m.ingredientId]?.takeIf { it.dimension.countable }
                    val stepAmount = if (m.amount != null && unit != null) {
                        formatAmount(RecipeScaler.scale(m.amount!!, recipe.defaultServings, servings), unit)
                    } else {
                        null
                    }
                    StepLine(line, stepAmount?.takeIf { it != line.amountText })
                }
            },
            nutrition = nutrition,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun setServings(n: Int) { saved["servings"] = n }

    /**
     * Abgehakte Zutaten („schon bereitgestellt“) – nur für diesen Besuch der Seite, übersteht Drehen und
     * Prozessende über den SavedStateHandle. Gespeichert als Text, damit kein eigener Saver nötig ist.
     */
    val checkedLines = saved.getStateFlow("checkedLines", "")
        .map { raw -> raw.split(',').filter { it.isNotEmpty() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleChecked(lineId: String) {
        val now = saved.get<String>("checkedLines").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
        saved["checkedLines"] = (if (lineId in now) now - lineId else now + lineId).joinToString(",")
    }

    fun clearChecked() { saved["checkedLines"] = "" }
    fun archive(archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    fun toggleFavorite() = viewModelScope.launch { state.value.recipe?.let { repo.setFavorite(id, !it.favorite) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val image = state.value.recipe?.imageUri
        repo.delete(id)
        photos.deleteIfUnused(image)
        onDone()
    }

    fun newPhotoTarget() = photos.newPhotoTarget()

    /** Alle laufenden Kochtimer – auch die anderer Rezepte, damit keiner im Hintergrund vergessen wird. */
    val timers = cookTimers.timers

    fun startTimer(timer: StepTimer) =
        cookTimers.start(timer.label, state.value.recipe?.name.orEmpty(), timer.duration)

    fun dismissTimer(timer: RunningTimer) = cookTimers.dismiss(timer.id)

    /** Kamerafoto übernehmen; ein ersetztes eigenes Foto wird aufgeräumt, ein abgebrochenes verworfen. */
    fun onPhotoTaken(path: String, success: Boolean) = viewModelScope.launch {
        val file = java.io.File(path)
        if (!success) { file.delete(); return@launch }
        val old = state.value.recipe?.imageUri
        repo.setImage(id, photos.storedUri(file))
        photos.deleteIfUnused(old)
    }
    fun duplicate(suffix: String, onDone: (String) -> Unit) = viewModelScope.launch { repo.duplicate(id, suffix)?.let(onDone) }

    /** Einmaliges Ergebnis von „Auf die Einkaufsliste“; die UI quittiert es nach Anzeige. */
    private val _added = MutableStateFlow<AddRecipeResult?>(null)
    val added = _added.asStateFlow()
    fun addedShown() { _added.value = null }

    fun addToShopping(defaultListName: String) = viewModelScope.launch {
        _added.value = shopping.addRecipe(id, state.value.servings, defaultListName)
    }
}
