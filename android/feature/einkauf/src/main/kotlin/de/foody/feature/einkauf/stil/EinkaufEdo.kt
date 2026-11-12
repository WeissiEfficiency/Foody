package de.foody.feature.einkauf.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.edo.edoShu
import kotlin.math.min

/**
 * „Einkauf“ im Edo-Stil (S) in Immersiv (Entwurf `S-Einkauf`): „3 OFFEN · 9 ERLEDIGT“ über dem Titel, rechts ein
 * Tuschering für den Fortschritt, darunter eine Tuschelinie. Die Abteilungen tragen Namen wie auf dem Markt („Vom
 * Markt“), die Posten stehen als Zeilen auf dem Papier: offen ein leerer, gestrichelter Kreis, gekauft ein Ensō im
 * Zinnober und ein Pinselstrich durch den Namen.
 */
internal fun LazyGridScope.edoKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    ganzeBreite("edo-kopf") { Kopf(ansicht, onListen, menue) }
}

internal fun LazyGridScope.edoPosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    jeGruppe("edo", ansicht.gruppiert()) { _, g -> Gruppe(g, onUmschalten, onDetails) }
}

/** Namen der Abteilungen wie auf dem Markt; andere bleiben, wie sie sind. */
internal fun edoAbteilung(name: String): String = when (name) {
    "Obst & Gemüse" -> "Vom Markt"
    "Milch & Käse", "Kühlregal" -> "Aus der Kühlung"
    "Getreideprodukte", "Trockenwaren" -> "Für die Vorratskammer"
    "Brot & Gebäck", "Brot & Backwaren" -> "Vom Bäcker"
    "Fleisch & Fisch" -> "Vom Fischmarkt"
    "Zutaten & Gewürze" -> "Aus dem Gewürzladen"
    else -> name
}

/** Linie zwischen den Zeilen (Papierfaser). */
private val ZEILENLINIE = Color(0xFFD8CBAE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    val gesamt = ansicht.gesamt
    val erledigt = ansicht.erledigt
    FlowRow(
        Modifier.fillMaxWidth().drawBehind { drawLine(f.text, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (ansicht != null) Text("${gesamt - erledigt} OFFEN · $erledigt ERLEDIGT", style = Edo.kopf(12.5f), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Einkauf", Modifier.semantics { heading() },
                style = Edo.titel(34f * min(k, 1.3f) / k), color = f.text,
            )
        }
        Row(Modifier.align(Alignment.Bottom), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (gesamt > 0) Ring(erledigt, gesamt)
            EdoKnopf("Listen", onListen, voll = false, beschreibung = "Frühere Listen")
            menue()
        }
    }
}

/** Tuschering: Spur in Papierlinie, darauf der erledigte Teil in Tusche. */
@Composable
private fun Ring(erledigt: Int, gesamt: Int) {
    val f = FoodyTheme.farben
    Canvas(Modifier.size(54.dp).clearAndSetSemantics { contentDescription = "$erledigt von $gesamt erledigt" }) {
        val breite = 6.dp.toPx()
        val r = size.minDimension / 2f - breite
        val oben = Offset(size.width / 2f - r, size.height / 2f - r)
        val groesse = Size(r * 2, r * 2)
        drawArc(Edo.papierLinie, 0f, 360f, false, oben, groesse, style = Stroke(breite))
        if (erledigt > 0) drawArc(f.text, -80f, 360f * erledigt / gesamt, false, oben, groesse, style = Stroke(breite, cap = StrokeCap.Round))
    }
}

@Composable
private fun Gruppe(g: PostenGruppe, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            edoAbteilung(g.name), Modifier.padding(top = 8.dp).semantics { heading() },
            style = Edo.titel(17f), color = edoShu(),
        )
        g.artikel.forEach { Zeile(it, onUmschalten, onDetails) }
    }
}

@Composable
private fun Zeile(a: EinkaufsArtikel, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind { drawLine(ZEILENLINIE, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .posten(a, onUmschalten, onDetails).padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Enso(a.gekauft, shu)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                a.name,
                Modifier.drawWithContent {
                    drawContent()
                    if (a.gekauft) pinselstrich(shu)
                },
                style = Edo.text(15.5f), color = if (a.gekauft) f.nebentext else f.text,
            )
        }
        a.menge?.let { Text(it, style = Edo.text(13f, FontWeight.Normal), color = f.nebentext) }
    }
}

/** Gekauft: Ensō im Zinnober, offen: leerer, gestrichelter Kreis. */
@Composable
private fun Enso(gekauft: Boolean, shu: Color) {
    Canvas(Modifier.size(26.dp)) {
        val s = size.width / 26f
        if (gekauft) {
            val pfad = PathParser().parsePathString("M21 8c-3-5-12-5-16 1-3 6 1 13 8 13 6 0 10-5 9-10").toPath()
            scale(s, Offset.Zero) {
                drawPath(pfad, shu, style = Stroke(3.2f, cap = StrokeCap.Round))
            }
        } else {
            drawCircle(
                Color(0xFF9A8A6C), 10f * s, center,
                style = Stroke(1.2f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * s, 3f * s))),
            )
        }
    }
}

/** Pinselstrich in Zinnober quer durch den Namen, leicht ansteigend wie im Entwurf. */
private fun DrawScope.pinselstrich(farbe: Color) {
    val y = size.height / 2f
    val pfad = Path().apply {
        moveTo(-4.dp.toPx(), y + 2.dp.toPx())
        cubicTo(size.width * 0.2f, y - 1.dp.toPx(), size.width * 0.5f, y - 2.dp.toPx(), size.width + 4.dp.toPx(), y)
    }
    drawPath(pfad, farbe.copy(alpha = 0.85f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
}
