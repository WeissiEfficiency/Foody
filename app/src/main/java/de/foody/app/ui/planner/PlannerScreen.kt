package de.foody.app.ui.planner

import androidx.compose.material3.Switch
import de.foody.app.data.repo.einordnung
import de.foody.app.ui.common.label
import de.foody.app.ui.common.slotLabel
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.PlanAuswahl
import de.foody.domain.alsText
import java.time.LocalTime
import de.foody.domain.DayNutrition
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import de.foody.app.ui.theme.FoodyGlass
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.HeaderAction
import de.foody.app.ui.common.MetaPill
import de.foody.app.ui.common.RecipeImage
import de.foody.app.ui.common.ScreenHeader
import de.foody.app.ui.common.ServingsStepper
import de.foody.app.ui.common.compactRange
import de.foody.app.ui.common.pretty
import de.foody.app.ui.theme.EyebrowStyle
import java.time.LocalDate

@Composable
fun PlannerScreen(onOpenRecipe: (String) -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val proposal by vm.proposal.collectAsStateWithLifecycle()
    var addForEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var customDays by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)) { padding ->
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
                    FilledTonalButton(
                        { vm.suggest() },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        enabled = s.activeRecipes.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.planner_suggest))
                    }
                }
                if (s.dayNutrition.isNotEmpty()) {
                    item(key = "average") {
                        val avg = s.dayNutrition.values.map { it.kcal }.average().toInt()
                        Text(
                            pluralStringResource(R.plurals.planner_average, s.dayNutrition.size, avg, s.dayNutrition.size),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(s.range.days, key = { it.toEpochDay() }) { day ->
                    DaySection(
                        day, s.slotsByDay[day].orEmpty(), s.recipes, vm, onOpenRecipe,
                        nutrition = s.dayNutrition[day], goal = s.dailyGoalKcal,
                        onAdd = { addForEpochDay = day.toEpochDay() },
                    )
                }
            }
        }
    }

    proposal?.let { p ->
        ProposalDialog(p, onMahlzeit = { vm.suggest(it, p.seed) }, onAccept = { vm.acceptProposal() },
            onReshuffle = vm::reshuffle, onDismiss = vm::dismissProposal)
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
    nutrition: DayNutrition?,
    goal: Int?,
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
        nutrition?.let { DayNutritionRow(it, goal) }
        // Tagesablauf statt Alphabet: Frühstück, Mittag, Snack, Abend, dann alte Freitexte
        slots.sortedWith(compareBy({ Mahlzeit.reihenfolge(it.slotType) }, { it.slotType }))
            .forEach { slot -> SlotCard(slot, recipes[slot.recipeId], vm, onOpen) }
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
        color = FoodyGlass.fill,
        border = FoodyGlass.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecipeImage(recipe?.imageUri, recipe?.name.orEmpty(), Modifier.size(72.dp).clip(MaterialTheme.shapes.small), emojiSize = 32.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(slotLabel(slot.slotType).uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.primary)
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
    onAdd: (Mahlzeit, String, Int) -> Unit,
) {
    // Als Namen gespeichert, damit rememberSaveable ohne eigenen Saver auskommt
    var mahlzeitName by rememberSaveable { mutableStateOf(Mahlzeit.vorschlagFuer(LocalTime.now()).name) }
    var gaengeText by rememberSaveable { mutableStateOf("") }
    var alle by rememberSaveable { mutableStateOf(false) }
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var servings by rememberSaveable { mutableIntStateOf(2) }
    val mahlzeit = Mahlzeit.valueOf(mahlzeitName)
    val gaenge = Gang.mengeAus(gaengeText).orEmpty()
    val einordnungen = remember(recipes) { recipes.associate { it.id to it.einordnung() } }
    val sichtbar = remember(recipes, mahlzeit, gaenge, alle) {
        PlanAuswahl.filtern(recipes, { einordnungen.getValue(it.id) }, mahlzeit, gaenge, alle)
    }
    // Was der Filter ausblendet, bleibt nicht gewählt – sonst plante man ein unsichtbares Rezept ein
    val gewaehlt = PlanAuswahl.auswahlBehalten(recipeId, sichtbar) { it.id }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_add_for, date.pretty())) },
        text = {
            FormColumn {
                Text(stringResource(R.string.field_slot), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mahlzeit.entries.forEach { m ->
                        FilterChip(mahlzeit == m, { mahlzeitName = m.name }, label = { Text(stringResource(m.label())) },
                            shape = RoundedCornerShape(50), colors = chipColors)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gang.entries.forEach { g ->
                        FilterChip(g in gaenge, { gaengeText = alsText(if (g in gaenge) gaenge - g else gaenge + g).orEmpty() },
                            label = { Text(stringResource(g.label())) }, shape = RoundedCornerShape(50), colors = chipColors)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.planner_alle_rezepte), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(alle, { alle = it })
                }
                when {
                    recipes.isEmpty() -> Text(stringResource(R.string.planner_no_recipes))
                    sichtbar.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.planner_keine_passenden), modifier = Modifier.weight(1f))
                        TextButton({ alle = true }) { Text(stringResource(R.string.planner_alle_zeigen)) }
                    }
                    else -> RecipePicker(sichtbar, gewaehlt) { recipeId = it.id; servings = it.defaultServings }
                }
                ServingsStepper(servings, { servings = it })
            }
        },
        confirmButton = {
            TextButton(enabled = gewaehlt != null, onClick = { onAdd(mahlzeit, gewaehlt!!, servings) }) {
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

/** Vorschau der Vorschläge: erst übernehmen, wenn es passt – oder neu mischen. */
@Composable
private fun ProposalDialog(
    p: PlannerViewModel.Proposal,
    onMahlzeit: (Mahlzeit) -> Unit,
    onAccept: () -> Unit,
    onReshuffle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoAwesome, null) },
        title = { Text(stringResource(R.string.planner_suggest_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mahlzeit.entries.forEach { m ->
                        FilterChip(p.mahlzeit == m, { onMahlzeit(m) }, label = { Text(stringResource(m.label())) },
                            shape = RoundedCornerShape(50), colors = chipColors)
                    }
                }
                if (p.entries.isEmpty()) {
                    Text(stringResource(R.string.planner_suggest_nothing))
                } else {
                    Text(stringResource(R.string.planner_suggest_hint), style = MaterialTheme.typography.bodySmall)
                    p.entries.forEach { (day, recipe) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.pretty(), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(96.dp))
                            Text(recipe.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (p.entries.isNotEmpty()) {
                    TextButton(onReshuffle) { Text(stringResource(R.string.planner_suggest_reshuffle)) }
                    TextButton(onAccept) { Text(stringResource(R.string.planner_suggest_accept)) }
                } else {
                    TextButton(onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        },
        dismissButton = if (p.entries.isNotEmpty()) ({ TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } }) else null,
    )
}

/**
 * Nährwerte des Tages pro Person; mit Tagesziel als Fortschrittsbalken. Über dem Ziel wird der Balken
 * korallenrot – als Hinweis, nicht als Warnung. „≥“, wenn Rezepten Werte fehlen.
 */
@Composable
private fun DayNutritionRow(n: DayNutrition, goal: Int?) {
    val approx = if (n.complete) "" else "≥ "
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.planner_day_nutrition, approx + n.kcal, n.protein, n.carbs, n.fat),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (goal != null) {
            val over = n.kcal > goal
            LinearProgressIndicator(
                progress = { (n.kcal.toFloat() / goal).coerceAtMost(1f) },
                color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                    .semantics { contentDescription = "${n.kcal} / $goal kcal" },
            )
        }
    }
}
