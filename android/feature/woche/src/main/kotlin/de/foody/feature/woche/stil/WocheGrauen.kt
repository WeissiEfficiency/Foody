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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.domain.Mahlzeit
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * „Woche“ im Kosmischen Grauen (U) in Immersiv (Entwurf `U-Woche`): „Mondphasen“ mit „6 / 7 · planen ›“, darunter
 * die sieben Tage als Monde auf einem Bogen – je mehr Mahlzeiten geplant sind, desto voller der Mond, leere Tage sind
 * Neumond mit gestricheltem Rand, heute leuchtet. Darunter die Tafel des gewählten Tages mit einer Notiz und ganz
 * unten „Die dunklen Nächte füllen lassen“.
 */
@Composable
internal fun WocheGrauen(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    WocheRahmen(unten) {
        Kopf(w, aktionen.onFuellen)
        Blaettern(w, aktionen, Grauen.maschine(13f))
        if (w.tage.size == 7) Mondbogen(w, gewaehlt) { gewaehlt = it }
        w.tage.getOrNull(gewaehlt)?.let { Tafel(it, it.datum == w.heute, aktionen) }
        Naechte(aktionen.onFuellen)
    }
}

/** Wie voll der Mond eines Tages ist: Anteil der vier Mahlzeiten, die geplant sind (0 = Neumond, 1 = Vollmond). */
internal fun mondAnteil(t: WochenTag): Float =
    min(t.eintraege.mapNotNull { Mahlzeit.ausText(it.mahlzeit) }.toSet().size.coerceAtLeast(if (t.eintraege.isEmpty()) 0 else 1), 4) / 4f

/** Die Notiz unter dem Tag. */
internal fun mondNotiz(t: WochenTag, heute: Boolean): String = when {
    t.eintraege.isEmpty() -> "Neumond. Nichts ist geplant."
    heute -> "Heute Nacht steht er am Himmel."
    t.eintraege.all { it.gekocht } -> "Verzehrt. Der Mond nimmt ab."
    mondAnteil(t) >= 1f -> "Vollmond. Jede Mahlzeit hat ihren Platz."
    else -> "Noch liegt der Tag im Halbdunkel."
}

private val NACHT = Color(0xFF070B0A)
private val MOND = Color(0xFFD9DEC8)
private val MOND_HEUTE = Color(0xFFF2F6E8)
private val LINIE = Color(0xFF24352E)
private val TAFEL = listOf(Color(0xFF121B18), Color(0xFF0C1210))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten, onFuellen: () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            w.woche?.let { Text(kwText(it.montag), style = Grauen.maschine(12.5f), color = f.nebentext) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Mondphasen",
                Modifier.semantics {
                    heading()
                    contentDescription = "Woche"
                },
                style = Grauen.titel(34f * min(k, 1.3f) / k, 0.02f), color = f.text,
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${w.geplant} / ${w.tage.size}", style = Grauen.maschine(14f), color = f.text)
                Text("planen ›", style = Grauen.maschine(11f), color = f.primaer)
            }
        }
    }
}

@Composable
private fun Mondbogen(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val glut = FoodyTheme.stil.zier
    BoxWithConstraints(Modifier.fillMaxWidth().height(210.dp).clipToBounds()) {
        val breite = 48.dp
        val schritt = (maxWidth - breite) / 6
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val sx = size.width / 354f
            val sy = size.height / 210f
            val bogen = Path().apply {
                moveTo(42f * sx, 142f * sy)
                quadraticTo(177f * sx, -40f * sy, 312f * sx, 142f * sy)
            }
            val strich = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
            drawPath(bogen, glut.copy(alpha = 0.25f), style = Stroke(1.dp.toPx(), pathEffect = strich))
            listOf(20f to 30f, 300f to 20f, 170f to 190f, 90f to 180f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.45f), 0.8.dp.toPx(), Offset(x * sx, y * sy))
            }
        }
        w.tage.forEachIndexed { i, t ->
            val hoehe = (118 - sin(PI * i / 6) * 96).dp
            Mond(t, heute = i == w.heuteIndex, an = i == gewaehlt, Modifier.offset(schritt * i, hoehe).width(breite)) { onWahl(i) }
        }
    }
}

@Composable
private fun Mond(t: WochenTag, heute: Boolean, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val leer = t.eintraege.isEmpty()
    val anteil = mondAnteil(t)
    val k = LocalDensity.current.fontScale
    Column(modifier.tagWahl(t, heute, an, onKlick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(44.dp)) {
            val r = 18.dp.toPx()
            if (heute || an) {
                drawCircle(Brush.radialGradient(listOf(glut.copy(alpha = if (heute) 0.45f else 0.3f), Color.Transparent), center, r * 1.4f), r * 1.4f)
            }
            drawCircle(if (heute) MOND_HEUTE else MOND, r)
            if (anteil < 1f) {
                // Der Schatten rückt mit jeder geplanten Mahlzeit weiter zur Seite
                val versatz = (if (anteil == 0f) 0f else 6f + anteil * 36f) * size.width / 44f
                val scheibe = Path().apply { addOval(Rect(center, r)) }
                clipPath(scheibe) { drawCircle(NACHT, r, Offset(center.x + versatz, center.y)) }
            }
            when {
                an -> drawCircle(glut, r, style = Stroke(1.2.dp.toPx()))
                leer -> {
                    val strich = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    drawCircle(f.nebentext, r, style = Stroke(1.2.dp.toPx(), pathEffect = strich))
                }
            }
        }
        // Das Kürzel wächst nur bis 130 %, der Bogen hat feste Maße
        Text(wochentagKurz(t.datum), style = Grauen.titel(13f * min(k, 1.3f) / k, 0.06f), color = if (an) f.text else f.nebentext)
    }
}

@Composable
private fun Tafel(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    Column(
        Modifier.fillMaxWidth().border(1.dp, LINIE).padding(4.dp).background(Brush.verticalGradient(TAFEL)).border(1.dp, LINIE)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tagLangText(t.datum) + if (heute) " · heute" else "", style = Grauen.titel(15f, 0.04f), color = f.primaer)
        if (t.eintraege.isEmpty()) {
            Text("Neumond. Nichts ist geplant.", style = Grauen.text(22f), color = f.text)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(e, { aktionen.onEintrag(e) }, oben = Grauen.maschine(12f), gericht = Grauen.text(20f), stand = Grauen.maschine(13f))
            }
            tagKcal(t)?.let { Text(it, style = Grauen.maschine(13f), color = f.nebentext) }
            Text(mondNotiz(t, heute), style = Grauen.text(15.5f, kursiv = true), color = f.nebentext)
        }
        GrauenKnopf(
            "+ Mahlzeit", { aktionen.onPlus(t.datum) }, Modifier.fillMaxWidth().padding(top = 6.dp),
            beschreibung = "Mahlzeit hinzufügen", umriss = true,
        )
    }
}

/** „Die dunklen Nächte füllen lassen“: gestrichelt in der Glut. */
@Composable
private fun Naechte(onKlick: () -> Unit) {
    val glut = FoodyTheme.stil.zier
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .drawBehind {
                drawRect(glut, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = "Leere Tage füllen" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Die dunklen Nächte füllen lassen", style = Grauen.maschine(14f), color = FoodyTheme.farben.text, textAlign = TextAlign.Center)
    }
}
