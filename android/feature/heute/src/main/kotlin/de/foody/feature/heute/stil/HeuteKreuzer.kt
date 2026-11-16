package de.foody.feature.heute.stil

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
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
import de.foody.core.designsystem.komponenten.balkenanteile
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalStand
import de.foody.core.designsystem.komponenten.kcalZahl
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.stil.kreuzer.KreuzerVerweis
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.domain.zielAnteil
import de.foody.feature.heute.DatenbankLeerHinweis
import de.foody.feature.heute.GegessenFrage
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteRahmen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.gericht
import de.foody.feature.heute.kcalZahl
import de.foody.feature.heute.meta
import de.foody.feature.heute.rezeptLink
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * „Heute“ des Sternenkreuzers in Immersiv (Entwurf `V-Heute`): das „Tagessystem“ – die Sonne ist das Tagesziel, jede
 * Mahlzeit ein Planet auf ihrer Bahn (gelandet = gegessen, gestrichelt in Amber = geplant, grau = frei). Antippen
 * wählt einen Planeten, darunter stehen seine Daten. Rechts der Treibstoff (kcal gegen das Ziel), darunter Makros und
 * die nächste Mission. Bahn und Korona ruhen ohne Animationen. TalkBack hört die normalen Begriffe.
 */
@Composable
internal fun HeuteKreuzer(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    HeuteRahmen(modifier, innen = PaddingValues(start = 18.dp, end = 6.dp, top = 12.dp)) { rahmen ->
        Column(rahmen, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kopf(zustand, aktionen.onEinstellungen)
            if (zustand == null) return@Column
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (zustand.datenbankLeer) DatenbankLeerHinweis(aktionen.onEinstellungen)
                val planeten = planeten(zustand.tagebuch)
                var wahl by rememberSaveable { mutableIntStateOf(startWahl(planeten)) }
                Tagessystem(zustand.tagebuch, planeten, wahl.coerceIn(planeten.indices)) { wahl = it }
                planeten.getOrNull(wahl)?.let { Planetendaten(it, aktionen) }
                Makros(zustand.tagebuch)
                Mission(zustand, aktionen)
                zustand.einkauf?.let { e ->
                    KreuzerVerweis("FRACHTRAUM · ${e.anzahl} OFFEN ›", { aktionen.onEinkauf(e.listeId) }, beschreibung = "Einkauf, ${e.anzahl} offen")
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                zustand?.datum?.let { Text(logbuchZeile(it), style = Kreuzer.mono(12.5f, 0.08f), color = f.nebentext) }
                // Titel und Anteil am Ziel nebeneinander; bei großer Schrift rückt der Anteil unter den Titel
                FlowRow(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Tagessystem",
                        Modifier.align(Alignment.Bottom).padding(end = 8.dp).semantics {
                            heading()
                            contentDescription = "Heute"
                        },
                        style = Kreuzer.titel(28f), color = f.text, maxLines = 1,
                    )
                    zustand?.let { z ->
                        Text(
                            kreuzerStatus(z.tagebuch.summe.kcal, z.tagebuch.ziel, z.tagebuch.summe.vollstaendig),
                            Modifier.align(Alignment.Bottom).clearAndSetSemantics { },
                            style = Kreuzer.mono(13f, 0.04f), color = FoodyTheme.stil.zier,
                        )
                    }
                }
            }
            IconButton(onEinstellungen) { Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen", tint = f.nebentext) }
        }
        zustand?.sync?.let { SyncStatus(it) }
    }
}

/** „SA 24.10. · LOGBUCH 297“: Wochentag, Datum und der Tag im Jahr als Logbuchnummer. */
internal fun logbuchZeile(d: LocalDate): String {
    val tag = wochentagKurz(d).uppercase(Locale.GERMAN)
    return "$tag ${d.dayOfMonth}.${d.monthValue}. · LOGBUCH ${d.dayOfYear}"
}

/** Anzeige oben rechts: Anteil am Ziel, ohne Ziel die Summe. */
internal fun kreuzerStatus(kcal: Int, ziel: Int?, vollstaendig: Boolean = true): String {
    val mindestens = if (vollstaendig) "" else "≥ "
    return when {
        ziel == null || ziel <= 0 -> "${kcalZahl(kcal, vollstaendig)} KCAL"
        kcal > ziel -> "$mindestens${kcal * 100 / ziel} % · ÜBER ZIEL"
        else -> "$mindestens${kcal * 100 / ziel} % ERREICHT"
    }
}

/** Zustand eines Planeten: gegessen (gelandet), geplant (Kurs gesetzt) oder frei. */
internal enum class Planetenart { GELANDET, KURS, FREI }

/** Eine Mahlzeit als Planet auf Bahn [bahn] (1 innen … 4 außen). */
internal data class Planet(
    val mahlzeit: Mahlzeit,
    val bahn: Int,
    val art: Planetenart,
    val gericht: String,
    val info: String,
    /** Der Vorschlag aus dem Plan, wenn der Planet „Kurs gesetzt“ hat. */
    val vorschlag: GeplanteMahlzeit?,
)

/** Bahn und Name je Mahlzeit wie im Entwurf: Frühstück innen, Snack ganz außen. */
private fun Mahlzeit.bahn(): Int = when (this) {
    Mahlzeit.FRUEHSTUECK -> 1
    Mahlzeit.MITTAGESSEN -> 2
    Mahlzeit.ABENDESSEN -> 3
    Mahlzeit.SNACK -> 4
}

internal fun Mahlzeit.kreuzerName(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.ABENDESSEN -> "Abend"
    Mahlzeit.SNACK -> "Snack"
}

/** Planeten aus dem Tagebuch: Einträge machen ihn „gelandet“, sonst ein Vorschlag „Kurs gesetzt“, sonst „frei“. */
internal fun planeten(t: TagebuchHeute): List<Planet> = t.mahlzeiten.map { a ->
    val v = a.vorschlaege.firstOrNull()
    when {
        a.eintraege.isNotEmpty() -> {
            Planet(
                a.mahlzeit, a.mahlzeit.bahn(), Planetenart.GELANDET, a.eintraege.joinToString(", ") { it.name },
                "${a.kcalZahl()} kcal · gelandet", null,
            )
        }
        v != null -> Planet(
            a.mahlzeit, a.mahlzeit.bahn(), Planetenart.KURS, v.titel,
            listOfNotNull(v.kcalJePortion?.let { "${ganzzahlDeutsch(it)} kcal" }, "Kurs gesetzt").joinToString(" · "), v,
        )
        else -> Planet(a.mahlzeit, a.mahlzeit.bahn(), Planetenart.FREI, "Kein Kontakt", "freier Orbit", null)
    }
}.sortedBy { it.bahn }

/** Zu Beginn gewählt: der erste geplante Planet, sonst der erste freie, sonst der innerste. */
internal fun startWahl(p: List<Planet>): Int =
    p.indexOfFirst { it.art == Planetenart.KURS }.takeIf { it >= 0 } ?: p.indexOfFirst { it.art == Planetenart.FREI }.takeIf { it >= 0 } ?: 0

/** Wie TalkBack einen Planeten liest: Mahlzeit, Gericht und Zustand in normalen Worten. */
internal fun planetBeschreibung(p: Planet): String = "${p.mahlzeit.anzeigename()}: " + when (p.art) {
    Planetenart.GELANDET -> "${p.gericht}, gegessen"
    Planetenart.KURS -> "${p.gericht}, geplant"
    Planetenart.FREI -> "noch nichts eingetragen"
}

/** Die Sonne mit den Bahnen und die Planeten links, der Treibstoff rechts. */
@Composable
private fun Tagessystem(t: TagebuchHeute, planeten: List<Planet>, wahl: Int, onWahl: (Int) -> Unit) {
    val skala = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val treibstoffBreite = 44.dp * max(1f, skala)
        val breite = (maxWidth - treibstoffBreite - 12.dp).coerceAtMost(320.dp)
        val hoehe = breite * (SYSTEM_HOEHE / SYSTEM_BREITE)
        Row(Modifier.height(hoehe), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Sonnensystem(t.ziel, planeten, wahl, onWahl, Modifier.width(breite).fillMaxHeight())
            Treibstoff(t.summe.kcal, t.ziel, t.summe.vollstaendig, Modifier.width(treibstoffBreite).fillMaxHeight())
        }
    }
}

@Composable
private fun Sonnensystem(ziel: Int?, planeten: List<Planet>, wahl: Int, onWahl: (Int) -> Unit, modifier: Modifier) {
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val bewegt = animationenErlaubt()
    val zeit = rememberInfiniteTransition(label = "System")
    val drehung by if (bewegt) {
        zeit.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "Bahn")
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val korona by if (bewegt) {
        zeit.animateFloat(0.55f, 0.9f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "Korona")
    } else {
        remember { mutableFloatStateOf(0.7f) }
    }
    val messer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val s = maxWidth.value / SYSTEM_BREITE
        // Sterne, feste Bahnen, Sonne und Schrift entstehen einmal je Größe. Die gestrichelte Bahn dreht sich als eigene
        // Ebene und die Korona pulsiert über die Deckkraft ihrer Ebene – beides ohne neu zu zeichnen.
        val bahn = holo.copy(alpha = 0.28f)
        Box(
            Modifier.matchParentSize().clearAndSetSemantics { }.drawWithCache {
                val px = size.width / SYSTEM_BREITE
                val mitte = Offset(140f * px, 135f * px)
                val linie = Stroke(1.dp.toPx())
                val aussen = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * px, 6f * px)))
                onDrawBehind {
                    STERNE.forEach { drawCircle(Color.White.copy(alpha = 0.5f), 0.8f * px, Offset(it.x * px, it.y * px)) }
                    drawCircle(bahn, 46f * px, mitte, style = linie)
                    drawCircle(bahn, 72f * px, mitte, style = linie)
                    drawCircle(bahn, 122f * px, mitte, style = aussen)
                }
            },
        ) {
            Spacer(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        rotationZ = drehung
                        transformOrigin = TransformOrigin(140f / SYSTEM_BREITE, 135f * size.width / SYSTEM_BREITE / size.height)
                    }
                    .drawWithCache {
                        val px = size.width / SYSTEM_BREITE
                        val strich = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * px, 5f * px)))
                        onDrawBehind { drawCircle(bahn, 98f * px, Offset(140f * px, 135f * px), style = strich) }
                    },
            )
            Spacer(
                Modifier.matchParentSize().graphicsLayer { alpha = korona }.drawWithCache {
                    val px = size.width / SYSTEM_BREITE
                    onDrawBehind { drawCircle(amber.copy(alpha = 0.25f), 34f * px, Offset(140f * px, 135f * px)) }
                },
            )
            Spacer(
                Modifier.matchParentSize().drawWithCache {
                    val px = size.width / SYSTEM_BREITE
                    val mitte = Offset(140f * px, 135f * px)
                    // Beschriftung der Sonne in fester Größe: Zierde, die Zahl liest TalkBack am Treibstoff
                    val oben = messer.measure(
                        "ZIEL",
                        Kreuzer.mono(11f, 0f).copy(fontSize = (11f * px).toSp(), lineHeight = TextUnit.Unspecified, color = Kreuzer.aufAmber),
                    )
                    val zahl = messer.measure(
                        ziel?.let { ganzzahlDeutsch(it) } ?: "–",
                        Kreuzer.titel(12f, abstand = 0f).copy(fontSize = (12f * px).toSp(), lineHeight = TextUnit.Unspecified, color = Kreuzer.aufAmber),
                    )
                    onDrawBehind {
                        drawCircle(amber, 24f * px, mitte)
                        drawText(oben, topLeft = Offset(mitte.x - oben.size.width / 2f, mitte.y - 2f * px - oben.size.height))
                        drawText(zahl, topLeft = Offset(mitte.x - zahl.size.width / 2f, mitte.y))
                    }
                },
            )
        }
        Box(Modifier.matchParentSize().selectableGroup()) {
            planeten.forEachIndexed { i, p ->
                val (x, y, groesse) = PLANETENORT.getValue(p.bahn)
                Planetenknopf(
                    p, groesse, i == wahl, { onWahl(i) },
                    Modifier.offset { IntOffset(((x * s - 22f).dp).roundToPx(), ((y * s - 22f).dp).roundToPx()) },
                )
            }
        }
    }
}

@Composable
private fun Planetenknopf(p: Planet, groesse: Float, an: Boolean, onKlick: () -> Unit, modifier: Modifier) {
    val holo = FoodyTheme.stil.zier
    val amber = kreuzerAmber()
    val grau = FoodyTheme.farben.nebentext
    Canvas(
        modifier.size(44.dp)
            .selectable(an, role = Role.Tab, onClick = onKlick)
            .semantics { contentDescription = planetBeschreibung(p) },
    ) {
        val r = groesse / 2f * density
        val m = center
        when (p.art) {
            Planetenart.GELANDET -> {
                drawCircle(holo.copy(alpha = 0.25f), r + 5.dp.toPx(), m)
                drawCircle(holo, r, m)
            }
            Planetenart.KURS -> drawCircle(amber, r, m, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
            Planetenart.FREI -> drawCircle(grau, r, m, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
        }
        if (an) drawCircle(amber, r + 5.5.dp.toPx(), m, style = Stroke(1.dp.toPx()))
    }
}

/** Senkrechte Anzeige „Treibstoff“: kcal gegen das Ziel, mit Strichen alle 24 dp. */
@Composable
private fun Treibstoff(kcal: Int, ziel: Int?, vollstaendig: Boolean, modifier: Modifier) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val anteil = zielAnteil(kcal, ziel) ?: 0f
    val vorlesen = kcalStand(kcal, ziel, vollstaendig)
    Column(
        modifier.clearAndSetSemantics { contentDescription = vorlesen },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(kcalZahl(kcal, vollstaendig), style = Kreuzer.mono(13f, 0f), color = holo, maxLines = 1)
        Box(
            Modifier.width(22.dp).weight(1f).border(1.dp, f.lichtkante).drawBehind {
                val schritt = 24.dp.toPx()
                var y = size.height - schritt
                while (y > 0f) {
                    drawLine(f.lichtkante, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y -= schritt
                }
                val rand = 2.dp.toPx()
                val h = (size.height - 2 * rand) * anteil
                if (h > 0f) {
                    drawRect(
                        Brush.verticalGradient(listOf(holo.copy(alpha = 0.35f), holo), startY = size.height - rand - h, endY = size.height - rand),
                        Offset(rand, size.height - rand - h), Size(size.width - 2 * rand, h),
                    )
                }
            },
        )
        Text("TREIB-\nSTOFF", style = Kreuzer.mono(10.5f, 0.08f), color = f.nebentext, textAlign = TextAlign.Center)
    }
}

/** Daten des gewählten Planeten unter dem System, mit dem passenden Weg weiter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Planetendaten(p: Planet, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val amber = kreuzerAmber()
    var frage by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .drawBehind { drawLine(amber, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "ORBIT ${p.bahn} · ${p.mahlzeit.kreuzerName().uppercase(Locale.GERMAN)}",
            Modifier.semantics { contentDescription = p.mahlzeit.anzeigename() },
            style = Kreuzer.mono(12f, 0.08f), color = amber,
        )
        Text(p.gericht, style = Kreuzer.titel(17f, FontWeight.Bold, 0f), color = f.text)
        Text(p.info, style = Kreuzer.mono(12.5f, 0f), color = f.nebentext)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (p.art) {
                Planetenart.GELANDET -> KreuzerVerweis("BORDBUCH ÖFFNEN ›", aktionen.onAlleEintraege, beschreibung = "Tagebuch öffnen")
                Planetenart.KURS -> {
                    KreuzerVerweis("SYSTEMDATEN ›", { aktionen.onRezept(requireNotNull(p.vorschlag).rezeptId) }, beschreibung = "Rezept ansehen")
                    KreuzerVerweis("GELANDET?", { frage = true }, beschreibung = "Gegessen?")
                }
                Planetenart.FREI -> KreuzerVerweis(
                    "+ PROVIANT ERFASSEN", { aktionen.onEintragen(p.mahlzeit) }, beschreibung = "${p.mahlzeit.anzeigename()} eintragen",
                )
            }
        }
    }
    val v = p.vorschlag
    if (frage && v != null) GegessenFrage(p.mahlzeit, v, aktionen) { frage = false }
}

/** Eiweiß, Kohlenhydrate und Fett nebeneinander mit dünnem Balken. */
@Composable
private fun Makros(t: TagebuchHeute) {
    val f = FoodyTheme.farben
    val s = t.summe
    val a = balkenanteile(s.kcal, null, s.eiweiss, s.kohlenhydrate, s.fett)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("Eiweiß", s.eiweiss to a.eiweiss, f.makro.eiweiss),
            Triple("KH", s.kohlenhydrate to a.kh, f.makro.kohlenhydrate),
            Triple("Fett", s.fett to a.fett, f.makro.fett),
        ).forEach { (name, werte, farbe) ->
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "${if (name == "KH") "Kohlenhydrate" else name} ${werte.first} g"
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row {
                    Text(name, Modifier.weight(1f), style = Kreuzer.mono(12f, 0f), color = f.nebentext)
                    Text("${werte.first} g", style = Kreuzer.mono(12f, 0f), color = f.text)
                }
                Box(
                    Modifier.fillMaxWidth().height(4.dp).background(f.lichtkante).drawBehind {
                        drawRect(farbe, size = Size(size.width * werte.second.coerceIn(0f, 1f), size.height))
                    },
                )
            }
        }
    }
}

/** Die nächste Mission (geplante Mahlzeit) in der Karte mit abgeschnittenen Ecken; ohne Plan ein Vorschlag. */
@Composable
private fun Mission(zustand: HeuteZustand, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val amber = kreuzerAmber()
    val form = Kreuzer.ecke()
    val n = zustand.naechste
    val r = zustand.inspiration
    Column(
        Modifier.fillMaxWidth().clip(form)
            .background(Brush.linearGradient(listOf(amber.copy(alpha = 0.10f), amber.copy(alpha = 0.02f))))
            .border(1.dp, amber.copy(alpha = 0.45f), form)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val kopf = when {
            n != null -> if (n.morgen) "NÄCHSTE MISSION · MORGEN" else "NÄCHSTE MISSION"
            r != null -> "MISSIONSVORSCHLAG"
            else -> "KEINE MISSION GEPLANT"
        }
        Text(kopf, style = Kreuzer.mono(12f, 0.1f), color = amber)
        val g = zustand.gericht()
        if (g != null) {
            val (titel, rezeptId) = g
            Text(
                "$titel ›",
                Modifier.rezeptLink(titel) { aktionen.onRezept(rezeptId) }.padding(vertical = 4.dp),
                style = Kreuzer.titel(20f, FontWeight.Bold, 0f), color = f.text,
            )
            val zeile = g.zeile
            if (!zeile.isNullOrEmpty()) Text(zeile, style = Kreuzer.mono(13f, 0f), color = f.nebentext)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                n != null -> KreuzerKnopf(
                    "Mission starten", { aktionen.onKochen(n.mahlzeit.rezeptId, n.mahlzeit.portionen) }, Modifier.weight(1.4f), beschreibung = "Kochen",
                )
                r != null -> KreuzerKnopf("Daten ansehen", { aktionen.onRezept(r.id) }, Modifier.weight(1.4f), beschreibung = "Rezept ansehen")
            }
            KreuzerKnopf("+ Eintragen", { aktionen.onEintragen(null) }, Modifier.weight(1f), voll = n == null && r == null)
        }
    }
    Spacer(Modifier.height(2.dp))
}

private const val SYSTEM_BREITE = 280f
private const val SYSTEM_HOEHE = 270f

/** Sterne um das Sonnensystem im Maß des Entwurfs. */
private val STERNE = listOf(Offset(12f, 20f), Offset(260f, 40f), Offset(30f, 240f), Offset(250f, 250f), Offset(200f, 12f))

/** Ort (Mitte) und Größe der Planeten je Bahn im Maß des Entwurfs. */
private val PLANETENORT = mapOf(1 to Triple(170f, 100f, 14f), 2 to Triple(78f, 171f, 16f), 3 to Triple(229f, 176f, 18f), 4 to Triple(79f, 29f, 9f))
