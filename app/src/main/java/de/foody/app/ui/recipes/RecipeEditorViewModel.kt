package de.foody.app.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.RecipeEditorRoute
import de.foody.app.ui.common.display
import de.foody.app.ui.common.parseDecimal
import de.foody.domain.MeasureUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EditorLine(
    val key: String = UUID.randomUUID().toString(),
    val ingredientId: String? = null,
    val ingredientText: String = "",
    val amount: String = "",
    val unit: MeasureUnit = MeasureUnit.GRAM,
    val note: String = "",
    val optional: Boolean = false,
)

@Serializable
data class EditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val servings: String = "4",
    val prep: String = "",
    val cook: String = "",
    val imageUri: String? = null,
    /** Bild beim Öffnen – wird es ersetzt oder entfernt, räumt Speichern die alte eigene Fotodatei auf. */
    val originalImageUri: String? = null,
    val tags: String = "",
    val notes: String = "",
    val lines: List<EditorLine> = listOf(EditorLine()),
    val steps: List<String> = listOf(""),
    val showErrors: Boolean = false,
) {
    val nameError get() = name.isBlank()
    val servingsError get() = (servings.toIntOrNull() ?: 0) < 1
    // Leere Menge = „nach Bedarf“ (0); sonst muss eine Zahl ≥ 0 angegeben sein.
    fun lineError(l: EditorLine) = l.ingredientText.isNotBlank() && l.amount.isNotBlank() && (parseDecimal(l.amount)?.signum() ?: -1) < 0
    val valid get() = !nameError && !servingsError && lines.none { lineError(it) }
}

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    private val photos: RecipePhotoStore,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val recipeId = saved.toRoute<RecipeEditorRoute>().id
    private val json = Json { ignoreUnknownKeys = true }

    // Entwurf überlebt Prozessneustart über SavedStateHandle.
    private val _state = MutableStateFlow(saved.get<String>("draft")?.let { json.decodeFromString<EditorState>(it) } ?: EditorState())
    val state = _state.asStateFlow()
    val allIngredients = ingredients.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!_state.value.loaded) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = recipeId ?: run { set { it.copy(loaded = true) }; return }
        val r = recipes.get(id) ?: return
        val lines = recipes.getIngredients(id)
        val steps = recipes.getSteps(id)
        val names = lines.map { it.ingredientId }.distinct()
            .associateWith { ingredients.get(it)?.canonicalName.orEmpty() }
        set {
            EditorState(
                loaded = true,
                name = r.name,
                servings = r.defaultServings.toString(),
                prep = r.prepMinutes?.toString().orEmpty(),
                cook = r.cookMinutes?.toString().orEmpty(),
                imageUri = r.imageUri,
                originalImageUri = r.imageUri,
                tags = r.tags,
                notes = r.notes.orEmpty(),
                lines = lines.map {
                    EditorLine(ingredientId = it.ingredientId, ingredientText = names[it.ingredientId].orEmpty(),
                        amount = if (it.amount.signum() == 0) "" else it.amount.display(3), unit = it.unit, note = it.preparationNote.orEmpty(), optional = it.optional)
                }.ifEmpty { listOf(EditorLine()) },
                steps = steps.map { it.text }.ifEmpty { listOf("") },
            )
        }
    }

    fun set(f: (EditorState) -> EditorState) {
        _state.update(f)
        saved["draft"] = json.encodeToString(EditorState.serializer(), _state.value)
    }

    /** Zieldatei für ein Kamerafoto; der Pfad muss eine Neuerstellung der Activity überstehen. */
    fun newPhotoTarget() = photos.newPhotoTarget()

    fun onPhotoTaken(path: String, success: Boolean) {
        val file = java.io.File(path)
        if (!success) { file.delete(); return }
        viewModelScope.launch {
            photos.shrink(file)
            set { it.copy(imageUri = photos.storedUri(file)) }
        }
    }

    fun updateLine(key: String, f: (EditorLine) -> EditorLine) = set { s -> s.copy(lines = s.lines.map { if (it.key == key) f(it) else it }) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.valid) { set { it.copy(showErrors = true) }; return }
        viewModelScope.launch {
            val lines = s.lines.filter { it.ingredientText.isNotBlank() }.map { l ->
                // Freitext → vorhandene oder neue kanonische Zutat
                val ingId = l.ingredientId ?: ingredients.getOrCreate(l.ingredientText).id
                RecipeDraft.Line(ingId, parseDecimal(l.amount) ?: java.math.BigDecimal.ZERO, l.unit, l.note.ifBlank { null }, l.optional)
            }
            recipes.save(
                RecipeDraft(
                    recipeId, s.name, s.servings.toInt(), s.prep.toIntOrNull(), s.cook.toIntOrNull(), s.imageUri,
                    s.notes, s.tags, lines, s.steps,
                ),
            )
            if (s.originalImageUri != s.imageUri) photos.deleteIfUnused(s.originalImageUri)
            saved.remove<String>("draft")
            onDone()
        }
    }
}
