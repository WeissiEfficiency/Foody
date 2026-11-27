package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.grauenFrakturTitel
import de.foody.core.designsystem.stil.grauen.grauenHell
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse

/*
 * „Rezepte“ im Kosmischen Grauen (U) in Immersiv: „Die Bibliothek – Ein Regal, das niemand aufgestellt hat“, alle
 * Treffer als Bände auf Brettern zu je vier, endlos nach unten; der Titel steht längs auf dem Rücken, Antippen schlägt
 * den Band auf (Wunsch 2026-11-24 statt Seiten mit Pergamentkarte). In Ruhig wie im Entwurf: „Rezepte“, die Treffer als Zeilen mit Name und kcal, eine helle Seite ohne
 * Schein, „Öffnen“.
 */

/** Einbände und Höhen der Bände. */
private val EINBAND = listOf(Color(0xFF2E3B2C), Color(0xFF4A2228), Color(0xFF24324A), Color(0xFF5A3A1C), Color(0xFF3A2A48), Color(0xFF1E3A36))
private val HOEHE = listOf(176, 162, 184, 168, 158, 180)
private val PERGAMENT = Grauen.pergament
private val TINTE = Grauen.tinte
private val TINTE_NEBEN = Grauen.tinteNeben
private val BRETT = Color(0xFF2B2117)
private val BRETT_KANTE = Color(0xFF4A3826)
private val GOLD = Color(0xFFB59A5A)
private val RUECKEN_SCHRIFT = Color(0xFFE6DCC2)

/** So viele Bände stehen auf einem Brett der Bibliothek. */
internal const val JE_BRETT = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GrauenKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            if (FoodyTheme.stil.inszeniert) Text("Ein Regal, das niemand aufgestellt hat", style = Grauen.text(14f, kursiv = true), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                szenenWort("Die Bibliothek", "Rezepte"), Modifier.semantics { heading() },
                // Im Necronomicon in Fraktur wie die übrigen Seitentitel
                style = if (FoodyTheme.stil.grauenBuch) grauenFrakturTitel(titelGroesse(34f)) else Grauen.titel(titelGroesse(30f)),
                color = grauenHell(),
            )
            if (n > 0) Text(szenenWort("$n Bände", "$n Rezepte"), style = Grauen.text(14f), color = f.nebentext)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/**
 * Brett [brett] der Bibliothek (Immersiv): bis zu [JE_BRETT] Bände nebeneinander auf einem Holzbrett, der Titel längs
 * auf dem Rücken wie bei einem Buch. Steht ein Brett nicht voll, lehnt der letzte Band schräg am Nachbarn. Antippen
 * schlägt den Band auf ([onOeffnen] bekommt seine Lage im Fenster und die Farbe des Einbands für den Übergang);
 * [offen] (rechts offen auf dem Tablet) glüht, der Band [fliegt] ist unsichtbar, solange er aus dem Regal kommt.
 */
@Composable
internal fun GrauenBrett(
    baende: List<RezeptKarte>,
    brett: Int,
    offen: String?,
    onOeffnen: (RezeptKarte, Rect?, Color) -> Unit,
    modifier: Modifier,
    fliegt: () -> String? = { null },
) {
    val feder = FoodyTheme.stil.grauenBuch
    Row(
        modifier.drawBehind { brettMalen(feder) }.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        baende.forEachIndexed { spalte, r ->
            val lehnt = baende.size in 2 until JE_BRETT && spalte == baende.lastIndex
            val schraeg = if (lehnt) {
                Modifier.graphicsLayer {
                    rotationZ = -5f
                    transformOrigin = TransformOrigin(0f, 1f)
                }
            } else {
                Modifier
            }
            val weg = Modifier.graphicsLayer { alpha = if (fliegt() == r.id) 0f else 1f }
            Band(r, brett * JE_BRETT + spalte, r.id == offen, Modifier.weight(1f).then(schraeg).then(weg)) { lage, farbe -> onOeffnen(r, lage, farbe) }
        }
        repeat(JE_BRETT - baende.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Ein Band: Einband mit goldenen Bünden, der Name längs, unten kcal; TalkBack hört „Öffnen: Reispfanne, 30 Min., …“. */
@Composable
private fun Band(r: RezeptKarte, i: Int, an: Boolean, modifier: Modifier, onKlick: (Rect?, Color) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val form = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
    val einband = EINBAND[i % EINBAND.size]
    var lage by remember { mutableStateOf<Rect?>(null) }
    val schatten = lerp(einband, Color.Black, 0.45f)
    Column(
        modifier.height(HOEHE[i % HOEHE.size].dp).onGloballyPositioned { lage = it.boundsInRoot() }
            .then(if (an) Modifier.shadow(14.dp, form, ambientColor = glut, spotColor = glut) else Modifier)
            .background(Brush.horizontalGradient(listOf(schatten, einband, lerp(einband, Color.White, 0.08f), einband, schatten)), form)
            .border(if (an) 1.5.dp else 1.dp, if (an) glut else Color.Black.copy(alpha = 0.45f), form)
            .clickable(role = Role.Button, onClickLabel = "Aufschlagen") { onKlick(lage, einband) }
            .semantics(mergeDescendants = true) { contentDescription = "Öffnen: ${rezeptBeschreibung(r)}" }
            .padding(vertical = 8.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Buende()
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                r.name, Modifier.laengs(), style = Grauen.name(17f).copy(lineHeight = 19.sp), color = if (an) glut else RUECKEN_SCHRIFT,
                textAlign = TextAlign.Center, maxLines = 3,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 17.sp),
            )
        }
        if (r.favorit) Text("♦", style = Grauen.text(12f), color = GOLD)
        Text(r.kcalJePortion?.toString() ?: "–", style = Grauen.maschine(10f), color = RUECKEN_SCHRIFT.copy(alpha = 0.8f), maxLines = 1)
        Buende()
    }
}

/** Zwei goldene Bünde quer über den Rücken. */
@Composable
private fun Buende() {
    Column(Modifier.fillMaxWidth(0.8f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) { Box(Modifier.fillMaxWidth().height(1.5.dp).background(GOLD.copy(alpha = 0.7f))) }
    }
}

/** Dreht den Inhalt um 90° (von oben nach unten zu lesen); Breite und Höhe tauschen beim Messen. */
private fun Modifier.laengs(): Modifier = layout { messbar, c ->
    val p = messbar.measure(Constraints(maxWidth = c.maxHeight, maxHeight = c.maxWidth))
    layout(p.height, p.width) { p.place((p.height - p.width) / 2, (p.width - p.height) / 2) }
}.rotate(90f)

/** Ruhig: die Treffer als Zeilen (die Bibliothek mit Brettern gibt es nur in Immersiv). */
@Composable
internal fun GrauenRegal(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) = GrauenListe(liste, gewaehlt, onWahl)

/** Ruhig wie im Entwurf: die Treffer als Zeilen mit Name und kcal, die gewählte mit einem Hauch Akzent. */
@Composable
private fun GrauenListe(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        liste.forEach { r ->
            val an = r.id == gewaehlt
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (an) akzent.copy(alpha = 0.12f) else Color.Transparent)
                    .drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .rezeptWahl(r, an) { onWahl(r.id) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(r.name, Modifier.weight(1f), style = Grauen.name(17f), color = f.text)
                r.kcalJePortion?.let { Text("$it kcal", style = Grauen.maschine(13f), color = f.nebentext) }
            }
        }
    }
}

/** Die aufgeschlagene Seite des gewählten Bandes; „Aufschlagen“ darunter im Dunkel. */
@Composable
internal fun GrauenSeite(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val inszeniert = FoodyTheme.stil.inszeniert
    val form = RoundedCornerShape(2.dp)
    val blatt = if (inszeniert) {
        Modifier.shadow(18.dp, form, ambientColor = glut, spotColor = glut).background(PERGAMENT, form)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.4f), form).border(1.dp, FoodyTheme.farben.lichtkante, form)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().then(blatt)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val band = if (inszeniert) grauenBand(r) else r.tags.firstOrNull()?.uppercase(java.util.Locale.GERMAN)
            band?.let { Text(it, style = Grauen.titel(12f, 0.14f), color = TINTE_NEBEN) }
            Text(r.name, style = Grauen.name(22f), color = TINTE)
            val werte = listOfNotNull(zeitUndKcal(r)?.let { if (r.kcalJePortion != null) "$it je Portion" else it }, if (r.favorit) "♦ Favorit" else null)
            if (werte.isNotEmpty()) Text(werte.joinToString(" · "), style = Grauen.text(15f), color = TINTE_NEBEN)
            zutatenSatz(r)?.let { Text(it, style = Grauen.text(16f, kursiv = true), color = TINTE) }
        }
        GrauenKnopf(szenenWort("Aufschlagen", "Öffnen"), { onOeffnen(r.id) }, Modifier.fillMaxWidth(), beschreibung = "Öffnen: ${r.name}")
    }
}

/** „BAND · OFENGERICHT“ bzw. „BAND · OHNE ORDNUNG“: der erste Tag als Titel des Bandes. */
internal fun grauenBand(r: RezeptKarte): String = "Band · ${r.tags.firstOrNull() ?: "ohne Ordnung"}".uppercase(java.util.Locale.GERMAN)

private val BRETT_BUCH = Color(0xFFB09A70)

/**
 * Das Brett unter den Bänden, 12 dp hoch am unteren Rand: in Ominös dunkles Holz mit Schatten, mit [feder]
 * (Necronomicon) ein helles Brett mit Kanten und Maserung in Tinte, der Schatten darunter schraffiert.
 */
internal fun DrawScope.brettMalen(feder: Boolean) {
    val dicke = 12.dp.toPx()
    val oben = size.height - dicke
    if (!feder) {
        drawRect(BRETT, Offset(0f, oben), Size(size.width, dicke))
        drawLine(BRETT_KANTE, Offset(0f, oben), Offset(size.width, oben), 2.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, size.height), Size(size.width, 4.dp.toPx()))
        return
    }
    val strich = 1.dp.toPx()
    drawRect(BRETT_BUCH, Offset(0f, oben), Size(size.width, dicke))
    drawLine(TINTE, Offset(0f, oben), Offset(size.width, oben), 1.4f * strich)
    drawLine(TINTE, Offset(0f, size.height), Offset(size.width, size.height), strich)
    // Maserung: lange, leicht versetzte Striche
    listOf(0.35f to 0.1f, 0.6f to 0.45f, 0.8f to 0.2f).forEach { (y, start) ->
        val hoehe = oben + dicke * y
        drawLine(TINTE.copy(alpha = 0.35f), Offset(size.width * start, hoehe), Offset(size.width * (start + 0.4f), hoehe), 0.7f * strich)
    }
    // Schatten unter dem Brett als kurze Schraffur
    var x = 0f
    while (x < size.width) {
        drawLine(TINTE.copy(alpha = 0.4f), Offset(x, size.height + 1.dp.toPx()), Offset(x + 3.dp.toPx(), size.height + 4.dp.toPx()), 0.7f * strich)
        x += 3.dp.toPx()
    }
}
