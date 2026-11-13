package de.foody.feature.heute.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.meta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val DATUM = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * „Heute“ des Bayrischen Stils in Immersiv (Entwurf `T-Maibaum`): links der Maibaum – die Kalorien klettern am
 * weiß-blauen Stamm hinauf zum Kranz (dem Tagesziel), die Mahlzeiten hängen als Schilder daran (Friahstück unten,
 * Brotzeit oben; gegessen = weiß, offen = gestrichelt). Rechts „Griaß di!“, „Heid“, das nächste Essen mit „Pack
 * ma's!“, die Bilanz mit den Nährwerten und „+ … eitragn“. Hinten Himmel, Berge und Kirchturm. TalkBack hört die
 * normalen Begriffe.
 */
@Composable
internal fun HeuteBayrisch(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    val k = LocalDensity.current.fontScale
    Box(modifier.fillMaxSize()) {
        Landschaft(Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .statusBarsPadding()
                    .padding(bottom = ueberLeiste(PLATZ_FUER_LEISTE))
                    .testTag(HEUTE_INHALT),
            ) {
                // Bei großer Schrift wird der Baum etwas breiter, damit die Schilder lesbar bleiben
                zustand?.let { Maibaum(it.tagebuch, Modifier.padding(start = 14.dp).width(104.dp * min(max(k, 1f), 1.5f))) }
                Column(
                    Modifier.weight(1f).padding(start = 14.dp, end = 18.dp, top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Kopf(zustand, aktionen.onEinstellungen)
                    if (zustand == null) return@Column
                    if (zustand.datenbankLeer) {
                        LeererZustandFlaeche(
                            Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
                            aktionText = "Server verbinden", onAktion = aktionen.onEinstellungen,
                        )
                    }
                    Essen(zustand, aktionen)
                    Bilanz(zustand.tagebuch, aktionen.onAlleEintraege)
                    Eintragen(zustand.tagebuch, aktionen)
                    zustand.einkauf?.let { e ->
                        Verweis("Eikaufszettl · ${e.anzahl} offen ›", "Einkauf, ${e.anzahl} offen") { aktionen.onEinkauf(e.listeId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Kopf(zustand: HeuteZustand?, onEinstellungen: () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(end = 48.dp)) {
            Text("Griaß di!", style = Bayrisch.titel(16f), color = bayrischBlau())
            // Der große Titel wächst mit der Systemschrift nur bis 130 %, wie große Schrift unter Android 14
            Text(
                "Heid",
                Modifier.semantics {
                    heading()
                    contentDescription = "Heute"
                },
                style = Bayrisch.fraktur(54f * min(k, 1.3f) / k), color = f.text, maxLines = 1,
            )
            zustand?.datum?.let { Text(DATUM.format(it), style = Bayrisch.text(14.5f, FontWeight.Medium), color = f.text) }
            zustand?.sync?.let { SyncStatus(it, Modifier.padding(top = 4.dp)) }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 0.dp).size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onEinstellungen)
                .semantics { contentDescription = "Einstellungen" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.75f), CircleShape).border(2.dp, f.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = f.text)
            }
        }
    }
}

/** Mahlzeit auf Bairisch, wie sie an den Schildern steht. */
internal fun Mahlzeit.bairisch(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Friahstück"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.ABENDESSEN -> "Abend"
    Mahlzeit.SNACK -> "Brotzeit"
}

/** Überschrift der Karte mit dem nächsten Essen: „Heid auf d'Nacht“, morgen „Morgn auf d'Nacht“. */
internal fun essenKopf(slot: String, morgen: Boolean): String {
    val tag = if (morgen) "Morgn" else "Heid"
    return when (Mahlzeit.ausText(slot)) {
        Mahlzeit.FRUEHSTUECK -> "$tag in da Friah"
        Mahlzeit.MITTAGESSEN -> "$tag z'Mittag"
        Mahlzeit.ABENDESSEN -> "$tag auf d'Nacht"
        Mahlzeit.SNACK -> "$tag zur Brotzeit"
        null -> "$tag · $slot"
    }
}

/** Bilanz in einem Satz: „Passt scho – no 1.140 frei“, über dem Ziel „Obacht – 300 drüber“. */
internal fun bilanzSatz(kcal: Int, ziel: Int?): String = when {
    ziel == null || ziel <= 0 -> "${ganzzahlDeutsch(kcal)} kcal heid"
    kcal <= ziel -> "Passt scho – no ${ganzzahlDeutsch(ziel - kcal)} frei"
    else -> "Obacht – ${ganzzahlDeutsch(kcal - ziel)} drüber"
}

/** Text auf dem Schild einer Mahlzeit: Gericht und kcal, offen „no nix“. */
internal fun schildText(a: MahlzeitAbschnitt): String {
    val v = a.vorschlaege.firstOrNull()
    return when {
        a.eintraege.isNotEmpty() -> a.eintraege.joinToString(", ") { it.name } + " · " + ganzzahlDeutsch(a.eintraege.sumOf { it.kcal ?: 0 })
        v != null -> v.titel + (v.kcalJePortion?.let { " · ${ganzzahlDeutsch(it)}" } ?: "")
        else -> "no nix"
    }
}

/** Was TalkBack über den Maibaum hört: die Bilanz und jede Mahlzeit in normalen Worten. */
internal fun maibaumBeschreibung(t: TagebuchHeute): String {
    val ziel = t.ziel?.takeIf { it > 0 }
    val kopf = if (ziel != null) "${ganzzahlDeutsch(t.summe.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(t.summe.kcal)} kcal"
    val mahlzeiten = t.mahlzeiten.joinToString(". ") { a ->
        val v = a.vorschlaege.firstOrNull()
        "${a.mahlzeit.anzeigename()}: " + when {
            a.eintraege.isNotEmpty() -> a.eintraege.joinToString(", ") { it.name } + ", ${ganzzahlDeutsch(a.eintraege.sumOf { it.kcal ?: 0 })} kcal"
            v != null -> "${v.titel}, geplant"
            else -> "noch nichts"
        }
    }
    return "$kopf. $mahlzeiten"
}

/** Wo die Schilder hängen (Mitte, Anteil der Baumhöhe) wie im Entwurf: Friahstück unten, Brotzeit oben. */
private fun Mahlzeit.hoehe(): Float = when (this) {
    Mahlzeit.FRUEHSTUECK -> 0.855f
    Mahlzeit.MITTAGESSEN -> 0.725f
    Mahlzeit.ABENDESSEN -> 0.415f
    Mahlzeit.SNACK -> 0.28f
}

/**
 * Der Maibaum: Stamm mit Spiralstreifen im Akzent, Kranz und Fahne oben, „Ziel 2.000“ darunter, der Stand der
 * kcal als Ring mit Schild am Stamm und die Schilder der Mahlzeiten. Die Schilder hängen auf der Höhe des Entwurfs;
 * würden sie sich bei großer Schrift überdecken, rücken sie auseinander und der Baum wird höher.
 */
@Composable
private fun Maibaum(t: TagebuchHeute, modifier: Modifier) {
    val f = FoodyTheme.farben
    val blau = bayrischBlau()
    val gruen = Color(0xFF4E7A3A)
    val ziel = t.ziel?.takeIf { it > 0 }
    val stand = ziel?.let { (t.summe.kcal.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val abschnitte = t.mahlzeiten.sortedBy { it.mahlzeit.hoehe() }
    Layout(
        content = {
            Text(
                ziel?.let { "Ziel ${ganzzahlDeutsch(it)}" } ?: "Ziel –",
                Modifier.background(Color(0xFFFBF6EA).copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                style = Bayrisch.titel(12f, FontWeight.ExtraBold), color = f.text, textAlign = TextAlign.Center,
            )
            abschnitte.forEach { Schild(it, blau) }
            // Stand der kcal: Schild über dem Ring am Stamm
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${ganzzahlDeutsch(t.summe.kcal)} kcal",
                    Modifier.fillMaxWidth().background(Color(0xFFFBF6EA), RoundedCornerShape(6.dp)).border(1.5.dp, f.text, RoundedCornerShape(6.dp))
                        .padding(vertical = 1.dp),
                    style = Bayrisch.titel(15f, FontWeight.ExtraBold), color = f.text, textAlign = TextAlign.Center,
                )
                Box(
                    Modifier.padding(top = 6.dp).width(56.dp).heightIn(min = 16.dp)
                        .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
                        .border(4.dp, gruen, RoundedCornerShape(99.dp)),
                )
            }
        },
        modifier = modifier
            .clearAndSetSemantics { contentDescription = "Maibaum: " + maibaumBeschreibung(t) }
            .drawBehind { stamm(blau, gruen, f.text) },
    ) { messbare, grenzen ->
        val breite = grenzen.maxWidth
        val teile = messbare.mapIndexed { i, m ->
            val rand = if (i in 1..abschnitte.size) 2.dp.roundToPx() else 0
            m.measure(Constraints(minWidth = breite - 2 * rand, maxWidth = breite - 2 * rand))
        }
        var hoehe = BAUM_HOEHE.dp.roundToPx()
        val luecke = 6.dp.roundToPx()
        val stammUnten = hoehe - 18.dp.roundToPx()
        val stammOben = 64.dp.roundToPx()
        val zielOben = 78.dp.roundToPx()
        // Mitten der Schilder (Anteil der Höhe) und des Stands (am Stamm von unten nach oben)
        val mitten = abschnitte.map { (it.mahlzeit.hoehe() * hoehe).toInt() } + (stammUnten - stand * (stammUnten - stammOben)).toInt()
        val reihenfolge = mitten.indices.sortedByDescending { mitten[it] }
        val oben = IntArray(mitten.size)
        var grenzeUnten = stammUnten
        reihenfolge.forEach { j ->
            val h = teile[j + 1].height
            val wunsch = mitten[j] - h / 2
            oben[j] = min(wunsch, grenzeUnten - h)
            grenzeUnten = oben[j] - luecke
        }
        // Reicht der Platz unter „Ziel …“ nicht, rückt alles nach unten und der Baum wird höher
        val frei = zielOben + teile[0].height + luecke
        val versatz = max(0, frei - (oben.minOrNull() ?: frei))
        hoehe += versatz
        layout(breite, hoehe) {
            teile[0].place((breite - teile[0].width) / 2, zielOben)
            oben.forEachIndexed { j, y ->
                val p = teile[j + 1]
                p.place((breite - p.width) / 2, y + versatz)
            }
        }
    }
}

/** Stamm, Kranz und Fahne – gezeichnet über die ganze Höhe des Baums. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.stamm(blau: Color, gruen: Color, dunkel: Color) {
    val mitte = size.width / 2f
    val dp = density
    val oben = 64f * dp
    val unten = size.height - 18f * dp
    val links = mitte - 7f * dp
    val breite = 14f * dp
    // Spiralstreifen: Blau und Weiß im Wechsel, schräg
    drawRect(Color.White, Offset(links, oben), Size(breite, unten - oben))
    clipRect(links, oben, links + breite, unten) {
        var y = oben - 20f * dp
        while (y < unten + 20f * dp) {
            drawLine(blau, Offset(links - 4f * dp, y + 10f * dp), Offset(links + breite + 4f * dp, y), 10f * dp * 0.82f)
            y += 20f * dp
        }
    }
    drawRect(dunkel, Offset(links, oben), Size(breite, unten - oben), style = Stroke(1.5f * dp))
    // Spitze, Fahne, Kranz
    drawRect(dunkel, Offset(mitte - 2f * dp, 6f * dp), Size(4f * dp, 30f * dp))
    val fahne = Offset(mitte + 2f * dp, 8f * dp)
    for (i in 0 until 4) {
        drawRect(if (i % 2 == 0) blau else Color.White, Offset(fahne.x + i * 5.5f * dp, fahne.y), Size(5.5f * dp, 14f * dp))
    }
    drawRect(dunkel, fahne, Size(22f * dp, 14f * dp), style = Stroke(1f * dp))
    drawCircle(gruen, 19f * dp, Offset(mitte, 52f * dp), style = Stroke(6f * dp))
}

@Composable
private fun Schild(a: MahlzeitAbschnitt, blau: Color) {
    val f = FoodyTheme.farben
    val gegessen = a.eintraege.isNotEmpty()
    val form = RoundedCornerShape(4.dp)
    Column(
        Modifier.fillMaxWidth().clip(form)
            .background(if (gegessen) Color.White else Color.White.copy(alpha = if (a.vorschlaege.isNotEmpty()) 0.75f else 0.6f))
            .then(
                if (gegessen) {
                    Modifier.border(1.5.dp, f.text, form)
                } else {
                    Modifier.drawBehind {
                        drawRoundRect(
                            f.text, cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                        )
                    }
                },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(a.mahlzeit.bairisch(), style = Bayrisch.titel(13f, FontWeight.ExtraBold), color = blau, textAlign = TextAlign.Center)
        Text(schildText(a), style = Bayrisch.text(12f), color = f.text, textAlign = TextAlign.Center)
    }
}

/** Das nächste Essen in der weißen Karte mit doppeltem Rahmen; ohne Plan ein Vorschlag. */
@Composable
private fun Essen(zustand: HeuteZustand, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val n = zustand.naechste
    val r = zustand.inspiration
    if (n == null && r == null) return
    val kopf = n?.let { essenKopf(it.mahlzeit.slot, it.morgen) } ?: "A Vorschlag"
    val titel = n?.mahlzeit?.titel ?: requireNotNull(r).titel
    val rezeptId = n?.mahlzeit?.rezeptId ?: requireNotNull(r).id
    val zeile = n?.mahlzeit?.let { meta(it.portionen, it.kcalJePortion, it.zeitMinuten) } ?: r?.let { meta(null, it.kcalJePortion, it.zeitMinuten) }.orEmpty()
    Box(Modifier.fillMaxWidth().border(1.dp, f.text, RoundedCornerShape(17.dp)).padding(3.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(Bayrisch.kartenForm).background(Color.White).border(2.dp, f.text, Bayrisch.kartenForm)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(kopf, style = Bayrisch.titel(14f), color = bayrischBlau())
            Text(
                "$titel ›",
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { aktionen.onRezept(rezeptId) }
                    .semantics { contentDescription = "$titel, Rezept öffnen" }.padding(vertical = 4.dp),
                style = Bayrisch.titel(21f, FontWeight.ExtraBold), color = f.text,
            )
            if (zeile.isNotEmpty()) Text(zeile, style = Bayrisch.text(14f), color = f.nebentext)
            if (n != null) {
                val m = n.mahlzeit
                val breit = Modifier.fillMaxWidth().padding(top = 4.dp)
                BayrischKnopf("Pack ma's!", { aktionen.onKochen(m.rezeptId, m.portionen) }, breit, beschreibung = "Kochen")
            } else {
                BayrischKnopf("Oschaugn", { aktionen.onRezept(rezeptId) }, Modifier.fillMaxWidth().padding(top = 4.dp), beschreibung = "Rezept ansehen")
            }
        }
    }
}

/** Bilanz mit Verweis aufs Tagebuch und den Nährwerten als Rauten. */
@Composable
private fun Bilanz(t: TagebuchHeute, onTagebuch: () -> Unit) {
    val f = FoodyTheme.farben
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val vorlesen = "Tagebuch, " + if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal"
        Verweis("${bilanzSatz(s.kcal, ziel)} ›", vorlesen, onTagebuch)
        listOf(
            Triple("Eiweiß", s.eiweiss, f.makro.eiweiss),
            Triple("Kohlenhydrate", s.kohlenhydrate, f.makro.kohlenhydrate),
            Triple("Fett", s.fett, f.makro.fett),
        ).forEach { (name, gramm, farbe) ->
            Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).rotate(45f).background(farbe))
                Text(name, Modifier.weight(1f).padding(start = 8.dp), style = Bayrisch.text(14f), color = f.text)
                Text("$gramm g", style = Bayrisch.text(14f, FontWeight.Bold), color = f.text)
            }
        }
    }
}

/** „+ Brotzeit eitragn“ für die erste leere Mahlzeit, daneben Scannen oder Foto. */
@Composable
private fun Eintragen(t: TagebuchHeute, aktionen: HeuteAktionen) {
    val leer = t.mahlzeiten.firstOrNull { it.eintraege.isEmpty() && it.vorschlaege.isEmpty() }?.mahlzeit
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BayrischKnopf(
            leer?.let { "+ ${it.bairischLang()} eitragn" } ?: "+ Eitragn",
            { aktionen.onEintragen(leer) },
            Modifier.weight(1f),
            voll = false,
            beschreibung = leer?.let { "${it.anzeigename()} eintragen" } ?: "Eintragen",
        )
        val f = FoodyTheme.farben
        Box(
            Modifier.width(52.dp).heightIn(min = 48.dp).clip(Bayrisch.knopfForm).background(Color.White).border(2.dp, f.text, Bayrisch.knopfForm)
                .clickable(role = Role.Button) { aktionen.onEintragen(null) }
                .semantics { contentDescription = "Scannen oder Foto" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = f.text)
        }
    }
}

/** Mahlzeit für „+ … eitragn“: Friahstück, Mittagessn, Abendessn, Brotzeit. */
internal fun Mahlzeit.bairischLang(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Friahstück"
    Mahlzeit.MITTAGESSEN -> "Mittagessn"
    Mahlzeit.ABENDESSEN -> "Abendessn"
    Mahlzeit.SNACK -> "Brotzeit"
}

@Composable
private fun Verweis(text: String, beschreibung: String, onKlick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = beschreibung }.padding(vertical = 12.dp),
        style = Bayrisch.titel(16f), color = FoodyTheme.farben.text,
    )
}

/** Himmel von Hellblau zu Creme, unten Berge mit Schnee, ein grüner Hügel und der Kirchturm mit Zwiebel. */
@Composable
private fun Landschaft(modifier: Modifier) {
    val pfade = remember {
        val p = { d: String -> PathParser().parsePathString(d).toPath() }
        listOf(
            p("M0 640L50 590 80 610 130 540 170 580 210 530 260 590 300 560 340 600 390 570V844H0z"),
            p("M126 548l4-8 6 9-5-2zM206 538l4-8 6 9-5-2z"),
            p("M0 700q100-40 200-10t190-20V844H0z"),
            p("M17 0c10 8 12 16 8 22 6 4 6 12 0 18H9c-6-6-6-14 0-18-4-6-2-14 8-22z"),
        )
    }
    val dunkel = FoodyTheme.farben.text
    Canvas(modifier.clearAndSetSemantics { }) {
        drawRect(Brush.verticalGradient(0f to Color(0xFFDCEBF7), 0.6f to Color(0xFFFBF6EA)))
        val s = size.width / 390f
        translate(0f, size.height - 844f * s) {
            scale(s, Offset.Zero) {
                drawPath(pfade[0], Color(0xFFB9CFE3))
                drawPath(pfade[1], Color.White)
                drawPath(pfade[2], Color(0xFF9CBF7A))
                translate(300f, 616f) {
                    drawRect(Color(0xFFFBF6EA), Offset(0f, 40f), Size(34f, 52f))
                    drawRect(dunkel, Offset(0f, 40f), Size(34f, 52f), style = Stroke(1.5f))
                    drawPath(pfade[3], Color(0xFF6E7F45))
                    drawPath(pfade[3], dunkel, style = Stroke(1.5f))
                    drawLine(dunkel, Offset(17f, -8f), Offset(17f, 2f), 1.5f)
                    drawRoundRect(dunkel, Offset(12f, 56f), Size(10f, 14f), CornerRadius(5f))
                }
            }
        }
    }
}

private const val BAUM_HOEHE = 772f
private val PLATZ_FUER_LEISTE = 104.dp
