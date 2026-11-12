package de.foody.feature.heute.stil

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.AnimeKnopf
import de.foody.core.designsystem.stil.anime.AnimePille
import de.foody.core.designsystem.stil.anime.animeSchatten
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.anzeigename
import java.time.LocalDate
import java.util.Locale
import kotlin.math.min

/**
 * „Heute“ des Anime-Stils in Immersiv (Entwurf `R-Heute`): schräges „HEUTE!“ mit Schatten im Akzent, Foody das
 * Reisbällchen mit Sprechblase (was heute ansteht und wie viel noch frei ist), die nächste Mahlzeit als Karte mit
 * Speedlines und hartem Schatten („LOS GEHT'S! ▶“), Energie als schräge Segmente und die gegessenen Mahlzeiten als
 * schief geklebte Zettel neben „+ Eintragen“. TalkBack hört die normalen Begriffe.
 */
@Composable
internal fun HeuteAnime(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = ueberLeiste(PLATZ_FUER_LEISTE))
                .testTag(HEUTE_INHALT),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Kopf(zustand, aktionen.onEinstellungen)
            if (zustand == null) return@Column
            if (zustand.datenbankLeer) {
                LeererZustandFlaeche(
                    Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
                    aktionText = "Server verbinden", onAktion = aktionen.onEinstellungen,
                )
            }
            Reisbaellchen(sprechblase(zustand))
            Mahlzeitkarte(zustand, aktionen)
            Energie(zustand.tagebuch, aktionen.onAlleEintraege)
            Zettel(zustand.tagebuch, aktionen)
            zustand.einkauf?.let { e ->
                Box(
                    Modifier.heightIn(min = 48.dp).clip(Anime.pille).clickable(role = Role.Button) { aktionen.onEinkauf(e.listeId) }
                        .semantics { contentDescription = "Einkauf, ${e.anzahl} offen" },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AnimePille("QUEST · ${e.anzahl} offen ▸", grund = Anime.gelb)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(zustand: HeuteZustand?, onEinstellungen: () -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val k = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Der große Titel wächst mit der Systemschrift nur bis 130 %, wie große Schrift unter Android 14
            Text(
                "HEUTE!",
                Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).semantics {
                    heading()
                    contentDescription = "Heute"
                },
                style = Anime.titel(44f * min(k, 1.3f) / k, 0f).copy(
                    textGeometricTransform = TextGeometricTransform(skewX = -0.14f),
                    shadow = Shadow(akzent, Offset(3f * LocalDensity.current.density, 3f * LocalDensity.current.density), 0f),
                ),
                color = f.text, maxLines = 1,
            )
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                zustand?.datum?.let { AnimePille(animeDatum(it), Modifier.rotate(4f), grund = Anime.gelb) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onEinstellungen)
                        .semantics { contentDescription = "Einstellungen" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(44.dp).background(Color.White, CircleShape).border(2.5.dp, f.text, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = f.text)
                    }
                }
            }
        }
        zustand?.sync?.let { SyncStatus(it) }
    }
}

/** „Sa · 24.10.“ für das gelbe Abzeichen. */
internal fun animeDatum(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.GERMAN).removeSuffix(".") + " · ${d.dayOfMonth}.${d.monthValue}."

/** Kurzname eines Gerichts für die Sprechblase: „Ofengemüse mit Feta …“ → „Ofengemüse“. */
internal fun kurzname(titel: String): String = titel.substringBefore(" mit ").substringBefore(" und ").substringBefore(",").trim()

/** Was Foody in der Sprechblase sagt: was ansteht und wie viel noch frei ist. */
internal fun sprechblase(z: HeuteZustand): String {
    val s = z.tagebuch.summe.kcal
    val ziel = z.tagebuch.ziel?.takeIf { it > 0 }
    val n = z.naechste
    val gericht = n?.let {
        val wann = when (Mahlzeit.ausText(it.mahlzeit.slot)) {
            Mahlzeit.FRUEHSTUECK -> if (it.morgen) "Morgen früh" else "Zum Frühstück"
            Mahlzeit.MITTAGESSEN -> if (it.morgen) "Morgen Mittag" else "Heute Mittag"
            Mahlzeit.ABENDESSEN -> if (it.morgen) "Morgen Abend" else "Heute Abend"
            Mahlzeit.SNACK, null -> if (it.morgen) "Morgen" else "Gleich"
        }
        "$wann gibt's ${kurzname(it.mahlzeit.titel)}!"
    } ?: z.inspiration?.let { "Noch nichts geplant – wie wär's mit ${kurzname(it.titel)}?" } ?: "Noch nichts geplant – trag ein, was du isst!"
    val bilanz = when {
        ziel == null -> "Schon ${ganzzahlDeutsch(s)} kcal heute!"
        s > ziel -> "Heute war's schon reichlich – morgen geht's weiter!"
        else -> {
            val frei = ziel - s
            val passt = n?.takeIf { !it.morgen }?.mahlzeit?.kcalJePortion?.let { it <= frei } == true
            "Noch ${ganzzahlDeutsch(frei)} kcal frei" + if (passt) " – das passt perfekt." else "."
        }
    }
    return "$gericht $bilanz"
}

/** Foody das Reisbällchen und die Sprechblase daneben. */
@Composable
private fun Reisbaellchen(text: String) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val pfade = androidx.compose.runtime.remember {
        val p = { d: String -> PathParser().parsePathString(d).toPath() }
        Triple(
            p("M46 8C56 8 84 58 84 70c0 10-8 14-38 14S8 80 8 70C8 58 36 8 46 8z"),
            p("M41 57q5 5 10 0"),
            p("M80 14l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(92.dp).clearAndSetSemantics { }) {
            val s = size.width / 92f
            scale(s, Offset.Zero) {
                val (koerper, mund, stern) = pfade
                drawPath(koerper, Color.White)
                clipRect(24f, 64f, 68f, 84f) { drawPath(koerper, f.text) }
                drawPath(koerper, f.text, style = Stroke(3f, join = StrokeJoin.Round))
                listOf(35f, 57f).forEach { x ->
                    drawOval(f.text, Offset(x - 5f, 41f), Size(10f, 14f))
                    drawCircle(Color.White, 2f, Offset(x + 1.5f, 45.5f))
                }
                listOf(25f, 67f).forEach { x -> drawOval(akzent.copy(alpha = 0.7f), Offset(x - 5f, 55f), Size(10f, 6f)) }
                drawPath(mund, f.text, style = Stroke(2.5f, cap = StrokeCap.Round))
                drawPath(stern, Anime.gelb)
                drawPath(stern, f.text, style = Stroke(1.5f))
            }
        }
        Box(
            Modifier.weight(1f)
                .drawBehind {
                    // Spitze der Blase nach links zum Reisbällchen
                    val y = 26.dp.toPx()
                    val spitze = Path().apply {
                        moveTo(1.dp.toPx(), y)
                        lineTo(-12.dp.toPx(), y + 9.dp.toPx())
                        lineTo(1.dp.toPx(), y + 18.dp.toPx())
                        close()
                    }
                    drawPath(spitze, f.text)
                }
                .background(Color.White, RoundedCornerShape(22.dp)).border(2.5.dp, f.text, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, style = Anime.text(15f), color = f.text)
        }
    }
}

/** Die nächste Mahlzeit als Karte im Akzent mit Speedlines und hartem Schatten; ohne Plan die Inspiration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Mahlzeitkarte(zustand: HeuteZustand, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val n = zustand.naechste
    val r = zustand.inspiration
    if (n == null && r == null) return
    val form = RoundedCornerShape(topStart = 6.dp, topEnd = 26.dp, bottomEnd = 6.dp, bottomStart = 26.dp)
    val titel = n?.mahlzeit?.titel ?: requireNotNull(r).titel
    val rezeptId = n?.mahlzeit?.rezeptId ?: requireNotNull(r).id
    val schild = n?.let { (if (it.morgen) "MORGEN · " else "") + Mahlzeit.ausText(it.mahlzeit.slot)?.anzeigename().orEmpty().ifEmpty { it.mahlzeit.slot } }
        ?: "INSPIRATION"
    Column(
        Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)
            .animeSchatten(form, f.text)
            .clip(form).background(akzent)
            .drawBehind { speedlines() }
            .border(3.dp, f.text, form)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            schild.uppercase(Locale.GERMAN),
            Modifier.background(f.text, Anime.pille).padding(horizontal = 12.dp, vertical = 3.dp),
            style = Anime.text(12f, FontWeight.ExtraBold).copy(letterSpacing = 0.06.em),
            color = Color.White,
        )
        Text(
            "$titel ›",
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { aktionen.onRezept(rezeptId) }
                .semantics { contentDescription = "$titel, Rezept öffnen" }.padding(vertical = 2.dp),
            style = Anime.titel(25f, 0f), color = f.text,
        )
        val angaben = n?.mahlzeit?.let {
            listOfNotNull("${it.portionen} Port.", it.kcalJePortion?.let { k -> "$k kcal" }, it.zeitMinuten?.let { m -> "$m Min." })
        }
            ?: r?.let { listOfNotNull(it.kcalJePortion?.let { k -> "$k kcal" }, it.zeitMinuten?.let { m -> "$m Min." }) }.orEmpty()
        if (angaben.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                angaben.forEach { AnimePille(it) }
            }
        }
        if (n != null) {
            val m = n.mahlzeit
            AnimeKnopf("LOS GEHT'S! ▶", { aktionen.onKochen(m.rezeptId, m.portionen) }, Modifier.fillMaxWidth().padding(top = 4.dp), beschreibung = "Kochen")
        } else {
            AnimeKnopf("ANSEHEN! ▶", { aktionen.onRezept(rezeptId) }, Modifier.fillMaxWidth().padding(top = 4.dp), beschreibung = "Rezept ansehen")
        }
    }
}

/** Speedlines oben rechts in Weiß (im Entwurf 340 × 220, rechts und oben über den Rand hinaus). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.speedlines() {
    val s = density
    val ursprung = Offset(size.width + 60f * s - 340f * s, -40f * s)
    val mitte = ursprung + Offset(240f * s, 110f * s)
    listOf(
        Offset(340f, 40f), Offset(340f, 80f), Offset(340f, 120f), Offset(340f, 160f), Offset(320f, 210f),
        Offset(270f, 0f), Offset(310f, 0f), Offset(200f, 0f), Offset(160f, 30f), Offset(280f, 220f),
    ).forEach { z ->
        drawLine(Color.White.copy(alpha = 0.22f), mitte, ursprung + Offset(z.x * s, z.y * s), 3f * s, StrokeCap.Round)
    }
}

/** Anteil der kcal am Ziel in Zehnteln: 860 von 2.000 → 4,3 Segmente. */
internal fun segmente(kcal: Int, ziel: Int?): Float = ziel?.takeIf { it > 0 }?.let { (kcal * 10f / it).coerceIn(0f, 10f) } ?: 0f

/** Energie des Tages: Verweis aufs Tagebuch, kcal gegen das Ziel, zehn schräge Segmente und die Nährwerte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Energie(t: TagebuchHeute, onTagebuch: () -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    val form = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().background(Color.White, form).border(2.5.dp, f.text, form).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "ENERGIE · Tagebuch ▸",
                Modifier.align(Alignment.CenterVertically).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onTagebuch)
                    .semantics { contentDescription = "Tagebuch öffnen" }.padding(vertical = 14.dp, horizontal = 0.dp),
                style = Anime.text(13f, FontWeight.ExtraBold), color = f.text,
            )
            Row(
                Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {
                    contentDescription = if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal"
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(ganzzahlDeutsch(s.kcal), style = Anime.titel(24f, 0f), color = f.text)
                val rest = ziel?.let { " / ${ganzzahlDeutsch(it)} kcal" } ?: " kcal"
                Text(rest, Modifier.padding(bottom = 3.dp), style = Anime.text(13f), color = f.text)
            }
        }
        val voll = segmente(s.kcal, ziel)
        Canvas(Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics { }) {
            val luecke = 3.dp.toPx()
            val b = (size.width - 9 * luecke - 6.dp.toPx()) / 10f
            val schraeg = size.height * 0.32f
            for (i in 0 until 10) {
                val x = 3.dp.toPx() + i * (b + luecke)
                val form4 = Path().apply {
                    moveTo(x + schraeg, 0f)
                    lineTo(x + b + schraeg, 0f)
                    lineTo(x + b, size.height)
                    lineTo(x, size.height)
                    close()
                }
                val anteil = (voll - i).coerceIn(0f, 1f)
                if (anteil > 0f) clipRect(right = x + schraeg + b * anteil + (if (anteil >= 1f) schraeg else 0f)) { drawPath(form4, akzent) }
                drawPath(form4, f.text, style = Stroke(2.dp.toPx(), join = StrokeJoin.Miter))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple("Eiweiß ${s.eiweiss} g", "Eiweiß ${s.eiweiss} g", f.makro.eiweiss),
                Triple("KH ${s.kohlenhydrate} g", "Kohlenhydrate ${s.kohlenhydrate} g", f.makro.kohlenhydrate),
                Triple("Fett ${s.fett} g", "Fett ${s.fett} g", f.makro.fett),
            ).forEach { (text, vorlesen, farbe) ->
                Row(Modifier.clearAndSetSemantics { contentDescription = vorlesen }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(farbe, CircleShape))
                    Text(text, Modifier.padding(start = 5.dp), style = Anime.text(12.5f), color = f.text)
                }
            }
        }
    }
}

/** Gegessene Mahlzeiten als schief geklebte Zettel (je Reihe drei), am Ende die Kachel „+ Eintragen“. */
@Composable
private fun Zettel(t: TagebuchHeute, aktionen: HeuteAktionen) {
    val gegessen = t.mahlzeiten.filter { it.eintraege.isNotEmpty() }
    val kacheln: List<MahlzeitAbschnitt?> = gegessen + null
    val neigung = listOf(-1.5f, 1f, -1f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kacheln.chunked(3).forEachIndexed { reihe, teil ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                teil.forEachIndexed { i, a ->
                    val winkel = neigung[(reihe + i) % 3]
                    if (a != null) {
                        MahlzeitZettel(a, Modifier.weight(1f).rotate(winkel))
                    } else {
                        EintragenKachel({ aktionen.onEintragen(null) }, Modifier.weight(1f).rotate(winkel))
                    }
                }
                repeat(3 - teil.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MahlzeitZettel(a: MahlzeitAbschnitt, modifier: Modifier) {
    val f = FoodyTheme.farben
    val kcal = a.eintraege.sumOf { it.kcal ?: 0 }
    val voll = a.eintraege.all { it.vollstaendig && it.kcal != null }
    val name = a.mahlzeit.anzeigename()
    Column(
        modifier.background(Color.White, RoundedCornerShape(4.dp)).border(2.5.dp, f.text, RoundedCornerShape(4.dp))
            .clearAndSetSemantics { contentDescription = "$name: ${a.eintraege.joinToString(", ") { it.name }}, ${kcalText(kcal, voll)}" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${animeKurz(a.mahlzeit)} ✓", style = Anime.text(11f, FontWeight.ExtraBold), color = f.primaer)
        Text(a.eintraege.joinToString(", ") { it.name }, style = Anime.text(13.5f), color = f.text)
        Text((if (voll) "" else "≥ ") + ganzzahlDeutsch(kcal), style = Anime.text(12f), color = f.text)
    }
}

/** Kurzer Name auf dem Zettel: FRÜHSTÜCK, MITTAG, SNACK, ABEND. */
internal fun animeKurz(m: Mahlzeit): String = when (m) {
    Mahlzeit.FRUEHSTUECK -> "FRÜHSTÜCK"
    Mahlzeit.MITTAGESSEN -> "MITTAG"
    Mahlzeit.SNACK -> "SNACK"
    Mahlzeit.ABENDESSEN -> "ABEND"
}

@Composable
private fun EintragenKachel(onKlick: () -> Unit, modifier: Modifier) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(4.dp)
    Column(
        modifier.heightIn(min = 72.dp).clip(form).background(Anime.tuerkis)
            .drawBehind {
                drawRoundRect(
                    f.text, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
            .clickable(role = Role.Button, onClick = onKlick)
            .semantics(mergeDescendants = true) { contentDescription = "Eintragen" }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", style = Anime.titel(22f, 0f), color = f.text)
        Text("Eintragen", style = Anime.text(12f, FontWeight.ExtraBold), color = f.text)
    }
}

private val PLATZ_FUER_LEISTE = 104.dp
