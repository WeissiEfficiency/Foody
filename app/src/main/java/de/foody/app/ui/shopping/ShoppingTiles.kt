package de.foody.app.ui.shopping

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.app.R
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.ui.common.formatAmount
import de.foody.domain.ShoppingCatalog

/** Anfangs aufgeklappt ist nur „Zuletzt verwendet“ – der Katalog ist lang, zugeklappt bleibt die Liste übersichtlich. */
private const val RECENT = "recent"
private const val MAX_RECENT = 18

/**
 * Einkaufsliste als Kacheln: oben rot, was gekauft werden soll (Antippen = gekauft); darunter „Zuletzt verwendet“
 * und der Katalog nach Abteilungen (Antippen = auf die Liste). Die Suche filtert den Katalog und legt Unbekanntes
 * als eigenen Artikel an.
 */
@Composable
fun ShoppingTiles(
    items: List<ShoppingItemEntity>,
    onTapCatalog: (name: String, section: String) -> Unit,
    onBuy: (ShoppingItemEntity) -> Unit,
    onPutBack: (ShoppingItemEntity) -> Unit,
    onDetails: (ShoppingItemEntity) -> Unit,
    onAddFromSearch: (String) -> Unit,
    header: LazyGridScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Als Text gespeichert: überlebt Drehen und Prozessende ohne eigenen Saver
    var expandedRaw by rememberSaveable { mutableStateOf(RECENT) }
    val expanded = expandedRaw.split('|').toSet()
    fun toggleSection(key: String) {
        expandedRaw = (if (key in expanded) expanded - key else expanded + key).joinToString("|")
    }

    val sectionIndex = ShoppingCatalog.sectionOrder.withIndex().associate { (i, s) -> s to i }
    val open = items.filter { !it.checked }
        .sortedWith(compareBy({ sectionIndex[ShoppingCatalog.sectionFor(it.category, it.name)] }, { it.sortOrder }))
    val recent = items.filter { it.checked }.sortedByDescending { it.sortOrder }.distinctBy { it.name.lowercase() }.take(MAX_RECENT)
    fun onListOf(name: String) = open.any { ShoppingCatalog.sameItem(it.name, name) }
    val recentTitle = stringResource(R.string.shopping_recent)
    val ownNames = items.map { it.name }.filter { ShoppingCatalog.find(it) == null }.distinctBy { it.lowercase() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        header()
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            SearchField(query, { query = it }) { onAddFromSearch(query); query = "" }
        }

        val q = query.trim()
        if (q.isNotEmpty()) {
            // Suche: passende Listeneinträge, Katalogtreffer und – wenn unbekannt – „„…“ hinzufügen“
            val hits = ShoppingCatalog.search(q)
            items(hits, key = { (item, _) -> "hit-${item.name}" }) { (item, section) ->
                Tile(item.emoji, item.name, null, onListOf(item.name), { onTapCatalog(item.name, section.name); query = "" })
            }
            if (hits.none { it.first.name.equals(q, ignoreCase = true) }) {
                item(key = "add-custom") {
                    Tile("➕", stringResource(R.string.shopping_add_custom, q), null, false, { onAddFromSearch(q); query = "" })
                }
            }
            return@LazyVerticalGrid
        }

        if (open.isEmpty()) {
            item(key = "nothing", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.shopping_nothing_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.shopping_nothing_hint), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(open, key = { "open-${it.id}" }) { item ->
                Tile(
                    ShoppingCatalog.emojiFor(item.name, item.category), item.name, item.detail(), true,
                    onClick = { onBuy(item) }, onLongClick = { onDetails(item) }, modifier = Modifier.animateItem(),
                )
            }
        }

        if (recent.isNotEmpty()) {
            sectionHeader(RECENT, recentTitle, RECENT in expanded, ::toggleSection)
            if (RECENT in expanded) {
                items(recent, key = { "recent-${it.id}" }) { item ->
                    Tile(
                        ShoppingCatalog.emojiFor(item.name, item.category), item.name, item.detail(), onListOf(item.name),
                        onClick = { onPutBack(item) }, onLongClick = { onDetails(item) }, modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        for (section in ShoppingCatalog.sections) {
            sectionHeader(section.name, section.name, section.name in expanded, ::toggleSection)
            if (section.name in expanded) {
                items(section.items, key = { "cat-${section.name}-${it.name}" }) { item ->
                    Tile(item.emoji, item.name, null, onListOf(item.name), { onTapCatalog(item.name, section.name) })
                }
            }
        }
        if (ownNames.isNotEmpty()) {
            sectionHeader(ShoppingCatalog.OWN_ITEMS, ShoppingCatalog.OWN_ITEMS, ShoppingCatalog.OWN_ITEMS in expanded, ::toggleSection)
            if (ShoppingCatalog.OWN_ITEMS in expanded) {
                items(ownNames, key = { "own-$it" }) { name ->
                    Tile(ShoppingCatalog.emojiFor(name), name, null, onListOf(name), { onTapCatalog(name, ShoppingCatalog.OWN_ITEMS) })
                }
            }
        }
    }
}

/** Untertitel der Kachel: eigene Angabe, sonst die Menge aus dem Planer. */
private fun ShoppingItemEntity.detail(): String? =
    note ?: if (amount != null && unit != null && amount.signum() > 0) formatAmount(amount, unit) else null

private fun LazyGridScope.sectionHeader(key: String, title: String, expanded: Boolean, onToggle: (String) -> Unit) {
    item(key = "header-$key", span = { GridItemSpan(maxLineSpan) }) {
        val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp)
                .clickable(role = Role.Button, onClickLabel = title) { onToggle(key) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.rotate(rotation))
        }
    }
}

@Composable
private fun SearchField(text: String, onText: (String) -> Unit, onSubmit: () -> Unit) {
    TextField(
        text, onText,
        placeholder = { Text(stringResource(R.string.shopping_search)) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (text.isNotEmpty()) IconButton({ onText("") }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit() }),
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

/** Eine Kachel: rot (Tertiärfarbe) = steht auf der Liste, grün (Primärfarbe) = nicht auf der Liste. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    emoji: String,
    name: String,
    detail: String?,
    onList: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val stateText = stringResource(if (onList) R.string.shopping_state_on_list else R.string.shopping_state_not_on_list)
    val container = if (onList) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val content = if (onList) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.aspectRatio(1f)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.sources_title) },
                onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            ) {
                haptic.performHapticFeedback(if (onList) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onClick()
            }
            .semantics { stateDescription = stateText },
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(emoji, fontSize = 30.sp)
            Text(
                name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
