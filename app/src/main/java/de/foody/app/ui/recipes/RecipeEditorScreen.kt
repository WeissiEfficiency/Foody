package de.foody.app.ui.recipes

import de.foody.app.data.RecipePhotoStore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.RecipeEditorRoute
import de.foody.app.ui.common.AutocompleteField
import de.foody.app.ui.common.DecimalField
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.SectionTitle
import de.foody.app.ui.common.display
import de.foody.app.ui.common.parseDecimal
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

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
        if (success) set { it.copy(imageUri = photos.storedUri(file)) } else file.delete()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(onDone: () -> Unit, onManageIngredients: () -> Unit, vm: RecipeEditorViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val all by vm.allIngredients.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.set { it.copy(imageUri = uri.toString()) }
        }
    }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.onPhotoTaken(it, ok) }
        pendingPhoto = null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.name.isBlank()) R.string.recipe_new else R.string.recipe_edit)) },
                navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { TextButton({ vm.save(onDone) }) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(s.name, { v -> vm.set { it.copy(name = v) } }, label = { Text(stringResource(R.string.field_name)) },
                    isError = s.showErrors && s.nameError,
                    supportingText = if (s.showErrors && s.nameError) ({ Text(stringResource(R.string.error_required)) }) else null,
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(s.servings, { v -> vm.set { it.copy(servings = v) } }, stringResource(R.string.field_servings),
                        s.showErrors && s.servingsError, Modifier.weight(1f))
                    NumberField(s.prep, { v -> vm.set { it.copy(prep = v) } }, stringResource(R.string.field_prep), false, Modifier.weight(1f))
                    NumberField(s.cook, { v -> vm.set { it.copy(cook = v) } }, stringResource(R.string.field_cook), false, Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(s.tags, { v -> vm.set { it.copy(tags = v) } }, label = { Text(stringResource(R.string.field_tags)) },
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.Image, null); Text(stringResource(R.string.recipe_pick_image))
                    }
                    OutlinedButton({
                        val (file, uri) = vm.newPhotoTarget()
                        pendingPhoto = file.path
                        camera.launch(uri)
                    }) { Icon(Icons.Outlined.PhotoCamera, null); Text(stringResource(R.string.photo_take)) }
                    if (s.imageUri != null) TextButton({ vm.set { it.copy(imageUri = null) } }) { Text(stringResource(R.string.action_remove)) }
                }
                s.imageUri?.let { AsyncImage(it, stringResource(R.string.recipe_image), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp)) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.recipe_ingredients), Modifier.weight(1f))
                    TextButton(onManageIngredients) { Text(stringResource(R.string.ingredients_manage)) }
                }
            }
            itemsIndexed(s.lines, key = { _, l -> l.key }) { _, line ->
                IngredientLineEditor(line, all, s.showErrors && s.lineError(line), vm)
            }
            item {
                OutlinedButton({ vm.set { it.copy(lines = it.lines + EditorLine()) } }) {
                    Icon(Icons.Default.Add, null); Text(stringResource(R.string.recipe_add_ingredient))
                }
            }
            item { SectionTitle(stringResource(R.string.recipe_steps)) }
            itemsIndexed(s.steps) { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(step, { v -> vm.set { st -> st.copy(steps = st.steps.toMutableList().also { it[i] = v }) } },
                        label = { Text(stringResource(R.string.step_n, i + 1)) }, modifier = Modifier.weight(1f))
                    IconButton({ vm.set { st -> st.copy(steps = st.steps.filterIndexed { j, _ -> j != i }) } }) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_remove))
                    }
                }
            }
            item {
                OutlinedButton({ vm.set { it.copy(steps = it.steps + "") } }) {
                    Icon(Icons.Default.Add, null); Text(stringResource(R.string.recipe_add_step))
                }
            }
            item {
                OutlinedTextField(s.notes, { v -> vm.set { it.copy(notes = v) } }, label = { Text(stringResource(R.string.recipe_notes)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp))
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, error: Boolean, modifier: Modifier) {
    OutlinedTextField(value, { v -> onChange(v.filter(Char::isDigit)) }, label = { Text(label) }, singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier)
}

@Composable
private fun IngredientLineEditor(line: EditorLine, all: List<IngredientEntity>, error: Boolean, vm: RecipeEditorViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AutocompleteField(
                    label = stringResource(R.string.field_ingredient),
                    text = line.ingredientText,
                    onTextChange = { t -> vm.updateLine(line.key) { it.copy(ingredientText = t, ingredientId = null) } },
                    options = all,
                    optionLabel = { it.canonicalName },
                    onSelect = { ing -> vm.updateLine(line.key) { it.copy(ingredientText = ing.canonicalName, ingredientId = ing.id) } },
                    modifier = Modifier.weight(1f),
                )
                IconButton({ vm.set { s -> s.copy(lines = s.lines.filter { it.key != line.key }) } }) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_remove))
                }
            }
            if (line.ingredientText.isNotBlank() && line.ingredientId == null &&
                all.none { it.canonicalName.equals(line.ingredientText.trim(), true) }) {
                Text(stringResource(R.string.ingredient_will_be_created, line.ingredientText.trim()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField(line.amount, { v -> vm.updateLine(line.key) { it.copy(amount = v) } }, stringResource(R.string.field_amount), Modifier.weight(1f))
                DropdownField(stringResource(R.string.field_unit), line.unit, MeasureUnit.entries.toList(), { it.symbol },
                    { u -> vm.updateLine(line.key) { it.copy(unit = u) } }, Modifier.weight(1f))
            }
            if (error) Text(stringResource(R.string.error_amount), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(line.note, { v -> vm.updateLine(line.key) { it.copy(note = v) } },
                    label = { Text(stringResource(R.string.field_note)) }, singleLine = true, modifier = Modifier.weight(1f))
                Checkbox(line.optional, { c -> vm.updateLine(line.key) { it.copy(optional = c) } })
                Text(stringResource(R.string.field_optional))
            }
        }
    }
}
