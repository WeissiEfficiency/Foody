package de.foody.feature.einkauf.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.token.FoodyAbstaende
import kotlin.math.min

/**
 * „Einkauf“ im Bayrischen Stil (T) in Immersiv (Entwurf `T-Einkauf`): oben die weiß-blaue Markise des Standls, darunter
 * das dunkle Schild „'s Standl“ mit „no 3 offn / 9 von 12 im Korb“ und „Zettl“ (frühere Listen). Jede Abteilung ist
 * eine Holzkiste („Vom Gmiasstandl“), darin die Posten zu zweit mit einem Punkt, der gekauft blau wird. TalkBack hört
 * die normalen Wörter.
 */
internal fun LazyGridScope.bayrischKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    ganzeBreite("bayrisch-markise") { Markise(FoodyAbstaende.s4) }
    ganzeBreite("bayrisch-kopf") { Kopf(ansicht, onListen, menue) }
}

internal fun LazyGridScope.bayrischPosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    jeGruppe("bayrisch", ansicht.gruppiert()) { _, g -> Kiste(g, onUmschalten, onDetails) }
}

/** Namen der Abteilungen auf Bairisch; andere bleiben, wie sie sind. */
internal fun bayrischAbteilung(name: String): String = when (name) {
    "Obst & Gemüse" -> "Vom Gmiasstandl"
    "Milch & Käse", "Kühlregal" -> "Aus da Kühlung"
    "Getreideprodukte", "Trockenwaren" -> "Für d'Speis"
    "Brot & Gebäck", "Brot & Backwaren" -> "Vom Bäck"
    "Fleisch & Fisch" -> "Vom Metzger"
    else -> name
}

/** „no 3 offn“ bzw. „nix mehr offn“. */
internal fun bayrischOffen(offen: Int): String = if (offen == 0) "nix mehr offn" else "no $offen offn"

private val HOLZ = Color(0xFFC8955E)
private val HOLZ_FUGE = Color(0xFFB9854F)
private val HOLZ_SCHATTEN = Color(0xFF6B4423)
private val GEKAUFT_GRUND = Color(0xFFF3E9DA)
private val GEKAUFT_TEXT = Color(0xFF5E5040)

/** Markise in Streifen mit Zacken; reicht über den Rand des Rasters ([rand]) bis an den Bildschirmrand. Stumm. */
@Composable
private fun Markise(rand: Dp) {
    val blau = bayrischBlau()
    val tinte = FoodyTheme.farben.text
    Canvas(
        Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics { }.layout { messbar, vorgabe ->
            val extra = rand.roundToPx()
            val p = messbar.measure(vorgabe.copy(minWidth = vorgabe.maxWidth + 2 * extra, maxWidth = vorgabe.maxWidth + 2 * extra))
            layout(vorgabe.maxWidth, p.height) { p.place(-extra, 0) }
        },
    ) {
        val streifen = 26.dp.toPx()
        val hoehe = 56.dp.toPx()
        val zacke = 12.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            val farbe = if (i % 2 == 0) blau else Color.White
            drawRect(farbe, Offset(x, 0f), Size(streifen, hoehe))
            drawCircle(farbe, zacke, Offset(x + streifen / 2f, hoehe))
            x += streifen
            i++
        }
        drawLine(tinte, Offset(0f, hoehe), Offset(size.width, hoehe), 2.dp.toPx())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    val gesamt = ansicht.gesamt
    val erledigt = ansicht.erledigt
    val form = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            Modifier.weight(1f).background(f.text, form).border(2.dp, f.text, form).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "'s Standl",
                Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).semantics {
                    heading()
                    contentDescription = "Einkauf"
                },
                style = Bayrisch.titel(32f * min(k, 1.3f) / k, FontWeight.ExtraBold), color = f.grund,
            )
            if (ansicht != null) {
                Column(
                    Modifier.align(Alignment.CenterVertically).semantics(mergeDescendants = true) {
                        contentDescription = "${gesamt - erledigt} offen, $erledigt von $gesamt erledigt"
                    },
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(bayrischOffen(gesamt - erledigt), style = Bayrisch.titel(15f, FontWeight.ExtraBold), color = f.grund, textAlign = TextAlign.End)
                    Text("$erledigt von $gesamt im Korb", style = Bayrisch.text(13f), color = f.grund, textAlign = TextAlign.End)
                }
            }
        }
        BayrischKnopf("Zettl", onListen, voll = false, beschreibung = "Frühere Listen")
        menue()
    }
}

@Composable
private fun Kiste(g: PostenGruppe, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 3.dp)
            .drawBehind {
                // Schatten unten, dann das Holz mit Fugen
                drawRoundRect(HOLZ_SCHATTEN, Offset(0f, 3.dp.toPx()), size, CornerRadius(6.dp.toPx()))
            }
            .clip(form).background(HOLZ)
            .drawBehind {
                val fuge = 16.dp.toPx()
                var y = fuge - 2.dp.toPx()
                while (y < size.height) {
                    drawRect(HOLZ_FUGE, Offset(0f, y), Size(size.width, 2.dp.toPx()))
                    y += fuge
                }
            }
            .border(2.dp, f.text, form).padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            bayrischAbteilung(g.name),
            Modifier.semantics {
                heading()
                contentDescription = g.name
            },
            style = Bayrisch.titel(15f, FontWeight.ExtraBold), color = f.text,
        )
        g.artikel.chunked(2).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reihe.forEach { Posten(it, Modifier.weight(1f), onUmschalten, onDetails) }
                if (reihe.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Posten(a: EinkaufsArtikel, modifier: Modifier, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val blau = bayrischBlau()
    val form = RoundedCornerShape(6.dp)
    val schrift = if (a.gekauft) GEKAUFT_TEXT else f.text
    Row(
        modifier.heightIn(min = 48.dp).clip(form).background(if (a.gekauft) GEKAUFT_GRUND else Color.White).border(1.5.dp, f.text, form)
            .posten(a, onUmschalten, onDetails).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(18.dp).background(if (a.gekauft) blau else Color.White, CircleShape).border(1.5.dp, f.text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (a.gekauft) Text("✓", style = Bayrisch.text(12f / LocalDensity.current.fontScale, FontWeight.Bold), color = Color.White)
        }
        Text(
            a.name, Modifier.weight(1f),
            style = Bayrisch.text(14.5f, FontWeight.Bold).copy(textDecoration = if (a.gekauft) TextDecoration.LineThrough else null),
            color = schrift,
        )
        a.menge?.let { Text(it, style = Bayrisch.text(12f), color = schrift) }
    }
}
