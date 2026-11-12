package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * „Rezepte“ im Sternenkreuzer (V) in Immersiv (Entwurf `V-Rezepte`): „NAVIGATIONSARCHIV · 6 SYSTEME“ über
 * „Sternkarte“, die Treffer der Seite als Sternsysteme auf einem Raster (Größe nach kcal, Favoriten in Amber, das
 * gewählte mit gestricheltem Ring), darunter seine Karte mit abgeschnittenen Ecken, FLUGZEIT, ENERGIE, BESUCHE und
 * „Kurs setzen“.
 */

private val LINIE = Color(0xFF1C2A44)

/** Lage der Systeme auf der Karte (Anteil an Breite und Höhe) wie im Entwurf. */
private val LAGE = listOf(0.18f to 0.24f, 0.5f to 0.16f, 0.84f to 0.33f, 0.65f to 0.6f, 0.26f to 0.68f, 0.85f to 0.88f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KreuzerKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (n > 0) "NAVIGATIONSARCHIV · $n SYSTEME" else "NAVIGATIONSARCHIV", style = Kreuzer.mono(12f, 0.08f), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text("Sternkarte", Modifier.semantics { heading() }, style = Kreuzer.titel(28f * min(k, 1.3f) / k), color = f.text)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/** Kurzname auf der Karte: das erste Wort, groß („OFENGEMÜSE“). */
internal fun kreuzerKurz(r: RezeptKarte): String =
    r.name.trim().split(' ', '-').firstOrNull { it.isNotBlank() }.orEmpty().uppercase(Locale.GERMAN)

/** Durchmesser des Sterns in dp: 7 + kcal / 70 wie im Entwurf, höchstens 22. */
internal fun sternGroesse(kcal: Int?): Int = (7 + (kcal ?: 300) / 70).coerceIn(7, 22)

/** Die Treffer der Seite als Systeme auf der Sternkarte; jedes ist mindestens 48 dp groß zu treffen. */
@Composable
internal fun KreuzerKarte(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    val holo = FoodyTheme.stil.zier
    val form = Kreuzer.ecke()
    Layout(
        content = {
            liste.forEach { r -> System(r, an = r.id == gewaehlt) { onWahl(r.id) } }
        },
        modifier = Modifier.fillMaxWidth().background(Brush.radialGradient(listOf(Color(0xFF0E1A33), Color(0xFF050913))), form)
            .border(1.dp, holo.copy(alpha = 0.35f), form)
            .drawBehind {
                // Raster der Sektoren
                val strich = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                for (s in 1..3) {
                    val x = size.width * s / 4
                    drawLine(LINIE, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), pathEffect = strich)
                    val y = size.height * s / 4
                    drawLine(LINIE, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = strich)
                }
            }
            .padding(8.dp),
    ) { messbar, c ->
        val breite = c.maxWidth
        val hoehe = 230.dp.roundToPx()
        val teile = messbar.map { it.measure(Constraints(maxWidth = breite)) }
        val lagen = teile.mapIndexed { i, p ->
            val (fx, fy) = LAGE[i % LAGE.size]
            val x = (fx * breite - p.width / 2f).roundToInt().coerceIn(0, (breite - p.width).coerceAtLeast(0))
            val y = (fy * hoehe - 24.dp.toPx()).roundToInt().coerceAtLeast(0)
            x to y
        }
        val unten = maxOf(hoehe, teile.indices.maxOfOrNull { lagen[it].second + teile[it].height } ?: 0)
        layout(breite, unten) { teile.forEachIndexed { i, p -> p.place(lagen[i].first, lagen[i].second) } }
    }
}

@Composable
private fun System(r: RezeptKarte, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val stern = if (r.favorit) amber else holo
    val groesse = sternGroesse(r.kcalJePortion)
    Column(Modifier.rezeptWahl(r, an, onKlick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(48.dp).clearAndSetSemantics { }) {
            val mitte = Offset(size.width / 2, size.height / 2)
            val radius = groesse.dp.toPx() / 2
            drawCircle(Brush.radialGradient(listOf(stern.copy(alpha = 0.45f), Color.Transparent), mitte, radius * 2.2f), radius * 2.2f, mitte)
            drawCircle(stern, radius, mitte)
            if (an) {
                drawCircle(
                    amber, radius + 8.dp.toPx(), mitte,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
        Text(kreuzerKurz(r), style = Kreuzer.mono(10f), color = if (an) f.text else f.nebentext, textAlign = TextAlign.Center)
    }
}

/** Die Karte des gewählten Systems mit abgeschnittenen Ecken und „Kurs setzen“. */
@Composable
internal fun KreuzerDaten(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val form = Kreuzer.ecke()
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(holo.copy(alpha = 0.08f), holo.copy(alpha = 0.01f))), form)
            .border(1.dp, holo.copy(alpha = 0.35f), form).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            listOfNotNull("SYSTEM", r.tags.firstOrNull()?.uppercase(Locale.GERMAN), if (r.favorit) "FAVORIT" else null).joinToString(" · "),
            style = Kreuzer.mono(11.5f, 0.1f), color = f.primaer,
        )
        Text(r.name, style = Kreuzer.titel(22f), color = f.text)
        Row(
            Modifier.fillMaxWidth().drawBehind {
                drawLine(LINIE, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                drawLine(LINIE, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Wert("FLUGZEIT", r.zeitMinuten?.let { "$it MIN" } ?: "–", Modifier.weight(1f))
            Wert("ENERGIE", r.kcalJePortion?.let { "$it KCAL" } ?: "–", Modifier.weight(1f))
            Wert("BESUCHE", "${r.gekocht}×", Modifier.weight(1f))
        }
        zutatenSatz(r)?.let { Text(it, style = Kreuzer.titel(15f, androidx.compose.ui.text.font.FontWeight.Normal, 0f), color = f.nebentext) }
        KreuzerKnopf("Kurs setzen", { onOeffnen(r.id) }, Modifier.fillMaxWidth().padding(top = 4.dp), beschreibung = "Öffnen: ${r.name}")
    }
}

@Composable
private fun Wert(name: String, wert: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(name, style = Kreuzer.mono(10f, 0.12f), color = FoodyTheme.farben.nebentext)
        Text(wert, style = Kreuzer.mono(14f), color = FoodyTheme.stil.zier)
    }
}
