package de.foody.app.ui.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import de.foody.app.ui.common.AutocompleteField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PantryRepository
import de.foody.app.ui.common.DecimalField
import de.foody.app.ui.common.DropdownField
import de.foody.app.ui.common.EmptyState
import de.foody.app.ui.common.FormColumn
import de.foody.app.ui.common.display
import de.foody.app.ui.common.formatAmount
import de.foody.app.ui.common.medium
import de.foody.app.ui.common.parseDecimal
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PantryRow(val item: PantryItemEntity, val name: String)
data class PantryUiState(val rows: List<PantryRow> = emptyList(), val ingredients: List<IngredientEntity> = emptyList())

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantry: PantryRepository,
    ingredients: IngredientRepository,
) : ViewModel() {
    val state = combine(pantry.observeAll(), ingredients.observeAll()) { items, ings ->
        val names = ings.associate { it.id to it.canonicalName }
        PantryUiState(items.map { PantryRow(it, names[it.ingredientId].orEmpty()) }.sortedBy { it.name.lowercase() }, ings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun save(id: String?, ingredientId: String, amount: java.math.BigDecimal, unit: MeasureUnit, bestBefore: LocalDate?) =
        viewModelScope.launch { pantry.save(id, ingredientId, amount, unit, bestBefore) }
    fun delete(id: String) = viewModelScope.launch { pantry.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(vm: PantryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_pantry)) }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, stringResource(R.string.pantry_add)) } },
    ) { padding ->
        if (s.rows.isEmpty()) {
            EmptyState(stringResource(R.string.pantry_empty), Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(s.rows, key = { it.item.id }) { row ->
                    val expired = row.item.bestBeforeDate?.isBefore(LocalDate.now()) == true
                    ListItem(
                        headlineContent = { Text(row.name) },
                        supportingContent = {
                            Text(
                                formatAmount(row.item.amount, row.item.unit) +
                                    (row.item.bestBeforeDate?.let { " · " + stringResource(R.string.pantry_best_before, it.medium()) } ?: ""),
                                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton({ vm.delete(row.item.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
                        },
                        modifier = Modifier.clickable { editingId = row.item.id },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    val editing = s.rows.firstOrNull { it.item.id == editingId }?.item
    if (creating || editing != null) {
        PantryDialog(editing, s.ingredients, onDismiss = { creating = false; editingId = null }) { ing, amount, unit, bb ->
            vm.save(editing?.id, ing, amount, unit, bb)
            creating = false; editingId = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryDialog(
    initial: PantryItemEntity?,
    ingredients: List<IngredientEntity>,
    onDismiss: () -> Unit,
    onSave: (String, java.math.BigDecimal, MeasureUnit, LocalDate?) -> Unit,
) {
    var ingredientId by rememberSaveable { mutableStateOf(initial?.ingredientId) }
    var ingredientText by rememberSaveable {
        mutableStateOf(ingredients.firstOrNull { it.id == initial?.ingredientId }?.canonicalName.orEmpty())
    }
    var amount by rememberSaveable { mutableStateOf(initial?.amount?.display(3).orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(initial?.unit ?: MeasureUnit.GRAM) }
    var bestBefore by rememberSaveable { mutableStateOf(initial?.bestBeforeDate?.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.pantry_add else R.string.pantry_edit)) },
        text = {
            FormColumn {
                // Suche statt Dropdown: nach Importen gibt es schnell mehrere hundert Zutaten
                AutocompleteField(
                    stringResource(R.string.field_ingredient), ingredientText,
                    { ingredientText = it; ingredientId = ingredients.firstOrNull { i -> i.canonicalName.equals(it.trim(), true) }?.id },
                    ingredients, { it.canonicalName }, { ingredientText = it.canonicalName; ingredientId = it.id },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(amount, { amount = it }, stringResource(R.string.field_amount), Modifier.weight(1f))
                    DropdownField(stringResource(R.string.field_unit), unit, MeasureUnit.entries.toList(), { it.symbol }, { unit = it }, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({ pickingDate = true }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Event, null)
                        Text(
                            bestBefore?.let { stringResource(R.string.pantry_best_before, LocalDate.ofEpochDay(it).medium()) }
                                ?: stringResource(R.string.pantry_pick_best_before),
                            Modifier.padding(start = 8.dp),
                        )
                    }
                    if (bestBefore != null) {
                        IconButton({ bestBefore = null }) { Icon(Icons.Default.Close, stringResource(R.string.pantry_clear_best_before)) }
                    }
                }
            }
        },
        confirmButton = {
            val amt = parseDecimal(amount)
            TextButton(
                enabled = ingredientId != null && amt != null && amt.signum() >= 0,
                onClick = { onSave(ingredientId!!, amt!!, unit, bestBefore?.let(LocalDate::ofEpochDay)) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingDate) {
        // DatePicker rechnet in UTC-Millisekunden; Umrechnung über UTC hält den Kalendertag stabil (Invariante 12)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (bestBefore ?: LocalDate.now().toEpochDay()) * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { bestBefore = Math.floorDiv(it, 86_400_000L) }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = { TextButton({ pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}
