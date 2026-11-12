package de.foody.feature.heute.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.TAGEBUCH_INHALT
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.tagebuch.TagAbschnitt
import de.foody.feature.heute.tagebuch.TagZustand
import de.foody.feature.heute.tagebuch.werte
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

private val TAG_NAME = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/**
 * Tagebuch der Stile R–V in Immersiv (Entwürfe `R/S/T/U/V-Tagebuch`). Alle fünf haben denselben Aufbau: Titel, Tag
 * mit ‹ ›, Bilanz (Ring mit Makros, beim Sternenkreuzer ein Balken aus Segmenten), je Mahlzeit eine Gruppe; Antippen
 * eines Eintrags öffnet darunter „Ändern“ und „Löschen“, „+“ unten rechts trägt ein. Wörter und Schriften kommen aus
 * [TagebuchArt]. Dieselben Aktionen wie die normale Ansicht; TalkBack hört die normalen Begriffe.
 */
internal object TagebuchSzene {
    /** Der Stil R–V, dessen Szene das Tagebuch zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        if (s.variante != StilVariante.IMMERSIV) return null
        return s.stil.takeIf { it != FoodyStil.MILCHGLAS && it != FoodyStil.SAGE }
    }
}

/** Wörter und Schriften eines Stils für das Tagebuch. */
internal class TagebuchArt(
    val titel: String,
    val unter: String?,
    val mahlzeit: (Mahlzeit) -> String,
    val leer: String,
    val loeschen: String,
    val zusatz: (LocalDate, LocalDate) -> String?,
    val titelSchrift: (Float) -> TextStyle,
    val text: (Float) -> TextStyle,
    val klein: (Float) -> TextStyle,
)

/** „heute“, „gestern“, „morgen“, „vor 3 Tagen“; ferne Tage ohne Zusatz. */
internal fun tagZusatzKurz(datum: LocalDate, heute: LocalDate): String? = when (val tage = ChronoUnit.DAYS.between(datum, heute)) {
    0L -> "heute"
    1L -> "gestern"
    -1L -> "morgen"
    in 2L..6L -> "vor $tage Tagen"
    else -> null
}

/** Mundart: „heid“, „gestern“, „vorgestern“, „morgn“. */
internal fun bayrischZusatz(datum: LocalDate, heute: LocalDate): String? = when (ChronoUnit.DAYS.between(datum, heute)) {
    0L -> "heid"
    2L -> "vorgestern"
    -1L -> "morgn"
    else -> tagZusatzKurz(datum, heute)
}

/** Sternenkreuzer: „HEUTE · LOGBUCH 297“ bzw. „LOGBUCH 296“ (Tag im Jahr). */
internal fun logbuchZusatz(datum: LocalDate, heute: LocalDate): String =
    (if (datum == heute) "HEUTE · " else "") + "LOGBUCH ${datum.dayOfYear}"

/** Kurze Mahlzeitnamen der Entwürfe („Mittag“, „Abend“); Bayrisch „Friahstück“ und „Brotzeit“. */
internal fun mahlzeitKurz(m: Mahlzeit): String = when (m) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.ABENDESSEN -> "Abend"
    Mahlzeit.SNACK -> "Snack"
}

internal fun mahlzeitBayrisch(m: Mahlzeit): String = when (m) {
    Mahlzeit.FRUEHSTUECK -> "Friahstück"
    Mahlzeit.SNACK -> "Brotzeit"
    else -> mahlzeitKurz(m)
}

internal fun tagebuchArt(stil: FoodyStil): TagebuchArt = when (stil) {
    FoodyStil.ANIME -> TagebuchArt(
        "TAGEBUCH!", null, ::mahlzeitKurz, "Noch leer – tippe auf +", "Löschen", ::tagZusatzKurz,
        { Anime.titel(it, 0f) }, { Anime.text(it, FontWeight.Medium) }, { Anime.text(it, FontWeight.ExtraBold) },
    )
    FoodyStil.EDO -> TagebuchArt(
        "Tagebuch", null, ::mahlzeitKurz, "—", "Löschen", ::tagZusatzKurz,
        { Edo.titel(it) }, { Edo.text(it) }, { Edo.kopf(it) },
    )
    FoodyStil.BAYRISCH -> TagebuchArt(
        "Tagebiachl", null, ::mahlzeitBayrisch, "no nix", "Löschen", ::bayrischZusatz,
        { Bayrisch.titel(it) }, { Bayrisch.text(it) }, { Bayrisch.text(it, FontWeight.Bold) },
    )
    FoodyStil.GRAUEN -> TagebuchArt(
        "Logbuch", "Aufzeichnungen eines Hungrigen", { it.anzeigename() }, "nichts verzeichnet", "Tilgen", ::tagZusatzKurz,
        { Grauen.titel(it) }, { Grauen.text(it) }, { Grauen.titel(it, 0.1f) },
    )
    else -> TagebuchArt(
        "Bordbuch", "LOGBUCH · VERSORGUNG", { it.anzeigename().uppercase(Locale.GERMAN) }, "kein Kontakt", "Löschen", ::logbuchZusatz,
        { Kreuzer.titel(it) }, { Kreuzer.titel(it, FontWeight.Normal, 0f) }, { Kreuzer.mono(it) },
    )
}

/** Anime: die Gruppen als Pillen in den Farben des Entwurfs. */
private fun animeFarbe(m: Mahlzeit, akzent: Color): Color = when (m) {
    Mahlzeit.FRUEHSTUECK -> Anime.gelb
    Mahlzeit.MITTAGESSEN -> Color(0xFF7EE0A8)
    Mahlzeit.ABENDESSEN -> akzent
    Mahlzeit.SNACK -> Anime.tuerkis
}

/** Grauen: die Notiz zum Tag, aus dem, was verzeichnet ist. */
internal fun grauenNotiz(z: TagZustand, heute: LocalDate): String {
    val n = z.abschnitte.count { it.eintraege.isNotEmpty() }
    val ziel = z.ziel
    return when {
        n == 0 -> "Die Seite ist leer. Noch."
        z.datum == heute && (ziel == null || z.summe.kcal < ziel) -> "Der Tag ist noch nicht vorüber. Etwas wartet auf den Abend."
        n == 1 -> "Eine Mahlzeit. Mehr wurde nicht verzeichnet."
        else -> "${listOf("", "", "Zwei", "Drei", "Vier")[n.coerceAtMost(4)]} Mahlzeiten. Keine davon wurde bereut."
    }
}

/** Bayrisch: „Passt scho · no 1.140 frei“ bzw. „Gnua für heid“; `null` ohne Ziel. */
internal fun bayrischSpruch(z: TagZustand): String? = z.ziel?.takeIf { it > 0 }?.let { ziel ->
    val rest = ziel - z.summe.kcal
    if (rest >= 0) "Passt scho · no ${ganzzahlDeutsch(rest)} frei" else "Gnua für heid · ${ganzzahlDeutsch(-rest)} drüber"
}

@Composable
internal fun TagebuchStil(
    stil: FoodyStil,
    datum: LocalDate,
    heute: LocalDate,
    zustand: TagZustand?,
    onDatum: (LocalDate) -> Unit,
    onWahl: () -> Unit,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onEintragen: (Mahlzeit?) -> Unit,
) {
    val art = tagebuchArt(stil)
    var offenId by rememberSaveable(datum) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = ueberLeiste(168.dp)).testTag(TAGEBUCH_INHALT),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Kopf(stil, art)
                Tageswahl(art, datum, heute, onDatum, onWahl)
                val z = zustand?.takeIf { it.datum == datum } ?: return@Column
                if (stil == FoodyStil.KREUZER) Balken(art, z) else Ring(art, z)
                when (stil) {
                    FoodyStil.BAYRISCH -> bayrischSpruch(z)?.let { Text(it, style = art.klein(15f), color = FoodyTheme.farben.primaer) }
                    FoodyStil.GRAUEN -> Text(grauenNotiz(z, heute), style = Grauen.text(16f, kursiv = true), color = FoodyTheme.farben.nebentext)
                    else -> Unit
                }
                z.abschnitte.forEach { a ->
                    Gruppe(
                        stil, art, a, offenId,
                        onWahl = { e -> offenId = if (offenId == e.id) null else e.id },
                        onEintragen = { onEintragen(a.mahlzeit) },
                        onAendern = { e -> offenId = null; onAendern(e) },
                        onLoeschen = { e -> offenId = null; onLoeschen(e) },
                    )
                }
            }
        }
        StilAktion(
            "+", { onEintragen(null) },
            Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 20.dp, bottom = ueberLeiste(104.dp))
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            beschreibung = "Eintragen", voll = true,
        )
    }
}

@Composable
private fun Kopf(stil: FoodyStil, art: TagebuchArt) {
    val f = FoodyTheme.farben
    val d = LocalDensity.current
    val k = d.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        art.unter?.let { Text(it, style = art.klein(12.5f), color = f.nebentext) }
        // Der Titel wächst mit der Systemschrift nur bis 130 %
        val groesse = (if (stil == FoodyStil.ANIME) 36f else 30f) * min(k, 1.3f) / k
        Text(
            art.titel,
            Modifier.semantics {
                heading()
                contentDescription = "Tagebuch"
            },
            style = if (stil == FoodyStil.ANIME) {
                Anime.titel(groesse, 0f).copy(
                    textGeometricTransform = TextGeometricTransform(skewX = -0.14f),
                    shadow = Shadow(FoodyTheme.stil.zier, Offset(3f * d.density, 3f * d.density), 0f),
                )
            } else {
                art.titelSchrift(groesse)
            },
            color = f.text,
        )
    }
}

@Composable
private fun Tageswahl(art: TagebuchArt, datum: LocalDate, heute: LocalDate, onDatum: (LocalDate) -> Unit, onWahl: () -> Unit) {
    val f = FoodyTheme.farben
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StilAktion("‹", { onDatum(datum.minusDays(1)) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Vorheriger Tag")
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Datum wählen", onClick = onWahl)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = TAG_LANG.format(datum)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(TAG_NAME.format(datum), style = art.titelSchrift(17f), color = f.text, textAlign = TextAlign.Center)
            art.zusatz(datum, heute)?.let { Text(it, style = art.klein(12.5f), color = f.nebentext, textAlign = TextAlign.Center) }
        }
        StilAktion("›", { onDatum(datum.plusDays(1)) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Nächster Tag")
    }
}

/** Was TalkBack zur Bilanz hört: „860 von 2.000 kcal: Eiweiß 42 g, …“. */
private fun bilanzSatz(z: TagZustand): String {
    val s = z.summe
    val ziel = z.ziel?.takeIf { it > 0 }
    return (if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal") +
        ": Eiweiß ${s.eiweiss} g, Kohlenhydrate ${s.kohlenhydrate} g, Fett ${s.fett} g"
}

private fun anteil(z: TagZustand): Float {
    val ziel = z.ziel?.takeIf { it > 0 }
    return if (ziel != null) (z.summe.kcal.toFloat() / ziel).coerceIn(0f, 1f) else if (z.summe.kcal > 0) 1f else 0f
}

/** Ring mit den kcal gegen das Ziel, daneben die Makros. */
@Composable
private fun Ring(art: TagebuchArt, z: TagZustand) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val s = z.summe
    val a = anteil(z)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = bilanzSatz(z) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val strich = 10.dp.toPx()
                val r = size.minDimension / 2 - strich / 2 - 1.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                drawArc(f.balkenSpur, -90f, 360f, false, tl, Size(2 * r, 2 * r), style = Stroke(strich))
                if (a > 0f) drawArc(akzent, -90f, 360f * a, false, tl, Size(2 * r, 2 * r), style = Stroke(strich, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ganzzahlDeutsch(s.kcal), style = art.titelSchrift(22f), color = f.text)
                Text(z.ziel?.let { "von ${ganzzahlDeutsch(it)}" } ?: "kcal", style = art.klein(11.5f), color = f.nebentext)
            }
        }
        Makros(art, z, Modifier.weight(1f))
    }
}

@Composable
private fun Makros(art: TagebuchArt, z: TagZustand, modifier: Modifier) {
    val f = FoodyTheme.farben
    val s = z.summe
    val energie = (s.eiweiss * 4 + s.kohlenhydrate * 4 + s.fett * 9).coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("Eiweiß", s.eiweiss, f.makro.eiweiss to s.eiweiss * 4f / energie),
            Triple("Kohlenhydrate", s.kohlenhydrate, f.makro.kohlenhydrate to s.kohlenhydrate * 4f / energie),
            Triple("Fett", s.fett, f.makro.fett to s.fett * 9f / energie),
        ).forEach { (name, gramm, farbeUndAnteil) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(name, Modifier.weight(1f), style = art.text(14.5f), color = f.nebentext)
                    Text("$gramm g", style = art.klein(14f), color = f.text)
                }
                Box(
                    Modifier.fillMaxWidth().height(5.dp).background(f.balkenSpur, RoundedCornerShape(3.dp)).drawBehind {
                        drawRect(farbeUndAnteil.first, size = Size(size.width * farbeUndAnteil.second.coerceIn(0f, 1f), size.height))
                    },
                )
            }
        }
    }
}

/** Sternenkreuzer: 20 Segmente für die kcal gegen das Ziel, Prozent und die Makros. */
@Composable
private fun Balken(art: TagebuchArt, z: TagZustand) {
    val f = FoodyTheme.farben
    val holo = FoodyTheme.stil.zier
    val a = anteil(z)
    val voll = (a * 20).toInt()
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = bilanzSatz(z) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                ganzzahlDeutsch(z.summe.kcal) + (z.ziel?.let { " / ${ganzzahlDeutsch(it)} kcal" } ?: " kcal"), Modifier.weight(1f),
                style = Kreuzer.mono(15f), color = f.text,
            )
            if (z.ziel != null) Text("${(a * 100).toInt()} %", style = Kreuzer.mono(15f), color = holo)
        }
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(20) { i -> Box(Modifier.weight(1f).height(14.dp).background(if (i < voll) holo else f.balkenSpur)) }
        }
        Makros(art, z, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Gruppe(
    stil: FoodyStil,
    art: TagebuchArt,
    a: TagAbschnitt,
    offenId: String?,
    onWahl: (TagebuchEintragEntity) -> Unit,
    onEintragen: () -> Unit,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
) {
    val f = FoodyTheme.farben
    val summe = a.eintraege.sumOf { it.werte().kcal ?: 0 }
    val vollstaendig = a.eintraege.all { it.vollstaendig && it.werte().kcal != null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                heading()
                contentDescription = a.mahlzeit.anzeigename()
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val name = art.mahlzeit(a.mahlzeit)
            if (stil == FoodyStil.ANIME) {
                Text(
                    name.uppercase(Locale.GERMAN),
                    Modifier.background(animeFarbe(a.mahlzeit, FoodyTheme.stil.zier), Anime.pille).border(2.dp, f.text, Anime.pille)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    style = Anime.text(12.5f, FontWeight.ExtraBold), color = f.text,
                )
                Box(Modifier.weight(1f))
            } else {
                Text(name, Modifier.weight(1f), style = art.klein(13.5f), color = f.nebentext)
            }
            if (a.eintraege.isNotEmpty()) Text(kcalText(summe, vollstaendig), style = art.klein(13f), color = f.nebentext)
        }
        a.eintraege.forEach { e ->
            val an = e.id == offenId
            Eintrag(art, e, an) { onWahl(e) }
            if (an) Offen(art, e, { onAendern(e) }, { onLoeschen(e) }, { onWahl(e) })
        }
        if (a.eintraege.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEintragen)
                    .semantics { contentDescription = "Noch nichts eingetragen. ${a.mahlzeit.anzeigename()} eintragen" }.padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(art.leer, style = art.text(15.5f), color = f.nebentext) }
        }
    }
}

@Composable
private fun Eintrag(art: TagebuchArt, e: TagebuchEintragEntity, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val kcal = e.werte().kcal?.let { if (e.vollstaendig) ganzzahlDeutsch(it) else "≥ ${ganzzahlDeutsch(it)}" }
    val wert = listOfNotNull(eintragMenge(e), kcal).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(if (an) akzent.copy(alpha = 0.14f) else Color.Transparent, FoodyTheme.formen.klein)
            .drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .clickable(onClickLabel = "Eintrag bearbeiten", onClick = onKlick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(e.name, eintragMenge(e), kcalText(e.werte().kcal, e.vollstaendig)).joinToString(", ")
                stateDescription = if (an) "geöffnet" else ""
                onClick("ändern oder löschen") { onKlick(); true }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(e.name, Modifier.weight(1f), style = art.text(16f), color = f.text)
        if (wert.isNotEmpty()) Text(wert, style = art.klein(13.5f), color = f.nebentext, textAlign = TextAlign.End)
    }
}

/** Unter dem geöffneten Eintrag: Name und Menge, „Ändern“ (Menge, Mahlzeit, Tag im Dialog) und „Löschen“. */
@Composable
private fun Offen(art: TagebuchArt, e: TagebuchEintragEntity, onAendern: () -> Unit, onLoeschen: () -> Unit, onSchliessen: () -> Unit) {
    val f = FoodyTheme.farben
    Column(
        Modifier.fillMaxWidth().border(1.5.dp, FoodyTheme.stil.zier, FoodyTheme.formen.klein).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(listOfNotNull(e.name, eintragMenge(e)).joinToString(" · "), Modifier.weight(1f), style = art.text(16f), color = f.text)
            StilAktion("✕", onSchliessen, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Schließen")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StilAktion("Ändern", onAendern, Modifier.weight(1f), beschreibung = "${e.name} ändern")
            StilAktion(art.loeschen, onLoeschen, Modifier.weight(1f), beschreibung = "${e.name} löschen")
        }
    }
}
