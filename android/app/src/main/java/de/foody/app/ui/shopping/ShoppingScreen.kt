package de.foody.app.ui.shopping

import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.ui.common.HeaderAction
import de.foody.app.ui.common.ScreenHeader
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
    val listView by vm.listView.collectAsStateWithLifecycle()
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
        containerColor = Color.Transparent,
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
                HeaderAction(
                    if (listView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    stringResource(if (listView) R.string.shopping_view_tiles else R.string.shopping_view_list),
                ) { vm.toggleListView() }
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
            ShoppingTiles(
                items = s.items,
                onTapCatalog = { name, section -> vm.tapCatalog(name, section, defaultEmptyName) },
                onBuy = { vm.toggle(it) },
                onPutBack = { vm.toggle(it) },
                onDetails = { vm.showSources(it) },
                onAddFromSearch = { vm.addFromSearch(it, defaultEmptyName) },
                asList = listView,
                header = {
                    if (s.lists.size > 1) {
                        item(key = "lists", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                },
            )
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
        // Langdruck auf eine Kachel: Angabe ergänzen („500 g“, „Bio“), Herkunft sehen, Artikel entfernen
        var note by rememberSaveable(item.id) { mutableStateOf(item.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = vm::hideSources,
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        note, { note = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.shopping_item_note)) },
                        placeholder = { Text(stringResource(R.string.shopping_item_note_hint)) },
                    )
                    if (list.isNotEmpty()) {
                        Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.labelLarge)
                        list.forEach { src ->
                            Text("${formatAmount(src.contributedAmount, src.unit)} · ${src.recipeName} (${src.date.pretty()})",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ vm.setNote(item, note); vm.hideSources() }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton({ vm.hideSources(); vm.delete(item) }) { Text(stringResource(R.string.action_remove)) } },
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

