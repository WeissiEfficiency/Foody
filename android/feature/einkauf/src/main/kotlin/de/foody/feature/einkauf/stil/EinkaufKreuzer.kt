package de.foody.feature.einkauf.stil

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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import java.util.Locale
import kotlin.math.min

/**
 * „Einkauf“ im Sternenkreuzer (V) in Immersiv (Entwurf `V-Einkauf`): „LADELISTE · …“ über dem „Frachtraum“, rechts
 * „9 / 12 GELADEN“ und „3 KISTEN OFFEN“, daneben „Ladelisten“ (frühere Listen); darunter die Ladeanzeige in Segmenten.
 * Im Frachtraum steht jeder Posten als Kiste mit Code der Abteilung („OBS 01“), Name und Menge; geladene tragen ein
 * schräges Band „GELADEN“ in Amber.
 */
internal fun LazyGridScope.kreuzerKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    ganzeBreite("kreuzer-kopf") { Kopf(ansicht, onListen, menue) }
}

internal fun LazyGridScope.kreuzerPosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val kisten = ansicht.gruppiert().flatMap { it.artikel }
    if (kisten.isEmpty()) return
    ganzeBreite("kreuzer-frachtraum") { Frachtraum(kisten, onUmschalten, onDetails) }
}

/** Code der Abteilung auf der Kiste: die ersten drei Buchstaben, groß („OBS“, „KÜH“). */
internal fun kistenCode(abteilung: String): String =
    abteilung.filter { it.isLetter() }.take(3).uppercase(Locale.GERMAN).ifEmpty { "---" }

/** Höchstens so viele Segmente zeigt die Ladeanzeige; bei mehr Posten steht ein Segment für mehrere. */
private const val SEGMENTE_HOECHSTENS = 24

private val KISTE_OFFEN = listOf(Color(0xFF13203A), Color(0xFF0B1426))
private val KISTE_GELADEN = listOf(Color(0xFF1A1A14), Color(0xFF0E0E0A))
private val KISTE_RAND = Color(0xFF2A3A58)
private val SEGMENT_LEER = Color(0xFF1C2A44)
private val GELADEN_TEXT = Color(0xFF8A8470)
private val MENGE = Color(0xFF8A97AD)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val k = LocalDensity.current.fontScale
    val gesamt = ansicht.gesamt
    val erledigt = ansicht.erledigt
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "LADELISTE · ${(ansicht?.name ?: "Einkauf").uppercase(Locale.GERMAN)}",
                    style = Kreuzer.mono(12.5f, 0.08f), color = f.nebentext,
                )
                // Der Titel wächst mit der Systemschrift nur bis 130 %
                Text(
                    "Frachtraum",
                    Modifier.semantics {
                        heading()
                        contentDescription = "Einkauf"
                    },
                    style = Kreuzer.titel(28f * min(k, 1.3f) / k), color = f.text,
                )
            }
            Row(Modifier.align(Alignment.Bottom), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (gesamt > 0) {
                    Column(
                        Modifier.padding(end = 4.dp).semantics(mergeDescendants = true) {
                            contentDescription = "$erledigt von $gesamt erledigt, ${gesamt - erledigt} offen"
                        },
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text("$erledigt / $gesamt GELADEN", style = Kreuzer.mono(13f), color = holo, textAlign = TextAlign.End)
                        Text(
                            if (gesamt == erledigt) "STARTKLAR" else "${gesamt - erledigt} KISTEN OFFEN",
                            style = Kreuzer.mono(13f), color = f.nebentext, textAlign = TextAlign.End,
                        )
                    }
                }
                KreuzerKnopf("Ladelisten", onListen, voll = false, beschreibung = "Frühere Listen")
                menue()
            }
        }
        if (gesamt > 0) Ladeanzeige(erledigt, gesamt)
    }
}

/** Ein Segment je Posten (höchstens [SEGMENTE_HOECHSTENS]); geladene in Amber. Für TalkBack eine Zeile. */
@Composable
private fun Ladeanzeige(erledigt: Int, gesamt: Int) {
    val amber = kreuzerAmber()
    val anzahl = min(gesamt, SEGMENTE_HOECHSTENS)
    val voll = (erledigt.toFloat() / gesamt * anzahl).toInt()
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Ladung: $erledigt von $gesamt" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(anzahl) { i -> Box(Modifier.weight(1f).height(8.dp).background(if (i < voll) amber else SEGMENT_LEER)) }
    }
}

@Composable
private fun Frachtraum(kisten: List<EinkaufsArtikel>, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val linie = SEGMENT_LEER
    val holo = FoodyTheme.stil.zier
    // Bei sehr großer Schrift zwei Kisten je Reihe, sonst drei wie im Entwurf
    val spalten = if (LocalDensity.current.fontScale > 1.5f) 2 else 3
    Column(
        Modifier.fillMaxWidth().border(1.dp, linie)
            .drawBehind {
                val raster = 40.dp.toPx()
                val farbe = holo.copy(alpha = 0.05f)
                var x = raster
                while (x < size.width) { drawLine(farbe, Offset(x, 0f), Offset(x, size.height), 1f); x += raster }
                var y = raster
                while (y < size.height) { drawLine(farbe, Offset(0f, y), Offset(size.width, y), 1f); y += raster }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var nr = 0
        kisten.chunked(spalten).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { a ->
                    nr++
                    Kiste(a, nr, Modifier.weight(1f), onUmschalten, onDetails)
                }
                repeat(spalten - reihe.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Kiste mit abgeschnittener Ecke oben rechts. */
private val KISTE = GenericShape { g, _ ->
    val s = min(g.width, g.height) * 0.12f
    moveTo(0f, 0f)
    lineTo(g.width - s, 0f)
    lineTo(g.width, s)
    lineTo(g.width, g.height)
    lineTo(0f, g.height)
    close()
}

@Composable
private fun Kiste(a: EinkaufsArtikel, nr: Int, modifier: Modifier, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val code = kistenCode(a.abteilung)
    val rand = when {
        a.gekauft -> amber.copy(alpha = 0.5f)
        code == "KÜH" -> holo.copy(alpha = 0.6f)
        else -> KISTE_RAND
    }
    Box(
        modifier.heightIn(min = 92.dp).clip(KISTE)
            .background(Brush.linearGradient(if (a.gekauft) KISTE_GELADEN else KISTE_OFFEN))
            .border(1.dp, rand, KISTE)
            .posten(a, onUmschalten, onDetails),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(code, Modifier.weight(1f), style = Kreuzer.mono(10.5f), color = if (a.gekauft) GELADEN_TEXT else holo)
                Text(nr.toString().padStart(2, '0'), style = Kreuzer.mono(10.5f), color = if (a.gekauft) GELADEN_TEXT else holo)
            }
            Text(a.name, style = Kreuzer.titel(14.5f, FontWeight.Bold, 0f), color = if (a.gekauft) GELADEN_TEXT else f.text)
            a.menge?.let { Text(it, style = Kreuzer.mono(12f, 0f), color = if (a.gekauft) GELADEN_TEXT else MENGE) }
        }
        if (a.gekauft) {
            Text(
                "GELADEN",
                Modifier.align(Alignment.Center).clearAndSetSemantics { }.ueberstehen(40.dp).rotate(-18f).background(amber).padding(vertical = 2.dp),
                style = Kreuzer.mono(12f / LocalDensity.current.fontScale, 0.2f), color = Kreuzer.aufAmber, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Misst den Inhalt um [extra] breiter als den Platz und legt ihn mittig, damit ein schräges Band bis über die Ränder reicht. */
private fun Modifier.ueberstehen(extra: Dp): Modifier = layout { messbar, vorgabe ->
    val e = extra.roundToPx()
    val p = messbar.measure(vorgabe.copy(minWidth = vorgabe.maxWidth + e, maxWidth = vorgabe.maxWidth + e))
    layout(vorgabe.maxWidth, p.height) { p.place(-e / 2, 0) }
}
