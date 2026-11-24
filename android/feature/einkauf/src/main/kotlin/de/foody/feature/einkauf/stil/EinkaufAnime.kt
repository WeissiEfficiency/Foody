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
import de.foody.feature.einkauf.PillenAussehen
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

/** „Hinzufügen“ in Anime: Überschrift in der Titelschrift, kleine Pillen mit Emoji, gewählt in Gelb. */
internal val animePillen = PillenAussehen(
    ueberschrift = { m -> Text("Hinzufügen", m, style = Anime.titel(18f, 0f), color = FoodyTheme.farben.text) },
    name = { a, e -> "$e " + if (a == ZULETZT) "Zuletzt" else a },
    pille = { t, g, k -> Pille(t, g, k) },
)

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
