package de.foody.feature.einkauf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.Abteilung
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.data.einkauf.ZuletztArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.Kachel
import de.foody.core.designsystem.komponenten.KachelEmoji
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.ShoppingCatalog

/** Schlüssel von „Zuletzt verwendet“ unter den aufklappbaren Abschnitten. */
internal const val ZULETZT = "Zuletzt verwendet"

/** Wie ein Artikel auf der angezeigten Liste steht. */
internal enum class Stand { OFFEN, GEKAUFT, NICHT }

internal fun stand(name: String, alle: List<EinkaufsArtikel>): Stand {
    val gleiche = alle.filter { ShoppingCatalog.sameItem(it.name, name) }
    return when {
        gleiche.any { !it.gekauft } -> Stand.OFFEN
        gleiche.isNotEmpty() -> Stand.GEKAUFT
        else -> Stand.NICHT
    }
}

@Composable
private fun EinkaufKachel(
    name: String,
    menge: String,
    emoji: String,
    stand: Stand,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    onLangdruck: (() -> Unit)? = null,
    // Auf der Liste selbst ohne Rand; im Katalog mit Rand für „auf der Liste“. Das Symbol ist überall dasselbe (Nutzerwunsch statt Monogramm)
    aufListe: Boolean = false,
) = Kachel(
    name, menge, onKlick, modifier, untertitel = untertitel,
    abgehakt = stand == Stand.GEKAUFT, aufDerListe = stand == Stand.OFFEN && !aufListe, onLangdruck = onLangdruck,
    zustandsText = when (stand) {
        Stand.OFFEN -> "auf der Liste"
        Stand.GEKAUFT -> "gekauft"
        Stand.NICHT -> "nicht auf der Liste"
    },
    symbol = { KachelEmoji(emoji) },
)

private fun LazyGridScope.ganzeZeile(key: String, inhalt: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { inhalt() }

/** Aufklappbarer Abschnittskopf (Zuletzt, Katalog-Abteilungen). */
@Composable
private fun Klappkopf(titel: String, offen: Boolean, onKlick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = if (offen) "Zuklappen" else "Aufklappen", onClick = onKlick)
            .padding(top = FoodyAbstaende.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text, modifier = Modifier.weight(1f))
        Icon(if (offen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = FoodyTheme.farben.text)
    }
}

/** Offene Artikel je Abteilung; Antippen = gekauft, Langdruck = Details. */
internal fun LazyGridScope.offeneKacheln(abteilungen: List<Abteilung>, onGekauft: (String) -> Unit, onDetails: (String) -> Unit) {
    for (a in abteilungen) {
        ganzeZeile("offen-kopf-${a.name}") { Augenbraue(a.name, Modifier.padding(top = FoodyAbstaende.s3)) }
        items(a.artikel, key = { "offen-${it.id}" }) { x ->
            EinkaufKachel(
                x.name, x.menge.orEmpty(), x.emoji, Stand.OFFEN, onKlick = { onGekauft(x.id) }, untertitel = x.notiz,
                onLangdruck = { onDetails(x.id) }, aufListe = true,
            )
        }
    }
}

/** „Zuletzt verwendet“: Antippen setzt den Artikel wieder auf die Liste. */
internal fun LazyGridScope.zuletztKacheln(
    zuletzt: List<ZuletztArtikel>,
    alle: List<EinkaufsArtikel>,
    offen: Boolean,
    onUmschalten: () -> Unit,
    onTipp: (ZuletztArtikel) -> Unit,
) {
    if (zuletzt.isEmpty()) return
    ganzeZeile("zuletzt-kopf") { Klappkopf(ZULETZT, offen, onUmschalten) }
    if (offen) items(zuletzt, key = { "zuletzt-${it.name}" }) { z -> EinkaufKachel(z.name, "", z.emoji, stand(z.name, alle), { onTipp(z) }) }
}

/** Katalog nach Abteilungen in Laufreihenfolge und „Eigene Artikel“ der Liste; Antippen nach der Kachelregel. */
internal fun LazyGridScope.katalogKacheln(
    alle: List<EinkaufsArtikel>,
    aufgeklappt: Set<String>,
    onUmschalten: (String) -> Unit,
    onTipp: (name: String, abteilung: String) -> Unit,
) {
    for (s in ShoppingCatalog.sections) {
        ganzeZeile("katalog-kopf-${s.name}") { Klappkopf("${s.emoji} ${s.name}", s.name in aufgeklappt) { onUmschalten(s.name) } }
        if (s.name in aufgeklappt) {
            items(s.items, key = { "katalog-${s.name}-${it.name}" }) { i ->
                EinkaufKachel(i.name, "", i.emoji, stand(i.name, alle), { onTipp(i.name, s.name) })
            }
        }
    }
    val eigene = alle.filter { ShoppingCatalog.find(it.name) == null }.distinctBy { it.name.lowercase() }
    if (eigene.isEmpty()) return
    val name = ShoppingCatalog.OWN_ITEMS
    ganzeZeile("katalog-kopf-eigene") { Klappkopf("🛒 $name", name in aufgeklappt) { onUmschalten(name) } }
    if (name in aufgeklappt) {
        items(eigene, key = { "eigen-${it.name.lowercase()}" }) { e -> EinkaufKachel(e.name, "", e.emoji, stand(e.name, alle), { onTipp(e.name, name) }) }
    }
}

/** Suche: Katalogtreffer und – solange der Text kein Katalogartikel ist (auch in anderer Schreibweise) – „„…“ hinzufügen“. */
internal fun LazyGridScope.suchKacheln(
    text: String,
    alle: List<EinkaufsArtikel>,
    onTreffer: (name: String, abteilung: String) -> Unit,
    onNeu: () -> Unit,
) {
    val q = text.trim()
    val treffer = ShoppingCatalog.search(q)
    items(treffer, key = { (i, s) -> "treffer-${s.name}-${i.name}" }) { (i, s) ->
        EinkaufKachel(i.name, "", i.emoji, stand(i.name, alle), { onTreffer(i.name, s.name) })
    }
    if (ShoppingCatalog.find(q) == null && treffer.none { ShoppingCatalog.sameItem(it.first.name, q) }) {
        item(key = "neu") { EinkaufKachel("„$q“ hinzufügen", "", "➕", Stand.NICHT, onNeu) }
    }
}
