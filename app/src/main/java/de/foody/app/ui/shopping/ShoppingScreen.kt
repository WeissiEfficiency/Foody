package de.foody.app.ui.shopping

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.foody.app.ui.common.HeaderAction
import de.foody.app.ui.common.RoundCheck
import de.foody.app.ui.common.ScreenHeader
import de.foody.app.ui.theme.EyebrowStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.ui.common.EmptyState
import de.foody.app.ui.common.formatAmount
import de.foody.app.ui.common.formatQuantity
import de.foody.app.ui.common.pretty
import de.foody.domain.DiffType
import de.foody.domain.ExportResult
import de.foody.domain.Quantity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val generate by vm.generate.collectAsStateWithLifecycle()
    val diff by vm.diff.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var manualText by rememberSaveable { mutableStateOf("") }
    var confirmDeleteList by rememberSaveable { mutableStateOf(false) }

    val chooserTitle = stringResource(R.string.share_title)
    val shareError = stringResource(R.string.share_error)
    val deletedText = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val defaultEmptyName = stringResource(R.string.shopping_default_name)

    LaunchedEffect(undo) {
        if (undo != null) {
            val r = snackbar.showSnackbar(deletedText, undoLabel)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.undoShown()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::openGenerate,
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                text = { Text(stringResource(R.string.shopping_generate)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader(stringResource(R.string.nav_shopping), s.selected?.name ?: stringResource(R.string.shopping_title_empty)) {
                if (s.selected != null) {
                    if (s.selected?.rangeStart != null) {
                        HeaderAction(Icons.Default.Refresh, stringResource(R.string.shopping_recalculate)) { vm.recalculate() }
                    }
                    HeaderAction(Icons.Default.Share, stringResource(R.string.action_share)) {
                        scope.launch {
                            val snap = vm.snapshot() ?: return@launch
                            val result = AndroidShareExporter(context, chooserTitle).export(snap)
                            if (result is ExportResult.Failure) snackbar.showSnackbar(shareError)
                        }
                    }
                    HeaderAction(Icons.Default.DeleteSweep, stringResource(R.string.shopping_delete_list)) { confirmDeleteList = true }
                }
            }
            if (s.lists.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.lists.forEach { l ->
                        FilterChip(
                            l.id == s.selected?.id, { vm.select(l.id) }, label = { Text(l.name) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
            }
            val selected = s.selected
            if (selected == null) {
                EmptyState(
                    stringResource(R.string.shopping_empty), Modifier.weight(1f), icon = Icons.Outlined.ShoppingCart,
                    actionLabel = stringResource(R.string.shopping_create_empty), onAction = { vm.createEmpty(defaultEmptyName) },
                )
                Spacer(Modifier.height(88.dp))
                return@Column
            }
            val open = s.items.filter { !it.checked }
            val done = s.items.filter { it.checked }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ShoppingProgress(done.size, s.items.size) }
                item {
                    AddItemField(manualText, { manualText = it }) { vm.addManual(manualText); manualText = "" }
                }
                // Offene Artikel nach Abteilung gruppiert – so läuft man den Supermarkt einmal ab
                open.groupBy { it.category }.toSortedMap(compareBy(nullsLast()) { it }).forEach { (category, items) ->
                    item(key = "cat-$category") { GroupTitle(category ?: stringResource(R.string.shopping_category_other)) }
                    items(items, key = { it.id }) { item -> ShoppingRow(item, vm, Modifier.animateItem()) }
                }
                if (done.isNotEmpty()) {
                    item(key = "done") { GroupTitle(stringResource(R.string.shopping_done_section, done.size)) }
                    items(done, key = { it.id }) { item -> ShoppingRow(item, vm, Modifier.animateItem()) }
                }
            }
        }
    }

    generate?.let { g ->
        val name = stringResource(R.string.shopping_name_for, g.start.pretty(), g.start.plusDays(g.days - 1L).pretty())
        AlertDialog(
            onDismissRequest = vm::closeGenerate,
            title = { Text(stringResource(R.string.shopping_generate)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ vm.updateGenerate { it.copy(start = it.start.minusDays(1)) } }) { Text("−") }
                        Text(stringResource(R.string.shopping_from, g.start.pretty()), Modifier.weight(1f))
                        TextButton({ vm.updateGenerate { it.copy(start = it.start.plusDays(1)) } }) { Text("+") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 7, 14).forEach { d ->
                            FilterChip(g.days == d, { vm.updateGenerate { it.copy(days = d) } }, label = { Text(pluralStringResource(R.plurals.days_n, d, d)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.shopping_use_pantry), Modifier.weight(1f))
                        Switch(g.usePantry, { c -> vm.updateGenerate { it.copy(usePantry = c) } })
                    }
                    Text(stringResource(R.string.shopping_preview_hint), style = MaterialTheme.typography.bodySmall)
                    if (!g.loading && g.previews.isEmpty()) Text(stringResource(R.string.shopping_nothing_planned))
                    g.previews.forEach { p ->
                        val included = p.need.ingredientId !in g.excluded
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleExcluded(p.need.ingredientId) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(included, { vm.toggleExcluded(p.need.ingredientId) })
                            Column(Modifier.weight(1f)) {
                                Text(p.ingredientName)
                                val pantryInfo = if (!p.need.fromPantry.isZero()) " · " + stringResource(R.string.shopping_from_pantry, formatQuantity(p.need.fromPantry)) else ""
                                Text(formatQuantity(p.need.toBuy) + pantryInfo, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = g.previews.any { it.need.ingredientId !in g.excluded && !it.need.toBuy.isZero() }, onClick = { vm.createList(name) }) {
                    Text(stringResource(R.string.shopping_create))
                }
            },
            dismissButton = { TextButton(vm::closeGenerate) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    diff?.let { d ->
        val changes = d.entries.filter { it.type != DiffType.UNCHANGED }
        AlertDialog(
            onDismissRequest = vm::dismissDiff,
            title = { Text(stringResource(R.string.shopping_recalculate)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (changes.isEmpty()) Text(stringResource(R.string.diff_none))
                    changes.forEach { e ->
                        val label = e.new?.let { n -> d.previews.firstOrNull { it.need.key == n.key }?.ingredientName } ?: e.old?.name.orEmpty()
                        val text = when (e.type) {
                            DiffType.ADDED -> stringResource(R.string.diff_added, label, formatQuantity(e.new!!.toBuy))
                            DiffType.CHANGED -> stringResource(R.string.diff_changed, label, oldText(e.old!!), formatQuantity(e.new!!.toBuy))
                            DiffType.REMOVED -> stringResource(
                                if (e.old!!.checked) R.string.diff_removed_kept else R.string.diff_removed, label,
                            )
                            DiffType.UNCHANGED -> ""
                        }
                        Text(text, Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = { TextButton(enabled = changes.isNotEmpty(), onClick = vm::applyDiff) { Text(stringResource(R.string.action_apply)) } },
            dismissButton = { TextButton(vm::dismissDiff) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    sources?.let { (item, list) ->
        AlertDialog(
            onDismissRequest = vm::hideSources,
            title = { Text(item.name) },
            text = {
                Column {
                    if (list.isEmpty()) Text(stringResource(R.string.sources_none))
                    list.forEach { src ->
                        Text("${formatAmount(src.contributedAmount, src.unit)} · ${src.recipeName} (${src.date.pretty()})")
                    }
                }
            },
            confirmButton = { TextButton(vm::hideSources) { Text(stringResource(R.string.action_close)) } },
        )
    }

    if (confirmDeleteList) {
        AlertDialog(
            onDismissRequest = { confirmDeleteList = false },
            title = { Text(stringResource(R.string.shopping_delete_list)) },
            text = { Text(stringResource(R.string.shopping_delete_list_text)) },
            confirmButton = { TextButton({ vm.deleteList(); confirmDeleteList = false }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirmDeleteList = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun oldText(i: ShoppingItemEntity): String =
    if (i.amount != null && i.unit != null) formatQuantity(Quantity.of(i.amount, i.unit)) else ""

@Composable
private fun ShoppingProgress(done: Int, total: Int) {
    if (total == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.shopping_progress, done, total),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun AddItemField(text: String, onText: (String) -> Unit, onAdd: () -> Unit) {
    TextField(
        text, onText,
        placeholder = { Text(stringResource(R.string.shopping_add_manual)) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        trailingIcon = {
            FilledIconButton(onAdd, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, stringResource(R.string.action_add)) }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun ShoppingRow(item: ShoppingItemEntity, vm: ShoppingViewModel, modifier: Modifier = Modifier) {
    val stateDesc = stringResource(if (item.checked) R.string.state_checked else R.string.state_open)
    val haptic = LocalHapticFeedback.current
    val toggle: () -> Unit = {
        haptic.performHapticFeedback(if (item.checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        vm.toggle(item)
    }
    Surface(
        onClick = toggle,
        shape = MaterialTheme.shapes.medium,
        color = if (item.checked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = if (item.checked) 0.dp else 1.dp,
        modifier = modifier.fillMaxWidth().semantics { stateDescription = stateDesc },
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundCheck(item.checked, item.name, toggle)
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            item.amount?.let { a -> item.unit?.let { u ->
                Text(
                    formatQuantity(Quantity.of(a, u)),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            } }
            // Nebenaktionen dezent, damit Name und Menge im Vordergrund stehen
            val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            if (!item.manual) {
                IconButton({ vm.showSources(item) }) {
                    Icon(Icons.Outlined.Info, stringResource(R.string.sources_title), Modifier.size(20.dp), tint = muted)
                }
            }
            IconButton({ vm.delete(item) }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), Modifier.size(20.dp), tint = muted)
            }
        }
    }
}
