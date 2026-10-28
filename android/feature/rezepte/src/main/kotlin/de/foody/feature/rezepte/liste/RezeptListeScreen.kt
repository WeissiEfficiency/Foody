package de.foody.feature.rezepte.liste

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.RezeptkarteRaster
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.token.FoodyFormen

/**
 * Rezeptliste: Kopf mit Menü, Suche, Filterchips, Bildraster. [ausgewaehlt] hebt auf dem Tablet das rechts offene
 * Rezept hervor.
 */
@Composable
fun RezeptListeRoute(
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
    onImport: () -> Unit,
    ausgewaehlt: String? = null,
    vm: RezeptListeViewModel = hiltViewModel(),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ListeKopf(onNeu, onImport)
        if (z.geladen && z.keineRezepte) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LeererZustandFlaeche(
                    Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte",
                    "Lege ein Rezept an oder importiere Markdown-Dateien.", aktionText = "Anlegen", onAktion = onNeu,
                )
                FoodyKnopf("Importieren", onImport, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
            }
            return@Column
        }
        Suche(z.filter.suche) { s -> vm.filter { it.copy(suche = s) } }
        FilterChips(z.filter, z.tags, vm::filter)
        if (z.geladen && z.treffer.isEmpty()) {
            LeererZustandFlaeche(
                Icons.AutoMirrored.Outlined.MenuBook, "Nichts gefunden", "Kein Rezept passt zu Suche und Filtern.",
                aktionText = "Filter zurücksetzen", onAktion = vm::zuruecksetzen, modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(z.treffer, key = { it.id }) { k ->
                val markiert = if (k.id == ausgewaehlt) Modifier.border(2.dp, FoodyTheme.farben.primaer, FoodyFormen.mittel) else Modifier
                RezeptkarteRaster(
                    titel = k.name, onKlick = { onOeffnen(k.id) }, modifier = markiert, bild = rezeptBild(k.bild),
                    favorit = k.favorit, zeit = k.zeitMinuten?.let { "$it Min." }, kcal = k.kcalJePortion?.let { "$it kcal" },
                )
            }
        }
    }
}
