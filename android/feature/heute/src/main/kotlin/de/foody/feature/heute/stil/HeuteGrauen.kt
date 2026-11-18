package de.foody.feature.heute.stil

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.anzeigename
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalStand
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.kcalZahl
import de.foody.core.designsystem.komponenten.makroSatz
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.GrauenNotizknopf
import de.foody.core.designsystem.stil.svgPfad
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.domain.zielAnteil
import de.foody.feature.heute.DatenbankLeerHinweis
import de.foody.feature.heute.GegessenFrage
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteRahmen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.meta
import de.foody.feature.heute.rezeptLink
import java.time.LocalDate
import java.util.Locale
import java.time.format.TextStyle as Wochentagform

/**
 * „Heute“ des Kosmischen Grauens in Immersiv (Entwurf `U-Heute`): „Das Auge“ blickt auf den Tag – der Ring um die
 * Iris füllt sich mit den kcal gegen das Ziel, darunter ein Satz dazu. Dann das Ritual (die nächste Mahlzeit) und
 * das Protokoll „Was bisher dargebracht wurde“ mit den Einträgen je Mahlzeit. Zustand und Aktionen wie in der
 * normalen Ansicht; Schweben und Blinzeln ruhen, wenn Animationen aus sind. TalkBack hört die normalen Begriffe.
 * In Ruhig wie im Entwurf auf Pergament: „Heute“, statt des Auges ein schlichter Ring, keine Fangarme, normale Wörter.
 */
@Composable
internal fun HeuteGrauen(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (FoodyTheme.stil.inszeniert) Tiefe(Modifier.matchParentSize())
        HeuteRahmen(innen = PaddingValues(start = 22.dp, end = 10.dp, top = 14.dp)) { rahmen ->
            Column(rahmen, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Kopf(zustand, aktionen.onEinstellungen)
                if (zustand == null) return@Column
                Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (zustand.datenbankLeer) DatenbankLeerHinweis(aktionen.onEinstellungen)
                    if (FoodyTheme.stil.inszeniert) DasAuge(zustand.tagebuch) else SchlichterRing(zustand.tagebuch)
                    Ritual(zustand, aktionen)
                    Protokoll(zustand.tagebuch, aktionen)
                    zustand.einkauf?.let { e ->
                        val text = szenenWort("Expeditionsliste", "Einkauf") + " · ${e.anzahl} offen ›"
                        Verweis(text, "Einkauf, ${e.anzahl} offen") { aktionen.onEinkauf(e.listeId) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(zustand: HeuteZustand?, onEinstellungen: () -> Unit) {
    val f = FoodyTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Titel und Datum in einer Zeile wie im Entwurf; bei großer Schrift rückt das Datum unter den Titel
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    szenenWort("Das Auge", "Heute"),
                    Modifier.align(Alignment.CenterVertically).semantics {
                        heading()
                        contentDescription = "Heute"
                    },
                    style = Grauen.titel(34f, 0.02f), color = f.text, maxLines = 1,
                )
                zustand?.datum?.let {
                    Text(grauenDatum(it), Modifier.align(Alignment.CenterVertically).padding(start = 8.dp), style = Grauen.maschine(12.5f), color = f.nebentext)
                }
            }
            IconButton(onEinstellungen) { Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen", tint = f.nebentext) }
        }
        zustand?.sync?.let { SyncStatus(it) }
    }
}

/** „Sa · 24. Oktober“ wie im Entwurf: Wochentag kurz ohne Punkt, Tag und Monat. */
internal fun grauenDatum(d: LocalDate): String =
    wochentagKurz(d) + " · ${d.dayOfMonth}. " +
        d.month.getDisplayName(Wochentagform.FULL, Locale.GERMAN)

/** Satz unter dem Auge: was es gesehen hat, gegen das Ziel. Ohne Ziel nur die Summe. */
internal fun augenSatz(kcal: Int, ziel: Int?, vollstaendig: Boolean = true): String = when {
    ziel == null -> "Es hat ${kcalText(kcal, vollstaendig)} gesehen."
    kcal > ziel -> "Es hat ${kcalStand(kcal, ziel, vollstaendig)} gesehen. Mehr, als es wollte."
    else -> "Es hat ${kcalStand(kcal, ziel, vollstaendig)} gesehen."
}

/** Das Auge mit dem Ring (Anteil am Ziel) und der Satz darunter; TalkBack hört einen nüchternen Satz. */
@Composable
private fun DasAuge(t: TagebuchHeute) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    val anteil = zielAnteil(s.kcal, ziel)
    val vorlesen = kcalStand(s.kcal, ziel, s.vollstaendig) +
        (if (ziel != null && s.kcal > ziel) ", über Ziel" else "") +
        ": " + makroSatz(s.eiweiss, s.kohlenhydrate, s.fett)
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = vorlesen },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Auge(anteil, glut, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(AUGE_BREITE / AUGE_HOEHE))
        val satz = augenSatz(s.kcal, ziel, s.vollstaendig)
        val zahl = kcalZahl(s.kcal, s.vollstaendig)
        Text(
            buildAnnotatedString {
                val i = satz.indexOf(zahl)
                append(satz.substring(0, i))
                withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontSize = 22.sp, color = glut)) { append(zahl) }
                append(satz.substring(i + zahl.length))
            },
            style = Grauen.text(18f, kursiv = true), color = f.text, textAlign = TextAlign.Center,
        )
        Text(
            makroSatz(s.eiweiss, s.kohlenhydrate, s.fett, " · "),
            style = Grauen.text(15f), color = f.nebentext, textAlign = TextAlign.Center,
        )
    }
}

/** Ruhig wie im Entwurf: statt des Auges ein Ring mit der Summe, daneben das Ziel und die Nährwerte mit Punkten. */
@Composable
private fun SchlichterRing(t: TagebuchHeute) {
    val f = FoodyTheme.farben
    val akzent = f.primaer
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    val anteil = zielAnteil(s.kcal, ziel)
    val vorlesen = kcalStand(s.kcal, ziel, s.vollstaendig) +
        (if (ziel != null && s.kcal > ziel) ", über Ziel" else "") +
        ": " + makroSatz(s.eiweiss, s.kohlenhydrate, s.fett)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = vorlesen }
            .drawBehind {
                drawLine(f.text, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                drawLine(f.text, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val dicke = 9.dp.toPx()
                val ecke = Offset(dicke / 2f, dicke / 2f)
                val mass = Size(size.width - dicke, size.height - dicke)
                drawArc(f.text.copy(alpha = 0.15f), 0f, 360f, false, ecke, mass, style = Stroke(dicke))
                if (anteil != null && anteil > 0f) drawArc(akzent, -90f, 360f * anteil, false, ecke, mass, style = Stroke(dicke))
            }
            Text(kcalZahl(s.kcal, s.vollstaendig), style = Grauen.name(22f), color = f.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (ziel != null) "von ${ganzzahlDeutsch(ziel)} kcal" else "kcal", style = Grauen.text(16f), color = f.text)
            Makro("Eiweiß", s.eiweiss, f.makro.eiweiss)
            Makro("KH", s.kohlenhydrate, f.makro.kohlenhydrate)
            Makro("Fett", s.fett, f.makro.fett)
        }
    }
}

@Composable
private fun Makro(name: String, gramm: Int, farbe: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(farbe, CircleShape))
        Text("$name $gramm g", style = Grauen.maschine(13.5f), color = FoodyTheme.farben.text)
    }
}

/**
 * Das Auge als Zeichnung im Maß des Entwurfs (300 × 170): Schein, Lid mit Adern, Iris, Ring mit dem Anteil in der
 * Glut, Pupille als Schlitz, Glanzpunkt. Es schwebt langsam und blinzelt selten.
 */
@Composable
private fun Auge(anteil: Float?, glut: Color, modifier: Modifier) {
    val bewegt = animationenErlaubt()
    val zeit = rememberInfiniteTransition(label = "Auge")
    val schweben by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "Schweben")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val lid by if (bewegt) {
        zeit.animateFloat(
            1f, 1f,
            infiniteRepeatable(
                keyframes {
                    durationMillis = 7000
                    1f at 6440
                    0.08f at 6650
                    1f at 6860
                },
            ),
            label = "Blinzeln",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val pfade = remember { AugenPfade() }
    // Das Schweben verschiebt nur die Ebene, ohne neu zu zeichnen; Verläufe entstehen einmal je Größe. Neu gezeichnet
    // wird nur, solange das Lid sich bewegt (Blinzeln, gut 0,4 s alle 7 s).
    Spacer(
        modifier.graphicsLayer { translationY = -4.dp.toPx() * schweben }.drawWithCache {
            val s = size.width / AUGE_BREITE
            val schein = Brush.radialGradient(listOf(glut.copy(alpha = 0.35f), glut.copy(alpha = 0f)), Offset(150f, 85f), 150f)
            val iris = Brush.radialGradient(
                0f to Color(0xFF1B3A2C), 0.7f to Color(0xFF0E2019), 1f to Color(0xFF06100C),
                center = Offset(150f, 85f), radius = 56f,
            )
            onDrawBehind {
                scale(s, Offset.Zero) {
                    drawOval(schein, Offset.Zero, Size(300f, 170f))
                    withTransform({ scale(1f, lid, Offset(150f, 85f)) }) {
                        drawPath(pfade.lid, Color(0xFFC9C4A8).copy(alpha = 0.12f))
                        drawPath(pfade.lid, Color(0xFF7F948A), style = Stroke(1.5f))
                        clipPath(pfade.lid) {
                            drawPath(pfade.adern, Color(0xFF6A2430).copy(alpha = 0.45f), style = Stroke(1f))
                            drawCircle(iris, 56f, Offset(150f, 85f))
                            drawCircle(Color(0xFF24382F), 50f, Offset(150f, 85f), style = Stroke(8f))
                            if (anteil != null && anteil > 0f) {
                                drawArc(glut, -90f, 360f * anteil, false, Offset(100f, 35f), Size(100f, 100f), style = Stroke(8f))
                            }
                            drawOval(Color(0xFF020403), Offset(141f, 51f), Size(18f, 68f))
                            drawCircle(Color.White.copy(alpha = 0.5f), 5f, Offset(166f, 66f))
                        }
                    }
                }
            }
        },
    )
}

/** Pfade aus dem SVG des Entwurfs (Maß 300 × 170 für das Auge, 390 breit für die Tiefe). */
private class AugenPfade {
    val lid: Path = svgPfad("M10 85C60 15 240 15 290 85 240 155 60 155 10 85z")
    val adern: Path = svgPfad("M40 70c30 4 50 10 70 14M260 70c-30 4-50 10-70 14M60 110c24-6 40-10 56-12M240 110c-24-6-40-10-56-12")
}

/** Fangarme aus der Tiefe an beiden Rändern unten und ein paar glimmende Punkte; reine Zierde. */
@Composable
private fun Tiefe(modifier: Modifier) {
    val glut = FoodyTheme.stil.zier
    val arme = remember {
        listOf(
            svgPfad(
                "M0 600c40 6 66 40 60 82-4 30-28 46-48 38-14-6-12-24 2-24 10 0 12 12 4 14 12-4 14-20 0-24" +
                    "-20-6-36 18-22 40 18 26 64 10 70-34 8-50-26-90-66-92z",
            ),
            svgPfad(
                "M390 560c-44 4-74 44-66 90 6 34 34 52 56 40 16-8 12-28-4-26-10 2-10 14-2 16-12-2-12-20 4-24" +
                    " 22-4 36 24 18 46-22 26-68 4-70-46-2-56 38-92 64-96z",
            ),
        )
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.width / 390f
        // Die Arme sitzen im Entwurf zwischen 560 und 720 von 844; hier über der Leiste am unteren Rand
        translate(0f, size.height - 760f * s) {
            scale(s, Offset.Zero) {
                arme.forEach { drawPath(it, Color(0xFF0F1A16)) }
                drawCircle(glut.copy(alpha = 0.35f), 2f, Offset(40f, 660f))
                drawCircle(glut.copy(alpha = 0.35f), 2.5f, Offset(352f, 628f))
                drawCircle(glut.copy(alpha = 0.35f), 1.6f, Offset(368f, 700f))
            }
        }
    }
}

/** Überschrift über dem Ritual je nach Mahlzeit; „· morgen“, wenn es erst morgen ansteht. */
internal fun ritualName(slot: String, morgen: Boolean): String {
    val name = when (Mahlzeit.ausText(slot)) {
        Mahlzeit.FRUEHSTUECK -> "Das Ritual des Morgens"
        Mahlzeit.MITTAGESSEN -> "Das Ritual des Mittags"
        Mahlzeit.SNACK -> "Das kleine Ritual"
        Mahlzeit.ABENDESSEN -> "Das Ritual des Abends"
        null -> slot
    }
    return if (morgen) "$name · morgen" else name
}

/** Ruhig: die Mahlzeit beim Namen („Abendessen“), „· morgen“ wie in Immersiv. */
internal fun mahlzeitKopf(slot: String, morgen: Boolean): String {
    val name = Mahlzeit.ausText(slot)?.anzeigename() ?: slot
    return if (morgen) "$name · morgen" else name
}

/** Die nächste geplante Mahlzeit als Tafel mit doppeltem Rahmen; ohne Plan die Eingebung des Tages. */
@Composable
private fun Ritual(zustand: HeuteZustand, aktionen: HeuteAktionen) {
    val n = zustand.naechste
    val r = zustand.inspiration
    val inszeniert = FoodyTheme.stil.inszeniert
    when {
        n != null -> {
            val m = n.mahlzeit
            Tafel(
                ueber = if (inszeniert) ritualName(m.slot, n.morgen) else mahlzeitKopf(m.slot, n.morgen),
                titel = m.titel,
                zeile = meta(m.portionen, m.kcalJePortion, m.zeitMinuten),
                onTitel = { aktionen.onRezept(m.rezeptId) },
                knopf = szenenWort("Das Ritual beginnen", "Kochen"),
                knopfBeschreibung = "Kochen",
                onKnopf = { aktionen.onKochen(m.rezeptId, m.portionen) },
            )
        }
        r != null -> Tafel(
            ueber = szenenWort("Eine Eingebung", "Vorschlag"),
            titel = r.titel,
            zeile = meta(null, r.kcalJePortion, r.zeitMinuten),
            onTitel = { aktionen.onRezept(r.id) },
            knopf = szenenWort("Das Buch aufschlagen", "Rezept ansehen"),
            knopfBeschreibung = "Rezept ansehen",
            onKnopf = { aktionen.onRezept(r.id) },
        )
    }
}

@Composable
private fun Tafel(ueber: String, titel: String, zeile: String, onTitel: () -> Unit, knopf: String, knopfBeschreibung: String, onKnopf: () -> Unit) {
    val f = FoodyTheme.farben
    // Auf dem Pergament (Ruhig) ist die Glut zu hell für Schrift; dort die lesbare Primärfarbe
    val glut = if (FoodyTheme.stil.inszeniert) FoodyTheme.stil.zier else f.primaer
    Box(Modifier.fillMaxWidth().border(1.dp, f.lichtkante).padding(4.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    if (FoodyTheme.stil.inszeniert) Brush.verticalGradient(TAFEL) else SolidColor(Color.White.copy(alpha = 0.35f)),
                )
                .border(1.dp, f.lichtkante)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(ueber, style = Grauen.titel(15f, 0.06f), color = glut)
            Text(
                "$titel ›",
                Modifier.rezeptLink(titel, onTitel).padding(vertical = 4.dp),
                style = Grauen.name(25f), color = f.text,
            )
            if (zeile.isNotEmpty()) Text(zeile, style = Grauen.maschine(13f), color = f.nebentext)
            GrauenKnopf(knopf, onKnopf, Modifier.fillMaxWidth().padding(top = 6.dp), beschreibung = knopfBeschreibung)
        }
    }
}

/** Kurzname in der Spalte links im Protokoll (wo der Entwurf die Uhrzeit tippt). */
internal fun Mahlzeit.protokollKurz(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Früh"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.SNACK -> "Zw."
    Mahlzeit.ABENDESSEN -> "Abend"
}

/** „Die Zwischenmahlzeit fehlt noch …“ für eine Mahlzeit ohne Eintrag. */
internal fun Mahlzeit.fehltNoch(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Das Frühstück fehlt noch …"
    Mahlzeit.MITTAGESSEN -> "Das Mittagessen fehlt noch …"
    Mahlzeit.SNACK -> "Die Zwischenmahlzeit fehlt noch …"
    Mahlzeit.ABENDESSEN -> "Das Abendessen fehlt noch …"
}

/** „Was bisher dargebracht wurde“: Einträge je Mahlzeit in Schreibmaschine, Geplantes mit „Gegessen?“, Leeres mit „+ notieren“. */
@Composable
private fun Protokoll(t: TagebuchHeute, aktionen: HeuteAktionen) {
    // Geplante Mahlzeit, zu der gerade „Gegessen?“ gefragt wird; übersteht Drehen
    var frage by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        Verweis(szenenWort("Was bisher dargebracht wurde", "Tagebuch") + " ›", "Tagebuch, alle Einträge", groesse = 19f, onKlick = aktionen.onAlleEintraege)
        t.mahlzeiten.forEach { a -> Abschnitt(a, aktionen) { frage = it } }
    }
    val offen = t.mahlzeiten.flatMap { a -> a.vorschlaege.map { a.mahlzeit to it } }.firstOrNull { it.second.planEintragId == frage }
    offen?.let { (mahlzeit, v) -> GegessenFrage(mahlzeit, v, aktionen) { frage = null } }
}

@Composable
private fun Abschnitt(a: MahlzeitAbschnitt, aktionen: HeuteAktionen, onFrage: (String) -> Unit) {
    val name = a.mahlzeit.anzeigename()
    a.eintraege.forEachIndexed { i, e ->
        Zeile(if (i == 0) a.mahlzeit.protokollKurz() else "", beschreibung = "$name: ${e.name}, ${kcalText(e.kcal, e.vollstaendig)}") {
            Text(e.name, Modifier.weight(1f), style = Grauen.maschine(14f), color = FoodyTheme.farben.text)
            Text(kcalText(e.kcal, e.vollstaendig).removeSuffix(" kcal"), style = Grauen.maschine(14f), color = FoodyTheme.farben.text)
        }
    }
    a.vorschlaege.forEach { v -> Vorgesehen(a.mahlzeit, v, onFrage) }
    if (a.eintraege.isEmpty() && a.vorschlaege.isEmpty()) {
        Zeile(a.mahlzeit.protokollKurz(), beschreibung = null, linie = false) {
            val leer = if (FoodyTheme.stil.inszeniert) a.mahlzeit.fehltNoch() else "$name, noch leer"
            Text(leer, Modifier.weight(1f), style = Grauen.text(16f, kursiv = true), color = FoodyTheme.farben.nebentext)
            GrauenNotizknopf("+ notieren", { aktionen.onEintragen(a.mahlzeit) }, beschreibung = "$name eintragen")
        }
    }
}

@Composable
private fun Vorgesehen(mahlzeit: Mahlzeit, v: GeplanteMahlzeit, onFrage: (String) -> Unit) {
    Zeile(mahlzeit.protokollKurz(), beschreibung = null, linie = false) {
        val satz = szenenWort("„${v.titel}“ ist vorgesehen …", "${v.titel}, geplant")
        Text(satz, Modifier.weight(1f), style = Grauen.text(16f, kursiv = true), color = FoodyTheme.farben.nebentext)
        GrauenNotizknopf("Gegessen?", { onFrage(v.planEintragId) }, beschreibung = "${mahlzeit.anzeigename()} ${v.titel}: gegessen?")
    }
}

@Composable
private fun Zeile(kurz: String, beschreibung: String?, linie: Boolean = true, inhalt: @Composable RowScope.() -> Unit) {
    val f = FoodyTheme.farben
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .then(if (beschreibung != null) Modifier.clearAndSetSemantics { contentDescription = beschreibung } else Modifier)
            .then(
                if (linie) {
                    Modifier.drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kurz, Modifier.widthIn(min = 52.dp), style = Grauen.maschine(14f), color = f.nebentext)
        inhalt()
    }
}

/** Unterstrichener Verweis in Buchschrift (Protokoll, Expeditionsliste). */
@Composable
private fun Verweis(text: String, beschreibung: String, groesse: Float = 17f, onKlick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = beschreibung }.padding(vertical = 10.dp),
        style = Grauen.titel(groesse, 0.04f), color = FoodyTheme.farben.text,
    )
}

/** Verlauf der Tafel in Immersiv. */
private val TAFEL = listOf(Color(0xFF121B18), Color(0xFF0C1210))

private const val AUGE_BREITE = 300f
private const val AUGE_HOEHE = 170f
