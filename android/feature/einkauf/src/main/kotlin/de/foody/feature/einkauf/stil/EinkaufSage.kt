package de.foody.feature.einkauf.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageKopf
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.SageRaute
import de.foody.core.designsystem.stil.svgPfad
import de.foody.core.designsystem.stil.szenenWort
import de.foody.feature.einkauf.PillenAussehen
import de.foody.feature.einkauf.ZULETZT
import java.util.Locale

/**
 * „Einkauf“ der Düsteren Sage in Immersiv (Entwurf `Q-Einkauf`): Kopf „Der Händler“ mit erledigt/gesamt, die Szene
 * im Laden mit dem Spruch des Händlers und die Posten als Felder mit Raute. Antippen hakt ab (und wieder auf),
 * Langdruck öffnet die Details – wie die Kacheln der normalen Ansicht. Eingabe, „Zuletzt“ und Katalog bleiben.
 */
internal fun LazyGridScope.haendlerKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit) {
    item(key = "haendler-kopf", span = { GridItemSpan(maxLineSpan) }) {
        val gesamt = ansicht?.alle?.size ?: 0
        val offen = ansicht?.anzahlOffen ?: 0
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SageKopf(
                unter = (ansicht?.name ?: "Einkaufsliste").uppercase(Locale.GERMAN),
                titel = szenenWort("DER HÄNDLER", "EINKAUF"),
                beschreibung = "Einkauf",
            ) {
                if (gesamt > 0) {
                    Text(
                        "${gesamt - offen} / $gesamt", Modifier.padding(bottom = 10.dp),
                        style = Sage.titel(15f, 0.04f), color = FoodyTheme.farben.text,
                    )
                }
                SageNebenknopf("LISTEN", "Einkaufslisten", onListen, akzent = true)
            }
            // Ruhig: ohne das Ruinentor und den Spruch des Händlers (Entwurf `Q-Einkauf`, Ruhig)
            if (FoodyTheme.stil.inszeniert) HaendlerRuinentor(offen)
        }
    }
}

/**
 * Die offenen Posten in Laufreihenfolge, danach die schon gekauften (durchgestrichen). [inszeniert]: als Papierrolle
 * ([HaendlerRolle]) in einem Stück; Ruhig: Felder, je Reihe zwei wie im Entwurf, unabhängig von den Spalten des Katalogs.
 */
internal fun LazyGridScope.haendlerPosten(
    ansicht: EinkaufsAnsicht?,
    inszeniert: Boolean,
    onUmschalten: (String, Boolean) -> Unit,
    onDetails: (String) -> Unit,
) {
    val offen = ansicht?.offen.orEmpty().flatMap { it.artikel }
    val gekauft = ansicht?.alle.orEmpty().filter { it.gekauft }
    if (inszeniert) {
        if (offen.isEmpty() && gekauft.isEmpty()) return
        item(key = "haendler-rolle", span = { GridItemSpan(maxLineSpan) }) { HaendlerRolle(offen + gekauft, onUmschalten, onDetails) }
        return
    }
    items((offen + gekauft).chunked(2), key = { "posten-${it.first().id}" }, span = { GridItemSpan(maxLineSpan) }) { reihe ->
        // Gleich hohe Felder je Reihe, auch wenn eine Unterzeile zweizeilig wird
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reihe.forEach { a -> Posten(a, Modifier.weight(1f).fillMaxHeight(), onKlick = { onUmschalten(a.id, !a.gekauft) }, onLang = { onDetails(a.id) }) }
            if (reihe.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** „Hinzufügen“ beim Händler: „Die Waren des Händlers“ in Versalien unter einer feinen Kante, die Waren ohne Emoji. */
internal val sagePillen = PillenAussehen(
    ueberschrift = { m ->
        val f = FoodyTheme.farben
        Text(
            "DIE WAREN DES HÄNDLERS",
            m.fillMaxWidth().drawBehind { drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 12.dp, bottom = 4.dp),
            style = Sage.titel(12f, 0.18f), color = f.nebentext,
        )
    },
    name = { a, _ -> if (a == ZULETZT) "Zuletzt gehandelt" else a },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Posten(a: EinkaufsArtikel, modifier: Modifier, onKlick: () -> Unit, onLang: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val zeile = listOfNotNull(a.menge, a.abteilung.uppercase(Locale.GERMAN)).joinToString(" · ")
    Row(
        modifier.heightIn(min = 50.dp)
            .border(1.dp, if (a.gekauft) f.balkenSpur else f.lichtkante)
            .then(if (a.gekauft) Modifier else Modifier.background(Brush.verticalGradient(listOf(glut.copy(alpha = 0.08f), Color.Transparent))))
            .combinedClickable(role = Role.Checkbox, onClick = onKlick, onLongClick = onLang, onLongClickLabel = "Details")
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(a.name, a.menge, a.notiz).joinToString(", ")
                stateDescription = if (a.gekauft) "gekauft" else "auf der Liste"
                onLongClick("Details") { onLang(); true }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { SageRaute(10.dp, gefuellt = a.gekauft) }
        Column(Modifier.weight(1f)) {
            Text(
                a.name, style = Sage.text(16.5f).copy(textDecoration = if (a.gekauft) TextDecoration.LineThrough else null),
                color = if (a.gekauft) f.nebentext else f.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // Zwei Zeilen: „500 G · OBST & GEMÜSE“ passt in Großbuchstaben nicht in ein halbes Handy (Gerätetest 2026-11-16)
            if (zeile.isNotEmpty()) Text(zeile, style = Sage.titel(10.5f, 0.1f), color = f.nebentext, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Was der Händler sagt, je nach Zahl der offenen Posten. */
internal fun haendlerSpruch(offen: Int): String = when (offen) {
    0 -> "Ihr habt alles. Geht in Frieden."
    1 -> "Ein Ding fehlt Euch noch."
    else -> "${ZAHLWORTE.getOrNull(offen) ?: offen.toString()} Dinge fehlen Euch noch."
}

private val ZAHLWORTE = listOf("Keine", "Ein", "Zwei", "Drei", "Vier", "Fünf", "Sechs", "Sieben", "Acht", "Neun", "Zehn", "Elf", "Zwölf")
