package de.foody.feature.rezepte.liste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.domain.Diet
import de.foody.domain.Mahlzeit

private val DIAETEN = listOf(
    Diet.VEGETARIAN to "Vegetarisch", Diet.VEGAN to "Vegan", Diet.HIGH_PROTEIN to "Eiweißreich",
    Diet.LOW_CARB to "Low Carb", Diet.LIGHT to "Leicht",
)

private val MAHLZEITEN = listOf(
    Mahlzeit.FRUEHSTUECK to "Frühstück", Mahlzeit.MITTAGESSEN to "Mittagessen", Mahlzeit.ABENDESSEN to "Abendessen",
    Mahlzeit.SNACK to "Snack",
)

@Composable
internal fun ListeKopf(onNeu: () -> Unit, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Rezepte", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, color = FoodyTheme.farben.text)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr", tint = FoodyTheme.farben.text) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Neues Rezept") }, onClick = { menu = false; onNeu() })
                DropdownMenuItem(text = { Text("Markdown importieren") }, onClick = { menu = false; onImport() })
            }
        }
    }
}

@Composable
internal fun Suche(text: String, onText: (String) -> Unit) {
    OutlinedTextField(
        text, onText, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        label = { Text("Rezepte suchen") }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
    )
}

/** Eine waagerecht scrollende Reihe: Favoriten, Oft gekocht, Ernährung, Mahlzeit, Archiv, Sortierung, dann Tags. */
@Composable
internal fun FilterChips(f: ListeFilter, tags: List<String>, aendern: ((ListeFilter) -> ListeFilter) -> Unit) {
    var sortierMenu by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box {
                FoodyChip("Sortierung: ${f.sortierung.text}", false, { sortierMenu = true })
                DropdownMenu(sortierMenu, onDismissRequest = { sortierMenu = false }) {
                    Sortierung.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.text) }, onClick = { sortierMenu = false; aendern { it.copy(sortierung = s) } })
                    }
                }
            }
        }
        item { FoodyChip("Favoriten", f.favoriten, { aendern { it.copy(favoriten = !it.favoriten) } }) }
        item { FoodyChip("Oft gekocht", f.oftGekocht, { aendern { it.copy(oftGekocht = !it.oftGekocht) } }) }
        items(DIAETEN) { (d, text) ->
            FoodyChip(text, d in f.diaeten, { aendern { it.copy(diaeten = if (d in it.diaeten) it.diaeten - d else it.diaeten + d) } })
        }
        items(MAHLZEITEN) { (m, text) ->
            FoodyChip(text, f.mahlzeit == m, { aendern { it.copy(mahlzeit = if (it.mahlzeit == m) null else m) } })
        }
        item { FoodyChip("Archiv", f.archiv, { aendern { it.copy(archiv = !it.archiv) } }) }
        items(tags) { t ->
            FoodyChip("#$t", t in f.tags, { aendern { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) } })
        }
    }
}
