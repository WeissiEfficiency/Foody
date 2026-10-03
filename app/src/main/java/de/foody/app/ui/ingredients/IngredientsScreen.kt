package de.foody.app.ui.ingredients

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.newId
import de.foody.app.ui.common.AutocompleteField
import de.foody.app.ui.common.DecimalField
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.display
import de.foody.app.ui.common.parseDecimal
import de.foody.domain.NutrientBasis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(onBack: () -> Unit, vm: IngredientsViewModel = hiltViewModel()) {
    val list by vm.ingredients.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var mergingId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val msgText = message?.let { (res, n) -> messageText(res, n) }
    LaunchedEffect(msgText) { if (msgText != null) { snackbar.showSnackbar(msgText); vm.messageShown() } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ingredients_title)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(vm::harmonize) { Icon(Icons.Default.AutoFixHigh, stringResource(R.string.ingredients_harmonize)) }
                },
            )
        },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, stringResource(R.string.ingredient_new)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                OutlinedTextField(filter, { filter = it }, placeholder = { Text(stringResource(R.string.search)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
            items(list.filter { it.canonicalName.contains(filter, true) }, key = { it.id }) { ing ->
                ListItem(
                    headlineContent = { Text(ing.canonicalName) },
                    supportingContent = {
                        Text(
                            if (ing.nutrientBasis == null) stringResource(R.string.ingredient_no_nutrients)
                            else stringResource(
                                if (ing.nutrientBasis == NutrientBasis.PER_100_G) R.string.kj_per_100g else R.string.kj_per_100ml,
                                ing.energyKj?.display(0) ?: stringResource(R.string.unknown),
                            ) + (ing.nutrientSource?.let { " · $it" } ?: ""),
                        )
                    },
                    overlineContent = if (ing.category != null) { { Text(ing.category) } } else null,
                    modifier = Modifier.clickable { editingId = ing.id },
                )
                HorizontalDivider()
            }
        }
    }
    val editing = list.firstOrNull { it.id == editingId }
    if (editing != null || creating) {
        IngredientDialog(
            initial = editing,
            onDismiss = { editingId = null; creating = false },
            onSave = { vm.save(it); editingId = null; creating = false },
            onDelete = editing?.let { e -> { vm.delete(e.id); editingId = null } },
            onMerge = editing?.let { e -> { mergingId = e.id; editingId = null } },
        )
    }
    val merging = list.firstOrNull { it.id == mergingId }
    if (merging != null) {
        MergeDialog(merging, list.filter { it.id != merging.id }, onDismiss = { mergingId = null }) { target ->
            vm.merge(merging.id, target.id)
            mergingId = null
        }
    }
}


@Composable
private fun IngredientDialog(
    initial: IngredientEntity?,
    onDismiss: () -> Unit,
    onSave: (IngredientEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onMerge: (() -> Unit)?,
) {
    fun java.math.BigDecimal?.t() = this?.display(3).orEmpty()
    var name by rememberSaveable { mutableStateOf(initial?.canonicalName.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category.orEmpty()) }
    var density by rememberSaveable { mutableStateOf(initial?.densityGPerMl.t()) }
    var piece by rememberSaveable { mutableStateOf(initial?.pieceWeightG.t()) }
    var basis by rememberSaveable { mutableStateOf(initial?.nutrientBasis ?: NutrientBasis.PER_100_G) }
    var kj by rememberSaveable { mutableStateOf(initial?.energyKj.t()) }
    var protein by rememberSaveable { mutableStateOf(initial?.protein.t()) }
    var carbs by rememberSaveable { mutableStateOf(initial?.carbs.t()) }
    var fat by rememberSaveable { mutableStateOf(initial?.fat.t()) }
    var fiber by rememberSaveable { mutableStateOf(initial?.fiber.t()) }
    var sugar by rememberSaveable { mutableStateOf(initial?.sugar.t()) }
    var salt by rememberSaveable { mutableStateOf(initial?.salt.t()) }
    var source by rememberSaveable { mutableStateOf(initial?.nutrientSource ?: "") }

    val manual = stringResource(R.string.source_manual)
    val basisLabels = mapOf(
        NutrientBasis.PER_100_G to stringResource(R.string.basis_100g),
        NutrientBasis.PER_100_ML to stringResource(R.string.basis_100ml),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.ingredient_new else R.string.ingredient_edit)) },
        text = {
            FormColumn(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.field_category)) }, singleLine = true)
                DecimalField(density, { density = it }, stringResource(R.string.field_density))
                DecimalField(piece, { piece = it }, stringResource(R.string.field_piece_weight))
                DropdownField(stringResource(R.string.field_basis), basis, NutrientBasis.entries.toList(),
                    { basisLabels.getValue(it) }, { basis = it })
                Text(stringResource(R.string.nutrients_hint))
                DecimalField(kj, { kj = it }, stringResource(R.string.nutrient_energy) + " (kJ)")
                DecimalField(protein, { protein = it }, stringResource(R.string.nutrient_protein) + " (g)")
                DecimalField(carbs, { carbs = it }, stringResource(R.string.nutrient_carbs) + " (g)")
                DecimalField(fat, { fat = it }, stringResource(R.string.nutrient_fat) + " (g)")
                DecimalField(fiber, { fiber = it }, stringResource(R.string.nutrient_fiber) + " (g)")
                DecimalField(sugar, { sugar = it }, stringResource(R.string.nutrient_sugar) + " (g)")
                DecimalField(salt, { salt = it }, stringResource(R.string.nutrient_salt) + " (g)")
                OutlinedTextField(source, { source = it }, label = { Text(stringResource(R.string.field_source)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val now = System.currentTimeMillis()
                    val values = listOf(kj, protein, carbs, fat, fiber, sugar, salt).map(::parseDecimal)
                    val any = values.any { it != null }
                    onSave(
                        IngredientEntity(
                            id = initial?.id ?: newId(),
                            canonicalName = name.trim(),
                            category = category.ifBlank { null },
                            densityGPerMl = parseDecimal(density),
                            pieceWeightG = parseDecimal(piece),
                            nutrientBasis = if (any) basis else null,
                            energyKj = values[0], protein = values[1], carbs = values[2], fat = values[3],
                            fiber = values[4], sugar = values[5], salt = values[6],
                            nutrientSource = if (any) source.ifBlank { manual } else null,
                            createdAt = initial?.createdAt ?: now,
                            updatedAt = now,
                            version = initial?.version ?: 0,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (onMerge != null) TextButton(onMerge) { Text(stringResource(R.string.ingredient_merge)) }
            if (onDelete != null) TextButton(onDelete) { Text(stringResource(R.string.action_delete)) }
            TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun messageText(res: Int, n: Int): String =
    if (res == R.plurals.ingredients_harmonized) pluralStringResource(res, n, n) else stringResource(res)

/** Auswahl der Zielzutat; danach zeigen alle Rezepte, Vorräte und Listeneinträge auf das Ziel. */
@Composable
private fun MergeDialog(
    source: IngredientEntity,
    candidates: List<IngredientEntity>,
    onDismiss: () -> Unit,
    onMerge: (IngredientEntity) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    val target = candidates.firstOrNull { it.id == targetId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ingredient_merge_title, source.canonicalName)) },
        text = {
            FormColumn {
                Text(stringResource(R.string.ingredient_merge_hint))
                AutocompleteField(
                    stringResource(R.string.field_ingredient), text, { text = it; targetId = null },
                    candidates, { it.canonicalName }, { text = it.canonicalName; targetId = it.id },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { target?.let(onMerge) }) { Text(stringResource(R.string.ingredient_merge)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
