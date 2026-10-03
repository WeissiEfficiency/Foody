package de.foody.app.ui.recipes

import de.foody.app.ui.common.sharedRecipeImage
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import de.foody.app.data.RecipePhotoStore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import de.foody.app.data.repo.AddRecipeResult
import de.foody.app.data.repo.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FavoriteBorder
import de.foody.app.ui.theme.FavoriteRed
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.toDomain
import de.foody.app.ui.RecipeDetailRoute
import de.foody.app.ui.common.MetaPill
import de.foody.app.ui.common.RecipeImage
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.display
import de.foody.app.ui.common.formatAmount
import de.foody.app.ui.common.label
import de.foody.app.ui.theme.EyebrowStyle
import de.foody.domain.Nutrient
import de.foody.domain.NutritionCalculator
import de.foody.domain.NutritionResult
import de.foody.domain.RecipeScaler
import de.foody.domain.Dimension
import de.foody.domain.StepIngredientMatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

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

@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenOther: (String) -> Unit,
    vm: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var cooking by rememberSaveable { mutableStateOf(false) }
    var askPhoto by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { vm.onPhotoTaken(it, ok) }
        pendingPhoto = null
    }
    val takePhoto = {
        val (file, uri) = vm.newPhotoTarget()
        pendingPhoto = file.path
        camera.launch(uri)
    }
    val snackbar = remember { SnackbarHostState() }
    val added by vm.added.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val defaultListName = stringResource(R.string.shopping_default_name)
    LaunchedEffect(added) {
        val a = added ?: return@LaunchedEffect
        vm.addedShown()
        snackbar.showSnackbar(
            if (a.added == 0) resources.getString(R.string.shopping_added_none)
            else resources.getQuantityString(R.plurals.shopping_added, a.added, a.added, a.listName),
        )
    }
    val listState = rememberLazyListState()
    val copySuffix = stringResource(R.string.recipe_copy_suffix)
    val recipe = state.recipe ?: return

    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState) {
            item {
                Hero(
                    recipe, onBack, onEdit,
                    onFavorite = {
                        haptic.performHapticFeedback(if (recipe.favorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        vm.toggleFavorite()
                    },
                    onTakePhoto = takePhoto,
                    onDuplicate = { vm.duplicate(copySuffix, onOpenOther) },
                    onArchive = { vm.archive(recipe.archivedAt == null) },
                    onDelete = { confirmDelete = true },
                )
            }
            item {
                // Inhaltsblatt überlappt das Bild mit großen Rundungen
                Surface(
                    // Nach oben ziehen UND die Höhe entsprechend kürzen, damit darunter keine Lücke bleibt
                    Modifier.fillMaxWidth().layout { measurable, constraints ->
                        val overlap = 28.dp.roundToPx()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
                    },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
                        MetaRow(recipe, state.nutrition)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.field_servings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            ServingsStepper(state.servings, vm::setServings)
                        }
                        if (state.steps.isNotEmpty()) {
                            Button({ cooking = true }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.cook_mode_start), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        OutlinedButton(
                            { vm.addToShopping(defaultListName) },
                            Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Outlined.AddShoppingCart, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.recipe_add_to_shopping), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.recipe_ingredients), null) }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp,
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        state.lines.forEachIndexed { i, l ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            IngredientRow(l)
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.recipe_steps), null) }
            itemsIndexed(state.steps) { i, s -> StepRow(i + 1, s.text) }
            state.nutrition?.let { n -> item { NutritionCard(n) } }
            recipe.notes?.let { notes ->
                item {
                    SectionHeader(stringResource(R.string.recipe_notes), null)
                    Text(notes, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
        // Scrim erst einblenden, wenn das Hero-Bild aus dem Bild gescrollt ist. derivedStateOf: nur der Wechsel
        // „oben / gescrollt“ löst eine Recomposition aus, nicht jede Scroll-Bewegung.
        val scrolledPastHero by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        if (scrolledPastHero) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
        }
    }

    if (cooking) {
        CookModeDialog(
            recipe.name, state.steps, state.stepLines,
            onClose = { cooking = false },
            // Gerade fertig gekocht ist der beste Moment für das erste eigene Foto
            onFinish = { cooking = false; askPhoto = recipe.imageUri == null },
        )
    }
    if (askPhoto) {
        AlertDialog(
            onDismissRequest = { askPhoto = false },
            icon = { Icon(Icons.Outlined.PhotoCamera, null) },
            title = { Text(stringResource(R.string.photo_prompt_title)) },
            text = { Text(stringResource(R.string.photo_prompt_text)) },
            confirmButton = { TextButton({ askPhoto = false; takePhoto() }) { Text(stringResource(R.string.photo_take)) } },
            dismissButton = { TextButton({ askPhoto = false }) { Text(stringResource(R.string.action_not_now)) } },
        )
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
private fun Hero(
    recipe: RecipeEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onTakePhoto: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val roundButton = IconButtonDefaults.filledIconButtonColors(
        containerColor = Color.White.copy(alpha = 0.92f), contentColor = Color(0xFF1C2321),
    )
    // Ohne Foto trägt das Emoji keine 360 dp – kompakter Kopf, damit die Zutaten früher sichtbar sind
    val heroHeight = if (recipe.imageUri != null) 360.dp else 240.dp
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        RecipeImage(recipe.imageUri, recipe.name, Modifier.sharedRecipeImage(recipe.id).fillMaxSize(), emojiSize = if (recipe.imageUri != null) 120.sp else 88.sp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent)))
        if (recipe.imageUri == null) {
            FilledTonalButton(
                onTakePhoto,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.92f), contentColor = Color(0xFF1C2321)),
            ) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.photo_take))
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            androidx.compose.material3.FilledIconButton(onBack, colors = roundButton) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.FilledIconButton(onFavorite, colors = roundButton) {
                Icon(
                    if (recipe.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (recipe.favorite) R.string.favorite_remove else R.string.favorite_add),
                    tint = if (recipe.favorite) FavoriteRed else Color(0xFF1C2321),
                )
            }
            Spacer(Modifier.size(8.dp))
            androidx.compose.material3.FilledIconButton(onEdit, colors = roundButton) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
            }
            Spacer(Modifier.size(8.dp))
            Box {
                androidx.compose.material3.FilledIconButton({ menu = true }, colors = roundButton) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        { Text(stringResource(if (recipe.imageUri == null) R.string.photo_take else R.string.photo_replace)) },
                        { menu = false; onTakePhoto() },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                    )
                    DropdownMenuItem({ Text(stringResource(R.string.action_duplicate)) }, { menu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                    val archived = recipe.archivedAt != null
                    DropdownMenuItem(
                        { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                        { menu = false; onArchive() },
                        leadingIcon = { Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                    )
                    DropdownMenuItem({ Text(stringResource(R.string.action_delete)) }, { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                }
            }
        }
    }
}

@Composable
private fun MetaRow(recipe: RecipeEntity, nutrition: NutritionResult?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val total = (recipe.prepMinutes ?: 0) + (recipe.cookMinutes ?: 0)
        if (total > 0) MetaPill(stringResource(R.string.minutes_total, total), icon = Icons.Outlined.Schedule)
        recipe.prepMinutes?.let { MetaPill(stringResource(R.string.prep_minutes, it)) }
        recipe.cookMinutes?.let { MetaPill(stringResource(R.string.cook_minutes, it)) }
        nutrition?.perServing(Nutrient.ENERGY_KJ)?.let { kj ->
            val approx = if (nutrition.isComplete(Nutrient.ENERGY_KJ)) "" else "≥ "
            MetaPill(approx + stringResource(R.string.kcal_per_serving, NutritionResult.kjToKcal(kj).display(0)),
                icon = Icons.Outlined.LocalFireDepartment)
        }
        recipe.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { MetaPill(it) }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun IngredientRow(l: DisplayLine) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            l.amountText ?: stringResource(R.string.amount_as_needed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.32f),
        )
        Column(Modifier.weight(0.68f)) {
            Text(l.name + if (l.optional) " " + stringResource(R.string.optional_suffix) else "", style = MaterialTheme.typography.bodyLarge)
            l.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}

@Composable
fun NutritionCard(n: NutritionResult) {
    SectionHeader(stringResource(R.string.nutrition_title), stringResource(R.string.nutrition_per_serving))
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Kacheln für die vier Hauptwerte pro Portion
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Nutrient.ENERGY_KJ, Nutrient.PROTEIN_G, Nutrient.CARBS_G, Nutrient.FAT_G).forEach { nut ->
                val v = n.perServing(nut)
                val value = when {
                    v == null -> "–"
                    nut == Nutrient.ENERGY_KJ -> NutritionResult.kjToKcal(v).display(0)
                    else -> v.display(1)
                }
                val unit = if (nut == Nutrient.ENERGY_KJ) "kcal" else "g"
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value + if (v != null && !n.isComplete(nut)) "*" else "", style = MaterialTheme.typography.titleLarge)
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (nut == Nutrient.ENERGY_KJ) stringResource(R.string.nutrient_energy) else nut.label(),
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        val pct = (n.overallCompleteness * 100).roundToInt()
        if (pct < 100) {
            Text(stringResource(R.string.nutrition_completeness, pct), color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall)
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("", Modifier.weight(1.1f))
                    Text(stringResource(R.string.nutrition_per_recipe), Modifier.weight(1f), style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.nutrition_per_serving), Modifier.weight(1f), style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Nutrient.entries.forEach { nut ->
                    Row {
                        Text(nut.label(), Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
                        val total = n.totals[nut]
                        val unknown = stringResource(R.string.unknown)
                        fun fmt(v: java.math.BigDecimal?) = when {
                            v == null -> unknown
                            nut == Nutrient.ENERGY_KJ -> "${NutritionResult.kjToKcal(v).display(0)} kcal"
                            else -> "${v.display(1)} g"
                        }
                        val suffix = if (total != null && !n.isComplete(nut)) " *" else ""
                        Text(fmt(total) + suffix, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(fmt(n.perServing(nut)) + suffix, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text(stringResource(R.string.nutrition_disclaimer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
