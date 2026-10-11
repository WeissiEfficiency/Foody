package de.foody.app.ui.recipes

import de.foody.app.ui.common.EinordnungChips
import de.foody.app.ui.common.label
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.ui.common.AutocompleteField
import de.foody.app.ui.common.DecimalField
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.SectionTitle
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.launch

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
        containerColor = Color.Transparent,
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
                val e = s.einordnung
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.einordnung_titel), style = MaterialTheme.typography.titleMedium)
                    EinordnungChips(stringResource(R.string.einordnung_mahlzeit), Mahlzeit.entries, e.mahlzeiten, e.mahlzeitenVermutet,
                        { it.label() }, vm::toggleMahlzeit, if (s.mahlzeiten != null) vm::mahlzeitenZuruecksetzen else null)
                    EinordnungChips(stringResource(R.string.einordnung_gang), Gang.entries, e.gaenge, e.gaengeVermutet,
                        { it.label() }, vm::toggleGang, if (s.gaenge != null) vm::gaengeZuruecksetzen else null)
                }
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
