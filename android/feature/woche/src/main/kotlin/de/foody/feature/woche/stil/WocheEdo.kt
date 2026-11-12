package de.foody.feature.woche.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.edo.edoShu
import java.util.Locale
import kotlin.math.min

/**
 * „Woche“ im Edo-Stil (S) in Immersiv (Entwurf `S-Woche`): „KW 43 · 19.–25. OKTOBER“ über „Woche“, rechts „6 / 7“,
 * darunter die sieben Tage als Votivtafeln (Ema) an zwei Holzleisten – heute im Zinnober, leere Tafeln hell mit „+“,
 * die gewählte hängt tiefer. Darunter das Papier des gewählten Tages mit seinen Gerichten und „+ Mahlzeit“, ganz
 * unten „絵馬 · Leere Tage vorschlagen lassen“.
 */
@Composable
internal fun WocheEdo(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    WocheRahmen(unten, abstand = 14.dp) {
        Kopf(w)
        Blaettern(w, aktionen, Edo.text(14f))
        if (w.tage.size == 7) Tafeln(w, gewaehlt) { gewaehlt = it }
        w.tage.getOrNull(gewaehlt)?.let { Tagespapier(it, it.datum == w.heute, aktionen) }
        FuellenLink(aktionen.onFuellen)
    }
}

/** „絵馬 · Leere Tage vorschlagen lassen“: gestrichelt im Zinnober, wie im Entwurf. */
@Composable
private fun FuellenLink(onKlick: () -> Unit) {
    val farbe = FoodyTheme.farben.primaer
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind {
                drawRect(farbe, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = "Leere Tage füllen" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("絵馬 · Leere Tage vorschlagen lassen", style = Edo.text(14f, FontWeight.Bold), color = farbe, textAlign = TextAlign.Center)
    }
}

private val HOLZ_TAFEL = Color(0xFFD8B98A)
private val TAFEL_LEER = Color(0xFFEFE6D2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(
        Modifier.fillMaxWidth().drawBehind { drawLine(f.text, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            w.woche?.let { Text(kwText(it.montag).uppercase(Locale.GERMAN), style = Edo.kopf(12.5f), color = f.nebentext) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text("Woche", Modifier.semantics { heading() }, style = Edo.titel(34f * min(k, 1.3f) / k), color = f.text)
        }
        if (w.tage.isNotEmpty()) {
            Text(
                "${w.geplant} / ${w.tage.size}",
                Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)
                    .semantics { contentDescription = "${w.geplant} von ${w.tage.size} Tagen geplant" }
                    .background(Edo.papier).border(2.dp, f.text).padding(horizontal = 10.dp, vertical = 3.dp),
                style = Edo.titel(15f), color = f.text,
            )
        }
    }
}

/** Fünfeck der Votivtafel: oben spitz, unten gerade. */
private val EMA = GenericShape { g, _ ->
    val schulter = g.height * 0.2f
    moveTo(g.width / 2f, 0f)
    lineTo(g.width, schulter)
    lineTo(g.width, g.height)
    lineTo(0f, g.height)
    lineTo(0f, schulter)
    close()
}

/** Zwei Reihen Tafeln (Mo–Do, Fr–So), jede an einer Holzleiste. */
@Composable
private fun Tafeln(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0..3, 4..6).forEach { reihe ->
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    // Holzleiste über die ganze Breite und etwas darüber hinaus
                    drawRect(Edo.holz, Offset(-16.dp.toPx(), 0f), Size(size.width + 32.dp.toPx(), 6.dp.toPx()))
                },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                reihe.forEach { i -> Tafel(w.tage[i], heute = i == w.heuteIndex, an = i == gewaehlt) { onWahl(i) } }
            }
        }
    }
}

@Composable
private fun Tafel(t: WochenTag, heute: Boolean, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    val leer = t.eintraege.isEmpty()
    val holz = when {
        heute -> shu
        leer -> TAFEL_LEER
        else -> HOLZ_TAFEL
    }
    val schrift = if (heute) Color.White else f.text
    Column(
        Modifier.width(78.dp).padding(top = if (an) 10.dp else 0.dp).tagWahl(t, heute, an, onKlick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Die rote Schnur, an der die Tafel hängt
        Box(Modifier.width(2.dp).heightIn(min = 14.dp).background(shu))
        Column(
            Modifier.fillMaxWidth().heightIn(min = 104.dp).background(f.text, EMA).padding(2.dp).background(holz, EMA)
                .padding(start = 6.dp, end = 6.dp, top = 28.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        ) {
            Text(wochentagKurz(t.datum), style = Edo.titel(20f), color = schrift)
            Text(
                if (leer) "+" else t.eintraege.last().rezeptName,
                style = Edo.text(10.5f), color = schrift, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Tagespapier(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(4.dp)
    Column(
        Modifier.fillMaxWidth().background(Edo.papier, form).border(2.dp, f.text, form).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (wochentagLang(t.datum) + " · " + if (heute) "heute" else "${t.datum.dayOfMonth}.").uppercase(Locale.GERMAN),
            style = Edo.kopf(12f), color = f.primaer,
        )
        if (t.eintraege.isEmpty()) {
            Text("Noch nichts geplant", style = Edo.titel(20f), color = f.text)
            Text("Eine leere Tafel.", style = Edo.text(14f), color = f.nebentext)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(e, { aktionen.onEintrag(e) }, oben = Edo.kopf(11f), gericht = Edo.titel(20f), stand = Edo.text(14f, FontWeight.Bold))
            }
            tagKcal(t)?.let { Text(it, style = Edo.text(14f), color = f.nebentext) }
        }
        EdoKnopf("+ Mahlzeit", { aktionen.onPlus(t.datum) }, Modifier.fillMaxWidth().padding(top = 4.dp), beschreibung = "Mahlzeit hinzufügen", voll = false)
    }
}
