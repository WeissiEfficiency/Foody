package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.data.einkauf.ZuletztArtikel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.domain.ShoppingCatalog

/**
 * Wie eine Szene „Hinzufügen“ zeigt: [ueberschrift] über den Pillen (ein Text im Stil), [name] macht aus einer
 * Abteilung ([ZULETZT], Katalog-Abteilung oder „Eigene Artikel“, mit ihrem Emoji) den Text der Pille, [pille] zeichnet
 * sie; Standard ist der Filter-Chip des Stils ([FoodyChip]).
 */
internal class PillenAussehen(
    val ueberschrift: @Composable (Modifier) -> Unit,
    val name: (abteilung: String, emoji: String) -> String,
    val pille: @Composable (text: String, gewaehlt: Boolean, onKlick: () -> Unit) -> Unit = { t, g, k -> FoodyChip(t, g, k) },
)

/** Modifier für die Überschrift über den Pillen: Überschrift für TalkBack, gelesen als „Hinzufügen“. */
internal val HinzufuegenKopf: Modifier = Modifier.semantics {
    heading()
    contentDescription = "Hinzufügen"
}

/**
 * „Hinzufügen“ in den Szenen: die Abteilungen (Zuletzt, falls da, der Katalog, die eigenen Artikel der Liste, falls da)
 * als Pillen, die umbrechen, darunter nur die Kacheln der Abteilung [gewaehlt] (`null` oder nicht mehr da = die
 * erste). Gescrollt wird nur senkrecht. Antippen nach der Kachelregel; aus „Zuletzt“ kommt der Artikel wieder auf die Liste.
 */
internal fun LazyGridScope.hinzufuegenMitPillen(
    zuletzt: List<ZuletztArtikel>,
    alle: List<EinkaufsArtikel>,
    gewaehlt: String?,
    aussehen: PillenAussehen,
    onWaehlen: (String) -> Unit,
    onZuletzt: (ZuletztArtikel) -> Unit,
    onTipp: (name: String, abteilung: String) -> Unit,
) {
    val eigene = alle.filter { ShoppingCatalog.find(it.name) == null }.distinctBy { it.name.lowercase() }
    val eigenName = ShoppingCatalog.OWN_ITEMS
    val pillen = buildList {
        if (zuletzt.isNotEmpty()) add(ZULETZT to aussehen.name(ZULETZT, "🕘"))
        ShoppingCatalog.sections.forEach { add(it.name to aussehen.name(it.name, it.emoji)) }
        if (eigene.isNotEmpty()) add(eigenName to aussehen.name(eigenName, "🛒"))
    }
    val aktiv = gewaehlt?.takeIf { g -> pillen.any { it.first == g } } ?: pillen.first().first
    item(key = "hinzufuegen", span = { GridItemSpan(maxLineSpan) }) { Pillen(pillen, aktiv, aussehen, onWaehlen) }
    when (aktiv) {
        ZULETZT -> items(zuletzt, key = { "hinzu-zuletzt-${it.name}" }) { z ->
            EinkaufKachel(z.name, "", z.emoji, stand(z.name, alle), { onZuletzt(z) })
        }
        eigenName -> items(eigene, key = { "hinzu-eigen-${it.name.lowercase()}" }) { e ->
            EinkaufKachel(e.name, "", e.emoji, stand(e.name, alle), { onTipp(e.name, eigenName) })
        }
        else -> {
            val s = ShoppingCatalog.sections.first { it.name == aktiv }
            items(s.items, key = { "hinzu-${s.name}-${it.name}" }) { k ->
                EinkaufKachel(k.name, "", k.emoji, stand(k.name, alle), { onTipp(k.name, s.name) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pillen(pillen: List<Pair<String, String>>, aktiv: String, aussehen: PillenAussehen, onWaehlen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        aussehen.ueberschrift(HinzufuegenKopf)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pillen.forEach { (name, text) -> aussehen.pille(text, name == aktiv) { onWaehlen(name) } }
        }
    }
}
