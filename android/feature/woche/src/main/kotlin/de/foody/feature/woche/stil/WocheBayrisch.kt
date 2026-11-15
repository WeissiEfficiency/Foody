package de.foody.feature.woche.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.min

/**
 * „Woche“ im Bayrischen Stil (T) in Immersiv (Entwurf `T-Woche`): „KW 43 · 19.–25. Oktober“ über „Wochn“, rechts
 * „6 von 7 · plana ›“ (leere Tage füllen). Darunter eine Wimpelkette mit den sieben Tagen (abwechselnd blau und weiß,
 * heute rot, leere hell, der gewählte länger), die Karte des Tages mit doppeltem Rahmen und unten alle Tage als
 * Liste. TalkBack hört die normalen Wörter.
 */
@Composable
internal fun WocheBayrisch(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    WocheRahmen(unten) {
        Kopf(w, aktionen.onFuellen)
        Blaettern(w, aktionen, Bayrisch.text(14.5f), heuteText = "Heid")
        if (w.tage.size == 7) Wimpelkette(w, gewaehlt) { gewaehlt = it }
        w.tage.getOrNull(gewaehlt)?.let { Tageskarte(it, it.datum == w.heute, aktionen) }
        Column {
            w.tage.forEachIndexed { i, t -> Zeile(t, heute = i == w.heuteIndex, an = i == gewaehlt) { gewaehlt = i } }
        }
    }
}

/** Kopf der Tageskarte: „SAMSTAG · HEID“, Sonntag „SUNDA, 25.“, sonst „MONTAG, 19.“. */
internal fun bayrischTagTitel(t: WochenTag, heute: Boolean): String = when {
    heute -> "${wochentagLang(t.datum)} · heid"
    t.datum.dayOfWeek == DayOfWeek.SUNDAY -> "Sunda, ${t.datum.dayOfMonth}."
    else -> "${wochentagLang(t.datum)}, ${t.datum.dayOfMonth}."
}.uppercase(Locale.GERMAN)

/** Wie tief jeder Wimpel an der Kette hängt (dp), Montag bis Sonntag. */
private val DURCHHANG = listOf(6, 14, 19, 21, 19, 14, 6)
private val ROT = Color(0xFFB8322A)
private val CREME = Color(0xFFFBF6EA)
private val LINIE = Color(0xFFCDBB9C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten, onFuellen: () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            w.woche?.let { Text(kwText(it.montag), style = Bayrisch.titel(15f), color = f.primaer) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Wochn",
                Modifier.semantics {
                    heading()
                    contentDescription = "Woche"
                },
                style = Bayrisch.fraktur(50f * min(k, 1.3f) / k), color = f.text,
            )
        }
        if (w.tage.isNotEmpty()) {
            val form = RoundedCornerShape(10.dp)
            Column(
                Modifier.align(Alignment.Bottom).padding(bottom = 6.dp).heightIn(min = 48.dp)
                    .background(Color.White, form).border(2.dp, f.text, form)
                    .clickable(role = Role.Button, onClick = onFuellen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${w.geplant} von ${w.tage.size} Tagen geplant, leere Tage füllen"
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${w.geplant} von ${w.tage.size}", style = Bayrisch.titel(15f, FontWeight.ExtraBold), color = f.text)
                Text("plana ›", style = Bayrisch.titel(12f), color = f.primaer)
            }
        }
    }
}

/** Dreieck des Wimpels, Spitze unten. */
private val WIMPEL = GenericShape { g, _ ->
    moveTo(0f, 0f)
    lineTo(g.width, 0f)
    lineTo(g.width / 2f, g.height)
    close()
}

@Composable
private fun Wimpelkette(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val f = FoodyTheme.farben
    BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp)) {
        val breite = 48.dp
        val schritt = (maxWidth - breite) / 6
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val y = 6.dp.toPx()
            val seil = Path().apply {
                moveTo(0f, y)
                quadraticTo(size.width / 2f, 50.dp.toPx(), size.width, y)
            }
            drawPath(seil, f.text, style = Stroke(2.dp.toPx()))
            drawCircle(f.text, 4.dp.toPx(), Offset(3.dp.toPx(), y))
            drawCircle(f.text, 4.dp.toPx(), Offset(size.width - 3.dp.toPx(), y))
        }
        w.tage.forEachIndexed { i, t ->
            val an = i == gewaehlt
            Wimpel(
                t, i, heute = i == w.heuteIndex, an = an,
                Modifier.offset(schritt * i, (DURCHHANG[i] + if (an) 4 else 0).dp).size(breite, if (an) 78.dp else 66.dp),
            ) { onWahl(i) }
        }
    }
}

@Composable
private fun Wimpel(t: WochenTag, i: Int, heute: Boolean, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val leer = t.eintraege.isEmpty()
    val stoff = when {
        heute -> ROT
        leer -> CREME
        i % 2 == 0 -> bayrischBlau()
        else -> Color.White
    }
    val schrift = if (heute || (!leer && i % 2 == 0)) Color.White else f.text
    val k = LocalDensity.current.fontScale
    Box(
        modifier.tagWahl(t, heute, an, onKlick).background(f.text, WIMPEL).padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 3.dp)
            .background(stoff, WIMPEL),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Das Kürzel wächst nur bis 130 %, das Dreieck hat feste Maße
        Text(
            wochentagKurz(t.datum), Modifier.padding(top = 6.dp),
            style = Bayrisch.titel(15f * min(k, 1.3f) / k, FontWeight.ExtraBold), color = schrift,
        )
    }
}

@Composable
private fun Tageskarte(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    Column(
        Modifier.fillMaxWidth().border(1.dp, f.text, RoundedCornerShape(17.dp)).padding(3.dp)
            .background(Color.White, RoundedCornerShape(14.dp)).border(2.dp, f.text, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(bayrischTagTitel(t, heute), Modifier.semantics { contentDescription = tagLangText(t.datum) + if (heute) ", heute" else "" },
            style = Bayrisch.titel(14f), color = f.primaer)
        if (t.eintraege.isEmpty()) {
            Text("no frei", style = Bayrisch.titel(20f, FontWeight.ExtraBold), color = f.text)
            Text("Noch nix gplant.", style = Bayrisch.text(14f), color = f.nebentext)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(
                    e, { aktionen.onEintrag(e) },
                    oben = Bayrisch.text(12.5f, FontWeight.Bold), gericht = Bayrisch.titel(20f, FontWeight.ExtraBold), stand = Bayrisch.text(14f),
                )
            }
            tagKcal(t)?.let { Text(it, style = Bayrisch.text(14f), color = f.nebentext) }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BayrischKnopf("+ Essn planen", { aktionen.onPlus(t.datum) }, Modifier.weight(1f), voll = false, beschreibung = "Mahlzeit hinzufügen")
            BayrischKnopf("Leere Tag füllen", aktionen.onFuellen, Modifier.weight(1f), voll = false, beschreibung = "Leere Tage füllen")
        }
    }
}

/** Ein Tag der Liste: „Mo 26.“ (heute „heid“), Gerichte, kcal; Antippen wählt ihn. */
@Composable
private fun Zeile(t: WochenTag, heute: Boolean, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val leer = t.eintraege.isEmpty()
    val k = LocalDensity.current.fontScale
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(if (an) bayrischBlau().copy(alpha = 0.08f) else Color.Transparent)
            .drawBehind {
                drawLine(
                    LINIE, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            .clickable(role = Role.Tab, onClick = onKlick)
            .semantics(mergeDescendants = true) {
                selected = an
                contentDescription = tagBeschreibung(t, heute)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (heute) "heid" else "${wochentagKurz(t.datum)} ${t.datum.dayOfMonth}.",
            Modifier.width(52.dp * k), style = Bayrisch.titel(15f, FontWeight.ExtraBold), color = f.primaer,
        )
        Text(
            if (leer) "no frei" else t.eintraege.joinToString(", ") { it.rezeptName },
            Modifier.weight(1f), style = Bayrisch.text(15f), color = if (leer) f.nebentext else f.text,
        )
        Text(
            t.naehrwerte?.takeIf { !leer }?.let { ganzzahlDeutsch(it.kcal) } ?: "–",
            style = Bayrisch.text(13.5f), color = f.nebentext, textAlign = TextAlign.End,
        )
    }
}
