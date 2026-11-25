package de.foody.feature.woche.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.komponenten.roemischVoll
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.GrauenSeite
import de.foody.core.designsystem.stil.grauen.umblaettern
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * „Woche“ im Kosmischen Grauen (U) in Immersiv (Entwurf `U-Woche`, Runde 2 G3): „Mondphasen“ mit Kalenderwoche und
 * Ø kcal, darunter die sieben Tage als Monde auf einem Bogen – je mehr Mahlzeiten geplant sind, desto voller der Mond,
 * leere Tage sind Neumond mit gestricheltem Rand, heute leuchtet. Ein Tipp auf einen Mond wählt den Tag, Wischen über
 * den Bogen blättert die Woche (dazu ‹ › leise in den oberen Ecken des Himmels, „Heute ›“ im Kopf, wenn eine andere
 * Woche zu sehen ist). Darunter der gewählte Tag als Blatt eines Almanachs (Runde 2, Buch ausbauen): Wischen blättert
 * zum Nachbartag, die Monde blättern mit; Seitenzahl ist der Tag des Jahres. Ganz unten das Ritual „Die dunklen Nächte
 * füllen“ mit dem Stand „2 von 7 Nächten erhellt“. Auf dem Tablet liegt der Almanach als Doppelseite offen: links Kopf,
 * Mondbogen und Ritual, rechts der Tag. In Ruhig wie im Entwurf auf Pergament: „Woche“ mit
 * „2 / 7 · planen ›“ und Blättern, sieben Tageschips statt des Bogens, eine helle Tafel ohne Notiz, „Leere Tage
 * vorschlagen lassen“.
 */
@Composable
internal fun WocheGrauen(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    if (!FoodyTheme.stil.inszeniert) {
        WocheRahmen(unten) {
            Kopf(w, aktionen.onFuellen)
            Blaettern(w, aktionen, Grauen.maschine(13f))
            if (w.tage.size == 7) TagChips(w, gewaehlt) { gewaehlt = it }
            w.tage.getOrNull(gewaehlt)?.let { Tafel(it, it.datum == w.heute, aktionen) }
            Naechte(aktionen.onFuellen)
        }
        return
    }
    val himmel = @Composable {
        MondKopf(w, aktionen.onHeute)
        if (w.tage.size == 7) Mondbogen(w, gewaehlt, aktionen.onBlaettern) { gewaehlt = it }
    }
    // Je Woche ein neues Buch, aufgeschlagen beim gewählten Tag
    val tage = @Composable { hoehe: Dp -> key(w.woche?.montag) { TagBlaetter(w, gewaehlt, aktionen, hoehe) { gewaehlt = it } } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= FoodyBreiten.zweispaltig) {
            // Die rechte Seite ist mindestens so hoch wie die linke, damit das Buch als Ganzes daliegt
            val dichte = LocalDensity.current
            var links by remember { mutableStateOf(0.dp) }
            WocheRahmen(unten, breite = FoodyBreiten.zweiBereiche) {
                Row(Modifier.fillMaxWidth()) {
                    GrauenSeite(kapitel = null, seite = null, Modifier.weight(1f).onSizeChanged { links = with(dichte) { it.height.toDp() } }) {
                        himmel()
                        Ritual(w, aktionen.onFuellen)
                    }
                    Box(Modifier.weight(1f)) { tage(links) }
                }
            }
        } else {
            WocheRahmen(unten) {
                himmel()
                tage(0.dp)
                Ritual(w, aktionen.onFuellen)
            }
        }
    }
}

/**
 * Die Tage der Woche als Blätter des Almanachs, jedes mindestens [hoehe] hoch; das Blatt des Tages [gewaehlt] liegt
 * oben. Blättert der Nutzer, meldet [onWahl] den neuen Tag; wählt er einen Mond, blättert das Buch mit.
 */
@Composable
private fun TagBlaetter(w: WocheDaten, gewaehlt: Int, aktionen: WocheSageAktionen, hoehe: Dp, onWahl: (Int) -> Unit) {
    if (w.tage.isEmpty()) return
    val pager = rememberPagerState(initialPage = gewaehlt.coerceIn(0, w.tage.lastIndex)) { w.tage.size }
    val bewegt = animationenErlaubt()
    val gezeigt by rememberUpdatedState(gewaehlt)
    val melden by rememberUpdatedState(onWahl)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (it != gezeigt) melden(it) }
    }
    LaunchedEffect(gewaehlt) {
        if (gewaehlt !in 0..<pager.pageCount || pager.currentPage == gewaehlt || pager.isScrollInProgress) return@LaunchedEffect
        if (bewegt) pager.animateScrollToPage(gewaehlt) else pager.scrollToPage(gewaehlt)
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { i ->
        val t = w.tage[i]
        val heute = t.datum == w.heute
        GrauenSeite(
            kapitel = tagLangText(t.datum) + if (heute) " · heute" else "",
            seite = roemischVoll(t.datum.dayOfYear),
            modifier = Modifier.umblaettern(pager, i).heightIn(min = hoehe),
            eselsohr = i < w.tage.lastIndex,
        ) {
            TagEintraege(t, heute, aktionen, notiz = true)
            // Die Seitenzahl steht unten auf dem Blatt
            if (hoehe > 0.dp) Spacer(Modifier.weight(1f))
        }
    }
}

/** „2 von 7 Nächten erhellt“: der Stand des Rituals. */
internal fun naechteStand(geplant: Int, tage: Int): String = "$geplant von $tage ${if (tage == 1) "Nacht" else "Nächten"} erhellt"

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

/** Rahmenlinie auf dem Pergament (Ruhig): die Kante des Stils. */
@Composable
private fun linie(): Color = FoodyTheme.farben.lichtkante

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten, onFuellen: () -> Unit) {
    val f = FoodyTheme.farben
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            w.woche?.let { Text(kwText(it.montag), style = Grauen.maschine(12.5f), color = f.nebentext) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                szenenWort("Mondphasen", "Woche"),
                Modifier.semantics {
                    heading()
                    contentDescription = "Woche"
                },
                style = Grauen.titel(titelGroesse(34f), 0.02f), color = f.text,
            )
        }
        if (w.tage.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Bottom).heightIn(min = 48.dp).border(1.dp, linie())
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

/**
 * Kopf in Immersiv: Kalenderwoche, „Mondphasen“ und darunter Ø kcal; rechts „Heute ›“, wenn eine andere Woche zu
 * sehen ist. Blättern und Planen liegen am Bogen bzw. im Ritual unten.
 */
@Composable
private fun MondKopf(w: WocheDaten, onHeute: () -> Unit) {
    val f = FoodyTheme.farben
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            w.woche?.let { Text(kwText(it.montag), style = Grauen.maschine(12.5f), color = f.nebentext) }
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Mondphasen",
                Modifier.semantics {
                    heading()
                    contentDescription = "Woche"
                },
                style = Grauen.titel(titelGroesse(34f), 0.02f), color = f.text,
            )
            w.schnitt?.let { Text(it, style = Grauen.maschine(12.5f), color = f.nebentext) }
        }
        if (!w.zeigtHeute) {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onHeute)
                    .semantics { contentDescription = "Heute" }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Heute ›", style = Grauen.maschine(13f), color = f.primaer)
            }
        }
    }
}

/** Ab dieser Strecke blättert ein Wischen über den Bogen die Woche. */
private val WISCH_STRECKE = 56.dp

@Composable
private fun Mondbogen(w: WocheDaten, gewaehlt: Int, onBlaettern: (Long) -> Unit, onWahl: (Int) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val strecke = with(LocalDensity.current) { WISCH_STRECKE.toPx() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(210.dp).clipToBounds().pointerInput(Unit) {
            var weg = 0f
            detectHorizontalDragGestures(
                onDragStart = { weg = 0f },
                onDragEnd = { if (abs(weg) > strecke) onBlaettern(if (weg < 0f) 1 else -1) },
            ) { _, dx -> weg += dx }
        },
    ) {
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
        // Leise Pfeile in den oberen Ecken des Himmels, über den Enden des Bogens
        Pfeil("‹", "Vorige Woche", Modifier.align(Alignment.TopStart)) { onBlaettern(-1) }
        Pfeil("›", "Nächste Woche", Modifier.align(Alignment.TopEnd)) { onBlaettern(1) }
    }
}

@Composable
private fun Pfeil(zeichen: String, beschreibung: String, modifier: Modifier, onKlick: () -> Unit) {
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClick = onKlick).semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Text(zeichen, style = Grauen.titel(22f), color = FoodyTheme.farben.nebentext.copy(alpha = 0.7f))
    }
}

/** Ruhig: die sieben Tage als Chips in einer Reihe, der gewählte in Tinte, heute in der Akzentfarbe. */
@Composable
private fun TagChips(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val f = FoodyTheme.farben
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        w.tage.forEachIndexed { i, t ->
            val heute = i == w.heuteIndex
            val an = i == gewaehlt
            val schrift = when {
                an -> Grauen.papierHell
                heute -> f.primaer
                else -> f.text
            }
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).background(if (an) f.text else Color.Transparent).border(1.dp, f.lichtkante)
                    .tagWahl(t, heute, an) { onWahl(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(wochentagKurz(t.datum), style = Grauen.titel(15f), color = schrift)
            }
        }
    }
}

@Composable
private fun Mond(t: WochenTag, heute: Boolean, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val leer = t.eintraege.isEmpty()
    val anteil = mondAnteil(t)
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
        Text(wochentagKurz(t.datum), style = Grauen.titel(titelGroesse(13f), 0.06f), color = if (an) f.text else f.nebentext)
    }
}

/** Ruhig: die helle Tafel des gewählten Tages. */
@Composable
private fun Tafel(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val linie = linie()
    Column(
        Modifier.fillMaxWidth().border(1.dp, linie).padding(4.dp).background(Color.White.copy(alpha = 0.35f)).border(1.dp, linie)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tagLangText(t.datum) + if (heute) " · heute" else "", style = Grauen.titel(15f, 0.04f), color = FoodyTheme.farben.primaer)
        TagEintraege(t, heute, aktionen, notiz = false)
    }
}

/** Die Einträge eines Tages, Kalorien, mit [notiz] die Notiz des Mondes, und „+ Mahlzeit“. */
@Composable
private fun TagEintraege(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen, notiz: Boolean) {
    val f = FoodyTheme.farben
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (t.eintraege.isEmpty()) {
            Text(szenenWort("Neumond. Nichts ist geplant.", "Noch nichts geplant"), style = Grauen.text(22f), color = f.text)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(e, { aktionen.onEintrag(e) }, oben = Grauen.maschine(12f), gericht = Grauen.text(20f), stand = Grauen.maschine(13f))
            }
            tagKcal(t)?.let { Text(it, style = Grauen.maschine(13f), color = f.nebentext) }
            if (notiz) Text(mondNotiz(t, heute), style = Grauen.text(15.5f, kursiv = true), color = f.nebentext)
        }
        GrauenKnopf(
            "+ Mahlzeit", { aktionen.onPlus(t.datum) }, Modifier.fillMaxWidth().padding(top = 6.dp),
            beschreibung = "Mahlzeit hinzufügen", umriss = true,
        )
    }
}

/**
 * Immersiv: das Ritual „Die dunklen Nächte füllen“ mit dem Stand („2 von 7 Nächten erhellt“), gestrichelt in der
 * Glut; ersetzt das „planen ›“ im Kopf.
 */
@Composable
private fun Ritual(w: WocheDaten, onKlick: () -> Unit) {
    val glut = FoodyTheme.stil.zier
    val f = FoodyTheme.farben
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .drawBehind {
                drawRect(glut, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(role = Role.Button, onClick = onKlick)
            .clearAndSetSemantics { contentDescription = "Leere Tage füllen, ${w.geplant} von ${w.tage.size} Tagen geplant" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Die dunklen Nächte füllen", style = Grauen.maschine(14f), color = f.text, textAlign = TextAlign.Center)
        if (w.tage.isNotEmpty()) Text(naechteStand(w.geplant, w.tage.size), style = Grauen.maschine(12f), color = glut, textAlign = TextAlign.Center)
    }
}

/** Ruhig: „Leere Tage vorschlagen lassen“, gestrichelt. */
@Composable
private fun Naechte(onKlick: () -> Unit) {
    val glut = if (FoodyTheme.stil.inszeniert) FoodyTheme.stil.zier else FoodyTheme.farben.primaer
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
        Text("Leere Tage vorschlagen lassen", style = Grauen.maschine(14f), color = FoodyTheme.farben.text, textAlign = TextAlign.Center)
    }
}
