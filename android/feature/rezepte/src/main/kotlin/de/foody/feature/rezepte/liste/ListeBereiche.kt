package de.foody.feature.rezepte.liste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.komponenten.Suchfeld
import de.foody.core.designsystem.token.FoodyAbstaende
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

/** Kopf wie im Entwurf: Überzeile mit der Anzahl, Titel, runder „+“ (neues Rezept oder Markdown importieren). */
@Composable
internal fun ListeKopf(anzahl: Int?, onNeu: () -> Unit, onImport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Seitenkopf(
        "Rezepte",
        Modifier.fillMaxWidth().padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s3, top = FoodyAbstaende.s3),
        ueberzeile = anzahl?.let { if (it == 1) "1 Rezept" else "$it Rezepte" },
    ) {
        Box {
            RunderKnopf(Icons.Outlined.Add, "Rezept hinzufügen", onKlick = { menu = true }, primaer = true)
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Neues Rezept") }, onClick = { menu = false; onNeu() })
                DropdownMenuItem(text = { Text("Markdown importieren") }, onClick = { menu = false; onImport() })
            }
        }
    }
}

@Composable
internal fun Suche(text: String, onText: (String) -> Unit) {
    Suchfeld(text, onText, "Rezepte, Zutaten …", Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2))
}

/**
 * Eine waagerecht scrollende Reihe: „Alle“ (kein Filter), Favoriten, Oft gekocht, Ernährung, Mahlzeit, Tags, Archiv,
 * zuletzt die Sortierung.
 */
@Composable
internal fun FilterChips(f: ListeFilter, tags: List<String>, aendern: ((ListeFilter) -> ListeFilter) -> Unit) {
    var sortierMenu by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = FoodyAbstaende.s4), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        item { FoodyChip("Alle", f.ohneFilter, { aendern { ListeFilter(suche = it.suche, sortierung = it.sortierung) } }) }
        item { FoodyChip("Favoriten", f.favoriten, { aendern { it.copy(favoriten = !it.favoriten) } }) }
        item { FoodyChip("Oft gekocht", f.oftGekocht, { aendern { it.copy(oftGekocht = !it.oftGekocht) } }) }
        items(DIAETEN) { (d, text) ->
            FoodyChip(text, d in f.diaeten, { aendern { it.copy(diaeten = if (d in it.diaeten) it.diaeten - d else it.diaeten + d) } })
        }
        items(MAHLZEITEN) { (m, text) ->
            FoodyChip(text, f.mahlzeit == m, { aendern { it.copy(mahlzeit = if (it.mahlzeit == m) null else m) } })
        }
        items(tags) { t ->
            FoodyChip("#$t", t in f.tags, { aendern { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) } })
        }
        item { FoodyChip("Archiv", f.archiv, { aendern { it.copy(archiv = !it.archiv) } }) }
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
    }
}
