package de.foody.app.ui.recipes

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
import de.foody.domain.Dimension
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

data class DisplayLine(val ingredientId: String, val name: String, val amountText: String?, val note: String?, val optional: Boolean)

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
        val display = lines.map { l ->
            val scaled = RecipeScaler.scale(l.amount, recipe.defaultServings, servings)
            DisplayLine(
                l.ingredientId, ingMap[l.ingredientId]?.name.orEmpty(),
                if (scaled.signum() == 0) null else formatAmount(scaled, l.unit), l.preparationNote, l.optional,
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
                    val unit = m.unit ?: unitById[m.ingredientId]?.takeIf { it.dimension == Dimension.COUNT }
                    val stepAmount = if (m.amount != null && unit != null) {
                        formatAmount(RecipeScaler.scale(m.amount!!, recipe.defaultServings, servings), unit)
                    } else {
                        null
                    }
                    StepLine(line, stepAmount?.takeIf { it != line.amountText })
                }
            },
            nutrition = NutritionCalculator.calculate(recipe.toDomain(lines), ingMap, servings),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun setServings(n: Int) { saved["servings"] = n }
    fun archive(archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    fun toggleFavorite() = viewModelScope.launch { state.value.recipe?.let { repo.setFavorite(id, !it.favorite) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val image = state.value.recipe?.imageUri
        repo.delete(id)
        photos.deleteIfUnused(image)
        onDone()
    }

    fun newPhotoTarget() = photos.newPhotoTarget()

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
