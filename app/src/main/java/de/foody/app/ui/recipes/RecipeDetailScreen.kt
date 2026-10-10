package de.foody.app.ui.recipes

import androidx.compose.ui.draw.alpha
import de.foody.app.data.repo.einordnung
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.IconButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.ui.text.style.TextDecoration
import de.foody.app.ui.theme.FoodyGlass
import de.foody.domain.LineGap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.RecipeEntity
import de.foody.app.ui.common.MetaPill
import de.foody.app.ui.common.RecipeImage
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.display
import de.foody.app.ui.common.label
import de.foody.app.ui.common.sharedRecipeImage
import de.foody.app.ui.theme.EyebrowStyle
import de.foody.app.ui.theme.FavoriteRed
import de.foody.domain.Nutrient
import de.foody.domain.NutritionResult
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenOther: (String) -> Unit,
    vm: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cookedCount by vm.cookedCount.collectAsStateWithLifecycle()
    val checked by vm.checkedLines.collectAsStateWithLifecycle()
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

    Scaffold(
        containerColor = Color.Transparent,contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
                        RatingRow(recipe.rating, cookedCount, vm::rate)
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
            item {
                val ready = state.lines.count { it.lineId in checked }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (ready > 0) {
                        Text(stringResource(R.string.ingredients_ready, ready, state.lines.size),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        TextButton(vm::clearChecked) { Text(stringResource(R.string.ingredients_reset)) }
                    } else {
                        Text(stringResource(R.string.ingredients_tap_hint), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = FoodyGlass.fill,
                    border = FoodyGlass.border,
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        state.lines.forEachIndexed { i, l ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            IngredientRow(l, l.lineId in checked) { vm.toggleChecked(l.lineId) }
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
        val timers by vm.timers.collectAsStateWithLifecycle()
        CookModeDialog(
            recipe.name, state.steps, state.stepLines,
            timers = timers,
            onStartTimer = vm::startTimer,
            onDismissTimer = vm::dismissTimer,
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
        val e = recipe.einordnung()
        e.mahlzeiten.sortedBy { it.ordinal }.forEach {
            MetaPill(stringResource(it.label()), if (e.mahlzeitenVermutet) Modifier.alpha(0.6f) else Modifier)
        }
        e.gaenge.sortedBy { it.ordinal }.forEach {
            MetaPill(stringResource(it.label()), if (e.gaengeVermutet) Modifier.alpha(0.6f) else Modifier)
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
private fun IngredientRow(l: DisplayLine, checked: Boolean, onToggle: () -> Unit) {
    // Abgehakt = bereitgestellt: blasser und durchgestrichen, damit der Blick beim Kochen auf dem Rest bleibt
    val alpha = if (checked) 0.45f else 1f
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 10.dp).size(20.dp),
        )
        Text(
            l.amountText ?: stringResource(R.string.amount_as_needed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.weight(0.30f),
        )
        Column(Modifier.weight(0.70f)) {
            Text(
                l.name + if (l.optional) " " + stringResource(R.string.optional_suffix) else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            l.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Energie dieser Zeile – so ist nachvollziehbar, woraus die Summe besteht und wo Werte fehlen
        when {
            l.kcal != null -> Text(
                stringResource(R.string.kcal_amount, l.kcal), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp),
            )
            l.gap != null -> Text(
                stringResource(if (l.gap == LineGap.NO_WEIGHT) R.string.kcal_gap_weight else R.string.kcal_gap_values),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 8.dp),
            )
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
        Surface(shape = MaterialTheme.shapes.medium, color = FoodyGlass.fill, border = FoodyGlass.border) {
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

/** Eigene Bewertung (1–5 Sterne) und wie oft schon gekocht – wie in Rezept-Apps mit eigener Sammlung. */
@Composable
private fun RatingRow(rating: Int?, cookedCount: Int, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { n ->
            val filled = rating != null && n <= rating
            IconButton({ onRate(n) }, Modifier.size(40.dp)) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    pluralStringResource(R.plurals.rating_stars, n, n),
                    tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (cookedCount > 0) {
            MetaPill(pluralStringResource(R.plurals.cooked_count, cookedCount, cookedCount), icon = Icons.Outlined.Restaurant)
        }
    }
}
