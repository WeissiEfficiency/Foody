package de.foody.feature.woche.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageKopf
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.SageRaute
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.sage.sageLinieUnten
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.woche.mahlzeitAnzeige
import de.foody.feature.woche.wochenTitel
import java.time.LocalDate
import java.util.Locale

/** Was die Karte der Woche auslösen kann; die Blätter (Mahlzeit, Wahl, Füllen) liegen weiter in der Route. */
internal class WocheSageAktionen(
    val onBlaettern: (Long) -> Unit,
    val onHeute: () -> Unit,
    val onEintrag: (PlanEintrag) -> Unit,
    val onPlus: (LocalDate) -> Unit,
    val onFuellen: () -> Unit,
)

/**
 * „Woche“ der Düsteren Sage in Immersiv (Entwurf `Q-Woche`): Kopf „Karte der Woche“ mit Blättern, eine Karte mit
 * einem Weg durch die sieben Tage (heute leuchtet, leere Tage hohl), darunter der gewählte Tag mit seinen Gerichten
 * (Antippen öffnet das Mahlzeit-Blatt), „+ Mahl hinzufügen“ und „Leere Tage füllen“.
 */
@Composable
internal fun WocheSage(woche: WochenAnsicht?, heute: LocalDate, zeigtHeute: Boolean, aktionen: WocheSageAktionen, unten: Dp) {
    val tage = woche?.tage.orEmpty()
    val heuteIndex = tage.indexOfFirst { it.datum == heute }
    var gewaehlt by rememberSaveable(woche?.montag) { mutableIntStateOf(heuteIndex.coerceAtLeast(0)) }
    val f = FoodyTheme.farben
    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = unten),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SageKopf(woche?.let { wochenTitel(it.montag).uppercase(Locale.GERMAN) } ?: "", "KARTE DER WOCHE", "Woche") {
                if (tage.isNotEmpty()) {
                    Text(
                        "${tage.count { it.eintraege.isNotEmpty() }} / ${tage.size}", Modifier.padding(bottom = 12.dp),
                        style = Sage.titel(15f, 0.04f), color = f.text,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SageNebenknopf("‹", "Vorige Woche", { aktionen.onBlaettern(-1) }, Modifier.size(48.dp), akzent = true, schrift = Sage.titel(22f, 0f))
                Text(
                    woche?.durchschnitt?.let { "Ø ${if (it.vollstaendig) "" else "mind. "}${ganzzahlDeutsch(it.kcal)} kcal am Tag" } ?: "",
                    Modifier.weight(1f), style = Sage.text(15f), color = f.nebentext,
                )
                if (!zeigtHeute) SageNebenknopf("HEUTE", "Heute", aktionen.onHeute, akzent = true)
                SageNebenknopf("›", "Nächste Woche", { aktionen.onBlaettern(1) }, Modifier.size(48.dp), akzent = true, schrift = Sage.titel(22f, 0f))
            }
            if (tage.size == ORTE.size) {
                Karte(tage, heuteIndex, gewaehlt) { gewaehlt = it }
            }
            tage.getOrNull(gewaehlt)?.let { TagSeite(it, it.datum == heute, aktionen) }
        }
    }
}

/** Lage der sieben Tage auf der Karte (Maß des Entwurfs 348 × 390), Montag unten links bis Sonntag oben. */
private val ORTE = listOf(44f to 344f, 112f to 302f, 72f to 224f, 160f to 182f, 252f to 222f, 302f to 142f, 222f to 62f)
private const val KARTE_B = 348f
private const val KARTE_H = 390f

@Composable
private fun Karte(tage: List<WochenTag>, heuteIndex: Int, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val pfade = remember { KartenPfade() }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .drawBehind {
                val a = 4.dp.toPx()
                drawRect(f.balkenSpur, Offset(-a, -a), Size(size.width + 2 * a, size.height + 2 * a), style = Stroke(1.dp.toPx()))
            }
            .padding(4.dp)
            .border(1.dp, f.lichtkante),
    ) {
        val s = maxWidth.value / KARTE_B
        Box(
            Modifier.fillMaxWidth().height((KARTE_H * s).dp).clipToBounds()
                .background(Brush.radialGradient(listOf(Color(0xFF241F17), Color(0xFF15120D)))),
        ) {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
                scale(size.width / KARTE_B, Offset.Zero) {
                    drawPath(pfade.fluss, Color(0xFF3B4A52).copy(alpha = 0.45f), style = Stroke(10f))
                    drawPath(pfade.berge, Color(0xFF5A4F38), style = Stroke(1.4f))
                    listOf(Offset(300f, 40f) to 3f, Offset(310f, 48f) to 2.5f, Offset(292f, 50f) to 2f, Offset(30f, 250f) to 3f, Offset(40f, 258f) to 2.5f)
                        .forEach { (o, r) -> drawCircle(Color(0xFF5A4F38).copy(alpha = 0.5f), r, o) }
                    drawPath(
                        pfade.weg, glut.copy(alpha = 0.65f),
                        style = Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))),
                    )
                    // Windrose
                    drawCircle(Color(0xFF9A9079), 14f, Offset(316f, 350f), style = Stroke(1f))
                    drawLine(Color(0xFF9A9079).copy(alpha = 0.5f), Offset(316f, 332f), Offset(316f, 368f), 1f)
                    drawLine(Color(0xFF9A9079).copy(alpha = 0.5f), Offset(298f, 350f), Offset(334f, 350f), 1f)
                    drawPath(pfade.nadel, Color(0xFF9A9079))
                }
            }
            tage.forEachIndexed { i, t ->
                val (x, y) = ORTE[i]
                TagMarke(
                    t, heute = i == heuteIndex, an = i == gewaehlt,
                    Modifier.offset((x * s).dp - 26.dp, (y * s).dp - 26.dp),
                ) { onWahl(i) }
            }
        }
    }
}

@Composable
private fun TagMarke(t: WochenTag, heute: Boolean, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val leer = t.eintraege.isEmpty()
    val glut = FoodyTheme.stil.zier
    val groesse = if (heute) 18.dp else if (an) 15.dp else 12.dp
    val fuellung = when {
        heute -> FEUER
        an -> glut
        else -> glut.copy(alpha = 0.5f)
    }
    Column(
        modifier.size(52.dp)
            .clickable(role = Role.Tab, onClick = onKlick)
            .semantics(mergeDescendants = true) {
                selected = an
                contentDescription = tagLangText(t.datum) + (if (heute) ", heute" else "") + ": " +
                    (if (leer) "nichts geplant" else t.eintraege.joinToString(", ") { it.rezeptName })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        SageRaute(groesse, gefuellt = !leer || heute, fuellung = fuellung, glanz = heute || an)
        Text(
            wochentagKurz(t.datum).uppercase(Locale.GERMAN),
            style = Sage.titel(11f, 0.1f).copy(shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)),
            color = if (an) sageHell() else FoodyTheme.farben.nebentext,
        )
    }
}

@Composable
private fun TagSeite(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val n = t.naehrwerte
    Column(
        Modifier.fillMaxWidth().border(1.dp, f.balkenSpur)
            .background(Brush.verticalGradient(listOf(glut.copy(alpha = 0.06f), Color.Transparent)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (tagLangText(t.datum) + if (heute) " · heute" else "").uppercase(Locale.GERMAN),
            style = Sage.titel(11.5f, 0.22f), color = f.primaer,
        )
        if (t.eintraege.isEmpty()) {
            Text("Noch unentdeckt", style = Sage.text(20f), color = f.text)
            Text("Kein Mahl geplant", style = Sage.text(15f), color = f.nebentext)
        } else {
            t.eintraege.forEach { e -> Gericht(e) { aktionen.onEintrag(e) } }
            n?.let { Text((if (it.complete) "" else "mind. ") + "${ganzzahlDeutsch(it.kcal)} kcal geplant", style = Sage.text(15f), color = f.nebentext) }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SageNebenknopf("+ MAHL HINZUFÜGEN", "Mahlzeit hinzufügen", { aktionen.onPlus(t.datum) }, Modifier.weight(1f))
            SageNebenknopf("LEERE TAGE FÜLLEN", "Leere Tage füllen", aktionen.onFuellen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Gericht(e: PlanEintrag, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .sageLinieUnten(f.balkenSpur).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(mahlzeitAnzeige(e.mahlzeit).uppercase(Locale.GERMAN), style = Sage.titel(10.5f, 0.14f), color = f.nebentext)
            Text(e.rezeptName, style = Sage.text(19f), color = f.text)
        }
        Text(if (e.gekocht) "✓ gekocht" else "${e.portionen} P.", style = Sage.text(15f), color = if (e.gekocht) f.oliv else f.text)
    }
}

private val FEUER = Color(0xFFF59E0B)

private class KartenPfade {
    private fun pfad(d: String) = PathParser().parsePathString(d).toPath()
    val fluss = pfad("M0 120c40 10 60-10 100 0s70 30 120 20 80-30 128-20")
    val berge = pfad("M22 60l10-14 10 14M36 64l8-11 8 11M280 300l12-16 12 16M298 306l9-12 9 12M180 330l10-13 10 13M120 40l9-12 9 12")
    val weg = pfad("M44 344C70 330 96 318 112 302S80 240 72 224 130 190 160 182 236 226 252 222 290 160 302 142 250 80 222 62")
    val nadel = pfad("M316 338l3 12-3 12-3-12z")
}
