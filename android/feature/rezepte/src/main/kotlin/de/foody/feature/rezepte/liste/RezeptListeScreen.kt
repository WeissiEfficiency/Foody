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
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.feature.rezepte.liste.stil.RezepteSage
import de.foody.feature.rezepte.liste.stil.RezepteStil
import de.foody.feature.rezepte.liste.stil.RezepteSzene

/**
 * Rezeptliste: Kopf mit Menü, Suche, Filterchips, Bildraster. [ausgewaehlt] hebt auf dem Tablet das rechts offene
 * Rezept hervor. Die Düstere Sage zeigt in Immersiv das Grimoire (`stil/RezepteSage.kt`),
 * die Stile R–V ihre Szenen (`stil/RezepteStile.kt`).
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
    // Düstere Sage in Immersiv: das Grimoire mit derselben Liste und denselben Filtern
    if (FoodyTheme.stil.immersiv(FoodyStil.SAGE)) {
        RezepteSage(z, vm::filter, vm::zuruecksetzen, onOeffnen, onNeu, onImport)
        return
    }
    // Stile R–V in Immersiv: ihre Szene mit derselben Liste, denselben Filtern und Zielen
    RezepteSzene.aktuell()?.let { stil ->
        RezepteStil(stil, z, vm::filter, vm::zuruecksetzen, onOeffnen, onNeu, onImport)
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ListeKopf(z.anzahl.takeIf { z.geladen && !z.keineRezepte }, onNeu, onImport)
        if (z.geladen && z.keineRezepte) {
            Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
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
                aktionText = "Filter zurücksetzen", onAktion = vm::zuruecksetzen, modifier = Modifier.padding(FoodyAbstaende.s4),
            )
            return@Column
        }
        LazyVerticalGrid(
            // 140 dp: auch auf schmalen Handys (große Anzeigegröße) zwei Spalten wie im Entwurf
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = ueberLeiste(104.dp)),
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            items(z.treffer, key = { it.id }) { k ->
                val markiert = if (k.id == ausgewaehlt) Modifier.border(2.dp, FoodyTheme.farben.primaer, FoodyTheme.formen.mittel) else Modifier
                RezeptkarteRaster(
                    titel = k.name, onKlick = { onOeffnen(k.id) }, modifier = markiert, bild = rezeptBild(k.bild),
                    favorit = k.favorit, zeit = k.zeitMinuten?.let { "$it min" }, kcal = k.kcalJePortion?.let { "$it kcal" },
                )
            }
        }
    }
}
