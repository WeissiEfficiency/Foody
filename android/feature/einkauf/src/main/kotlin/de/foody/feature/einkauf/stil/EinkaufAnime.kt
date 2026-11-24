package de.foody.feature.einkauf.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.data.einkauf.ZuletztArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.animeSzenenTitel
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse
import de.foody.domain.ShoppingCatalog
import de.foody.feature.einkauf.EinkaufKachel
import de.foody.feature.einkauf.Stand
import de.foody.feature.einkauf.ZULETZT
import de.foody.feature.einkauf.stand

/**
 * „Einkauf“ im Anime-Stil (R) in Immersiv, ruhig und nur senkrecht gescrollt: kleiner Kopf mit „QUEST!“, Zähler
 * „3/12“ und rundem Knopf „Listen“ (Klemmbrett). Darunter die Posten als Kacheln, je Abteilung mit einem bunten
 * Abzeichen; Antippen hakt ab und wieder auf, Langdruck öffnet die Details. Zum Hinzufügen stehen die Abteilungen
 * (Zuletzt, Katalog, Eigene Artikel) als Pillen, die umbrechen; darunter nur die Kacheln der gewählten.
 */
internal fun LazyGridScope.animeKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit) {
    ganzeBreite("anime-kopf") { Kopf(ansicht, onListen) }
}

internal fun LazyGridScope.animePosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    ansicht.gruppiert().forEach { g ->
        // Farbe nach der Abteilung, nicht nach der Reihenfolge: Abhaken verschiebt Abteilungen, die Farbe bleibt
        val farbe = ABZEICHEN[ShoppingCatalog.sectionOrder.indexOf(g.name).coerceAtLeast(0) % ABZEICHEN.size]
        ganzeBreite("anime-${g.name}") { Abzeichen(g.name, farbe) }
        items(g.artikel, key = { "anime-posten-${it.id}" }) { a ->
            EinkaufKachel(
                a.name, a.menge.orEmpty(), a.emoji, if (a.gekauft) Stand.GEKAUFT else Stand.OFFEN, { onUmschalten(a.id, !a.gekauft) },
                untertitel = a.notiz, onLangdruck = { onDetails(a.id) }, aufListe = true,
            )
        }
    }
}

/**
 * „Hinzufügen“: die Abteilungen als Pillen, darunter die Kacheln der Abteilung [gewaehlt] (`null` oder nicht mehr da =
 * Zuletzt, sonst die erste). Antippen nach der Kachelregel; aus „Zuletzt“ kommt der Artikel wieder auf die Liste.
 */
internal fun LazyGridScope.animeHinzufuegen(
    zuletzt: List<ZuletztArtikel>,
    alle: List<EinkaufsArtikel>,
    gewaehlt: String?,
    onWaehlen: (String) -> Unit,
    onZuletzt: (ZuletztArtikel) -> Unit,
    onTipp: (name: String, abteilung: String) -> Unit,
) {
    val eigene = alle.filter { ShoppingCatalog.find(it.name) == null }.distinctBy { it.name.lowercase() }
    val eigenName = ShoppingCatalog.OWN_ITEMS
    val pillen = buildList {
        if (zuletzt.isNotEmpty()) add(ZULETZT to "🕘 Zuletzt")
        ShoppingCatalog.sections.forEach { add(it.name to "${it.emoji} ${it.name}") }
        if (eigene.isNotEmpty()) add(eigenName to "🛒 $eigenName")
    }
    val aktiv = gewaehlt?.takeIf { g -> pillen.any { it.first == g } } ?: pillen.first().first
    ganzeBreite("anime-hinzufuegen") { Pillen(pillen, aktiv, onWaehlen) }
    when (aktiv) {
        ZULETZT -> items(zuletzt, key = { "anime-zuletzt-${it.name}" }) { z ->
            EinkaufKachel(z.name, "", z.emoji, stand(z.name, alle), { onZuletzt(z) })
        }
        eigenName -> items(eigene, key = { "anime-eigen-${it.name.lowercase()}" }) { e ->
            EinkaufKachel(e.name, "", e.emoji, stand(e.name, alle), { onTipp(e.name, eigenName) })
        }
        else -> {
            val s = ShoppingCatalog.sections.first { it.name == aktiv }
            items(s.items, key = { "anime-katalog-${s.name}-${it.name}" }) { k ->
                EinkaufKachel(k.name, "", k.emoji, stand(k.name, alle), { onTipp(k.name, s.name) })
            }
        }
    }
}

/** Farben der Abteilungs-Abzeichen wie im Entwurf (Grün, Türkis, Orange, Lila, Rosa). */
private val ABZEICHEN = listOf(Anime.mint, Anime.tuerkis, Anime.orange, Anime.lila, Anime.rosa)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit) {
    val f = FoodyTheme.farben
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Der Titel wächst mit der Systemschrift nur bis 130 %
        Text(
            szenenWort("QUEST!", "Einkauf"),
            Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).semantics {
                heading()
                contentDescription = "Einkauf"
            },
            style = animeSzenenTitel(titelGroesse(28f)),
            color = f.text,
        )
        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val gesamt = ansicht.gesamt
            val erledigt = ansicht.erledigt
            if (gesamt > 0) {
                Text(
                    "$erledigt/$gesamt",
                    Modifier.background(Color.White, Anime.pille).border(2.dp, f.text, Anime.pille).padding(horizontal = 10.dp, vertical = 4.dp)
                        .clearAndSetSemantics { contentDescription = "$erledigt von $gesamt erledigt" },
                    style = Anime.text(14f, FontWeight.ExtraBold),
                    color = f.text,
                )
            }
            // Rund wie das Zahnrad auf „Heute“, mit Klemmbrett
            RunderKnopf(Icons.AutoMirrored.Outlined.Assignment, "Einkaufslisten", onListen)
        }
    }
}

/** Name einer Abteilung der Liste im bunten Abzeichen über ihren Kacheln. */
@Composable
private fun Abzeichen(text: String, farbe: Color) {
    val f = FoodyTheme.farben
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text,
            Modifier.background(farbe, Anime.pille).border(2.dp, f.text, Anime.pille).padding(horizontal = 10.dp, vertical = 2.dp)
                .semantics { heading() },
            style = Anime.text(13f, FontWeight.ExtraBold),
            color = f.text,
        )
    }
}

/** „Hinzufügen“ und die Abteilungen als Pillen, die umbrechen; [aktiv] ist gewählt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pillen(pillen: List<Pair<String, String>>, aktiv: String, onWaehlen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Hinzufügen", Modifier.semantics { heading() }, style = Anime.titel(18f, 0f), color = FoodyTheme.farben.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pillen.forEach { (name, text) -> Pille(text, name == aktiv) { onWaehlen(name) } }
        }
    }
}

/** Eine Abteilung zum Wählen: kleine Pille, gewählt in Gelb; die Tippfläche ist trotzdem 48 dp hoch. */
@Composable
private fun Pille(text: String, gewaehlt: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    Box(Modifier.heightIn(min = 48.dp).selectable(gewaehlt, role = Role.Tab, onClick = onKlick), contentAlignment = Alignment.Center) {
        Text(
            text,
            Modifier.background(if (gewaehlt) Anime.gelb else Color.White, Anime.pille).border(2.dp, f.text, Anime.pille)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = Anime.text(13f, FontWeight.ExtraBold),
            color = f.text,
        )
    }
}
