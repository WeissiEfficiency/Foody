package de.foody.feature.woche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.Mahlzeit

/**
 * Rezepte für die Auswahl: Teilstring ohne Groß/klein in Name, Tags und Zutaten; zur [mahlzeit] passende zuerst
 * (`einordnung.passtZu`), dann nach Name. Ohne bekannte Mahlzeit (eigener Name) nur nach Name.
 */
fun auswahlSortiert(karten: List<RezeptKarte>, suche: String, mahlzeit: Mahlzeit?): List<RezeptKarte> {
    val s = suche.trim().lowercase()
    return karten
        .filter { k -> s.isEmpty() || (listOf(k.name) + k.tags + k.zutatNamen).any { s in it.lowercase() } }
        .sortedWith(compareBy({ mahlzeit != null && !it.einordnung.passtZu(mahlzeit) }, { it.name.lowercase() }))
}

/** Blatt zum Hinzufügen: Suche und Liste mit Name und kcal je Portion. */
@Composable
internal fun RezeptAuswahlBlatt(mahlzeit: String, karten: List<RezeptKarte>, onWahl: (RezeptKarte) -> Unit, onSchliessen: () -> Unit) {
    var suche by rememberSaveable { mutableStateOf("") }
    val treffer = auswahlSortiert(karten, suche, Mahlzeit.ausText(mahlzeit))
    GlasBlatt(onSchliessen) {
        Column(
            Modifier.padding(horizontal = FoodyAbstaende.s4).padding(bottom = FoodyAbstaende.s4),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        ) {
            Text("${mahlzeitAnzeige(mahlzeit)}: Rezept wählen", style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
            FoodyTextfeld("Rezept suchen", suche) { suche = it }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(treffer, key = { it.id }) { k ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onWahl(k) }.padding(vertical = FoodyAbstaende.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(k.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                        k.kcalJePortion?.let {
                            Text("${ganzzahlDeutsch(it)} kcal", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
                        }
                    }
                }
            }
        }
    }
}
