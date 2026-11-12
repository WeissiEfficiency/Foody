package de.foody.feature.woche.stil

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import de.foody.feature.woche.wochenTitel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

/**
 * „Woche“ im Sternenkreuzer (V) in Immersiv (Entwurf `V-Woche`): „Flugplan“ mit „6 / 7 SPRÜNGE · KURS PLOTTEN ›“,
 * darunter die Sprungroute: sieben Rauten im Zickzack (vergangene gefüllt in Holo-Blau, heute in Amber und pulsierend,
 * kommende gestrichelt). Darunter die Karte des gewählten Sprungs mit abgeschnittenen Ecken und alle Tage als Liste
 * mit Status („ANGEKOMMEN“, „IM ORBIT“, „ETA 2 TAGE“, „OFFEN“).
 */
@Composable
internal fun WocheKreuzer(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    WocheRahmen(unten) {
        Kopf(w, aktionen.onFuellen)
        Blaettern(w, aktionen, Kreuzer.mono(12.5f))
        if (w.tage.size == 7) Route(w, gewaehlt) { gewaehlt = it }
        w.tage.getOrNull(gewaehlt)?.let { Sprungkarte(it, gewaehlt, it.datum == w.heute, aktionen) }
        Column(Modifier.drawBehind { drawLine(LINIE, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
            w.tage.forEachIndexed { i, t -> Zeile(t, w.heute, an = i == gewaehlt) { gewaehlt = i } }
        }
    }
}

/** Status eines Tages im Flugplan. */
internal fun sprungStatus(t: WochenTag, heute: LocalDate): String {
    val tage = ChronoUnit.DAYS.between(heute, t.datum)
    return when {
        tage == 0L -> "IM ORBIT"
        tage < 0 -> if (t.eintraege.isEmpty()) "KEIN SPRUNG" else "ANGEKOMMEN"
        t.eintraege.isEmpty() -> "OFFEN"
        tage == 1L -> "ETA 1 TAG"
        else -> "ETA $tage TAGE"
    }
}

private val LINIE = Color(0xFF1C2A44)
private val NEBEN = Color(0xFF8A97AD)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten, onFuellen: () -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            w.woche?.let { Text(wochenTitel(it.montag), style = Kreuzer.mono(12.5f, 0.08f), color = f.nebentext) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Flugplan",
                Modifier.semantics {
                    heading()
                    contentDescription = "Woche"
                },
                style = Kreuzer.titel(28f * min(k, 1.3f) / k), color = f.text,
            )
        }
        if (w.tage.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Bottom).heightIn(min = 48.dp).border(1.dp, LINIE)
                    .clickable(role = Role.Button, onClick = onFuellen)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${w.geplant} von ${w.tage.size} Tagen geplant, leere Tage füllen"
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text("${w.geplant} / ${w.tage.size} SPRÜNGE", style = Kreuzer.mono(13f), color = holo)
                Text("KURS PLOTTEN ›", style = Kreuzer.mono(11f), color = f.primaer)
            }
        }
    }
}

@Composable
private fun Route(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val holo = FoodyTheme.stil.zier
    BoxWithConstraints(Modifier.fillMaxWidth().height(128.dp)) {
        val breite = 48.dp
        val schritt = (maxWidth - breite) / 6
        val heute = w.heuteIndex
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val punkte = (0 until 7).map { i -> Offset((schritt * i + breite / 2).toPx(), ((if (i % 2 == 1) 50 else 8) + 22).dp.toPx()) }
            for (i in 0 until 6) {
                val geflogen = heute < 0 || i < heute
                drawLine(
                    holo.copy(alpha = if (geflogen) 0.7f else 0.5f), punkte[i], punkte[i + 1], 1.5.dp.toPx(),
                    pathEffect = if (geflogen) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        w.tage.forEachIndexed { i, t ->
            Raute(
                t, w.heute, an = i == gewaehlt,
                Modifier.offset(schritt * i, (if (i % 2 == 1) 50 else 8).dp).width(breite),
            ) { onWahl(i) }
        }
    }
}

@Composable
private fun Raute(t: WochenTag, heute: LocalDate, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val istHeute = t.datum == heute
    val vorbei = t.datum.isBefore(heute)
    val leer = t.eintraege.isEmpty()
    val k = LocalDensity.current.fontScale
    val puls = if (istHeute && animationenErlaubt()) {
        rememberInfiniteTransition(label = "puls").animateFloat(0f, 1f, infiniteRepeatable(tween(2000)), label = "puls").value
    } else {
        0f
    }
    Column(modifier.tagWahl(t, istHeute, an, onKlick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(44.dp)) {
            val s = 18.dp.toPx()
            if (puls > 0f) drawCircle(amber.copy(alpha = 0.55f * (1f - puls)), s * 0.7f + 8.dp.toPx() * puls)
            rotate(45f) {
                val oben = Offset(center.x - s / 2, center.y - s / 2)
                when {
                    istHeute -> drawRect(amber, oben, Size(s, s))
                    vorbei && !leer -> drawRect(holo, oben, Size(s, s))
                    else -> drawRect(
                        if (leer) NEBEN else holo, oben, Size(s, s),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                    )
                }
                if (an) {
                    val a = 5.dp.toPx()
                    drawRect(amber, Offset(oben.x - a, oben.y - a), Size(s + 2 * a, s + 2 * a), style = Stroke(1.dp.toPx()))
                }
            }
        }
        // Das Kürzel wächst nur bis 130 %, die Route hat feste Maße
        Text(
            wochentagKurz(t.datum).uppercase(Locale.GERMAN),
            style = Kreuzer.mono(12f * min(k, 1.3f) / k), color = if (an) f.text else f.nebentext,
        )
    }
}

@Composable
private fun Sprungkarte(t: WochenTag, nr: Int, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    val amber = kreuzerAmber()
    val form = Kreuzer.ecke(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(amber.copy(alpha = 0.10f), amber.copy(alpha = 0.02f))), form)
            .border(1.dp, amber.copy(alpha = 0.45f), form)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "SPRUNG ${nr + 1} · " + (wochentagLang(t.datum) + if (heute) " · heute" else ", ${t.datum.dayOfMonth}.").uppercase(Locale.GERMAN),
            style = Kreuzer.mono(12f, 0.1f), color = f.primaer,
        )
        if (t.eintraege.isEmpty()) {
            Text("Kein Kurs gesetzt", style = Kreuzer.titel(20f, FontWeight.Bold, 0f), color = f.text)
            Text("Navigationscomputer wartet auf Kurs.", style = Kreuzer.mono(13f, 0f), color = f.nebentext)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(
                    e, { aktionen.onEintrag(e) },
                    oben = Kreuzer.mono(11f), gericht = Kreuzer.titel(20f, FontWeight.Bold, 0f), stand = Kreuzer.mono(13f, 0f),
                )
            }
            tagKcal(t)?.let { Text(it, style = Kreuzer.mono(13f, 0f), color = f.nebentext) }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KreuzerKnopf("+ Kurs setzen", { aktionen.onPlus(t.datum) }, Modifier.weight(1f), beschreibung = "Mahlzeit hinzufügen")
            KreuzerKnopf("Kurs plotten", aktionen.onFuellen, Modifier.weight(1f), voll = false, beschreibung = "Leere Tage füllen")
        }
    }
}

/** Ein Tag der Liste: „Mo 26.“, Gerichte und Status; Antippen wählt ihn. */
@Composable
private fun Zeile(t: WochenTag, heute: LocalDate, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val istHeute = t.datum == heute
    val leer = t.eintraege.isEmpty()
    val k = LocalDensity.current.fontScale
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(if (an) amber.copy(alpha = 0.07f) else Color.Transparent)
            .drawBehind { drawLine(LINIE, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .clickable(role = Role.Tab, onClick = onKlick)
            .semantics(mergeDescendants = true) {
                selected = an
                contentDescription = tagBeschreibung(t, istHeute) + ", " + sprungStatus(t, heute).lowercase(Locale.GERMAN)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${wochentagKurz(t.datum)} ${t.datum.dayOfMonth}.", Modifier.width(52.dp * k),
            style = Kreuzer.mono(13f, 0f), color = if (istHeute) f.primaer else holo,
        )
        Text(
            if (leer) "Kein Kurs gesetzt" else t.eintraege.joinToString(", ") { it.rezeptName },
            Modifier.weight(1f), style = Kreuzer.titel(14.5f, FontWeight.Medium, 0f), color = if (leer) f.nebentext else f.text,
        )
        Text(sprungStatus(t, heute), style = Kreuzer.mono(12f, 0.04f), color = if (istHeute) f.primaer else f.nebentext, textAlign = TextAlign.End)
    }
}
