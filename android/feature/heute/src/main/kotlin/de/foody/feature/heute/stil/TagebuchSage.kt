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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.sage.sageLinieUnten
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.TAGEBUCH_INHALT
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.tagebuch.TagAbschnitt
import de.foody.feature.heute.tagebuch.TagZustand
import de.foody.feature.heute.tagebuch.werte
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TAG_KOPF = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN)
private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/**
 * Tagebuch der Düsteren Sage in Immersiv (Entwurf `Q-Tagebuch`): „Chronik“ mit Tageswahl, Ring und Makros, je Mahlzeit
 * eine Gruppe („Des Morgens“ …). Antippen eines Eintrags öffnet „Eintrag berichtigen“ mit Ändern und Tilgen; die
 * Raute unten rechts trägt ein. Dieselben Aktionen wie die normale Ansicht, TalkBack liest die normalen Begriffe.
 */
@Composable
internal fun TagebuchSage(
    datum: LocalDate,
    heute: LocalDate,
    zustand: TagZustand?,
    onDatum: (LocalDate) -> Unit,
    onWahl: () -> Unit,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onEintragen: (Mahlzeit?) -> Unit,
) {
    var offenId by rememberSaveable(datum) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = ueberLeiste(168.dp)).testTag(TAGEBUCH_INHALT),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "CHRONIK", Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading(); contentDescription = "Tagebuch" },
                    style = Sage.titel(22f, 0.12f), color = FoodyTheme.farben.primaer, textAlign = TextAlign.Center,
                )
                Tageswahl(datum, heute, onDatum, onWahl)
                val z = zustand?.takeIf { it.datum == datum } ?: return@Column
                Bilanz(z)
                Column {
                    z.abschnitte.forEach { a ->
                        Gruppe(a, offenId, onWahl = { e -> offenId = if (offenId == e.id) null else e.id }, onEintragen = { onEintragen(a.mahlzeit) })
                    }
                }
                val offen = offenId?.let { id -> z.abschnitte.firstNotNullOfOrNull { a -> a.eintraege.firstOrNull { it.id == id } } }
                offen?.let { e ->
                    Berichtigen(
                        e, onAendern = { offenId = null; onAendern(e) }, onTilgen = { offenId = null; onLoeschen(e) },
                        onSchliessen = { offenId = null },
                    )
                }
            }
        }
        Eintragsraute(
            { onEintragen(null) },
            Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 28.dp, bottom = ueberLeiste(104.dp)),
        )
    }
}

/** „der heutige Tag“, „gestern“, „vor zwei Tagen“ … wie im Entwurf; für ferne Tage und die Zukunft leer. */
internal fun tagZusatz(datum: LocalDate, heute: LocalDate): String? = when (val tage = ChronoUnit.DAYS.between(datum, heute)) {
    0L -> "der heutige Tag"
    1L -> "gestern"
    -1L -> "morgen"
    in 2L..6L -> "vor ${listOf("", "", "zwei", "drei", "vier", "fünf", "sechs")[tage.toInt()]} Tagen"
    else -> null
}

/** Gruppenname der Sage: „DES MORGENS“, „ZU MITTAG“, „DES ABENDS“, „ZWISCHENMAHL“. */
internal fun Mahlzeit.chronikName(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "DES MORGENS"
    Mahlzeit.MITTAGESSEN -> "ZU MITTAG"
    Mahlzeit.ABENDESSEN -> "DES ABENDS"
    Mahlzeit.SNACK -> "ZWISCHENMAHL"
}

/** Menge eines Eintrags für die Zeile: „1 Port.“, „½ Port.“ bei Rezepten, sonst „350 g“; `null` ohne Menge. */
internal fun eintragMenge(e: TagebuchEintragEntity): String? = when {
    e.art == TagebuchArt.REZEPT -> e.portionen?.let { "${portionenText(it)} Port." }
    e.menge != null && e.einheit != null && e.menge!!.signum() > 0 -> QuantityFormatter.format(Quantity.of(e.menge!!, e.einheit!!), e.einheit!!)
    else -> null
}

private fun portionenText(p: BigDecimal): String {
    val ganz = p.toBigInteger().toInt()
    val rest = p.subtract(BigDecimal(ganz)).toDouble()
    val bruch = when {
        rest in 0.2..0.3 -> "¼"
        rest in 0.45..0.55 -> "½"
        rest in 0.7..0.8 -> "¾"
        else -> null
    }
    return when {
        bruch == null -> p.stripTrailingZeros().toPlainString().replace('.', ',')
        ganz == 0 -> bruch
        else -> "$ganz$bruch"
    }
}

@Composable
private fun Tageswahl(datum: LocalDate, heute: LocalDate, onDatum: (LocalDate) -> Unit, onWahl: () -> Unit) {
    val f = FoodyTheme.farben
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pfeil("‹", "Vorheriger Tag") { onDatum(datum.minusDays(1)) }
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Datum wählen", onClick = onWahl)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = TAG_LANG.format(datum) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(TAG_KOPF.format(datum).uppercase(Locale.GERMAN), style = Sage.titel(16f, 0.1f), color = sageHell(), textAlign = TextAlign.Center)
            tagZusatz(datum, heute)?.let { Text(it, style = Sage.text(14f), color = f.nebentext) }
        }
        Pfeil("›", "Nächster Tag") { onDatum(datum.plusDays(1)) }
    }
}

@Composable
private fun Pfeil(zeichen: String, beschreibung: String, onKlick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onKlick).semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) { Text(zeichen, style = Sage.titel(22f, 0f), color = FoodyTheme.stil.zier) }
}

/** Ring mit den kcal gegen das Ziel, daneben Eiweiß, Kohlenhydrate und Fett als Anteil an der Energie. */
@Composable
private fun Bilanz(z: TagZustand) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val s = z.summe
    val ziel = z.ziel?.takeIf { it > 0 }
    val anteil = if (ziel != null) (s.kcal.toFloat() / ziel).coerceIn(0f, 1f) else if (s.kcal > 0) 1f else 0f
    val satz = (if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal") +
        ": Eiweiß ${s.eiweiss} g, Kohlenhydrate ${s.kohlenhydrate} g, Fett ${s.fett} g"
    val energie = (s.eiweiss * 4 + s.kohlenhydrate * 4 + s.fett * 9).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = satz },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val strich = 8.dp.toPx()
                val r = size.minDimension / 2 - strich / 2 - 1.dp.toPx()
                val tl = Offset(center.x - r, center.y - r)
                drawArc(f.balkenSpur, -90f, 360f, false, tl, Size(2 * r, 2 * r), style = Stroke(strich))
                if (anteil > 0f) drawArc(glut, -90f, 360f * anteil, false, tl, Size(2 * r, 2 * r), style = Stroke(strich))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ganzzahlDeutsch(s.kcal), style = Sage.titel(22f, 0f), color = sageHell())
                Text(if (ziel != null) "von ${ganzzahlDeutsch(ziel)}" else "kcal", style = Sage.text(13f), color = f.nebentext)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Makro("Eiweiß", s.eiweiss, s.eiweiss * 4f / energie, f.makro.eiweiss)
            Makro("Kohlenhydrate", s.kohlenhydrate, s.kohlenhydrate * 4f / energie, f.makro.kohlenhydrate)
            Makro("Fett", s.fett, s.fett * 9f / energie, f.makro.fett)
        }
    }
}

@Composable
private fun Makro(name: String, gramm: Int, anteil: Float, farbe: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(name, Modifier.weight(1f), style = Sage.text(14.5f), color = FoodyTheme.farben.nebentext)
            Text("$gramm g", style = Sage.text(14.5f), color = FoodyTheme.farben.text)
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp).background(FoodyTheme.werte.glas).drawBehind {
                drawRect(farbe, size = Size(size.width * anteil.coerceIn(0f, 1f), size.height))
            },
        )
    }
}

@Composable
private fun Gruppe(a: TagAbschnitt, offenId: String?, onWahl: (TagebuchEintragEntity) -> Unit, onEintragen: () -> Unit) {
    val f = FoodyTheme.farben
    val summe = a.eintraege.sumOf { it.werte().kcal ?: 0 }
    val vollstaendig = a.eintraege.all { it.vollstaendig && it.werte().kcal != null }
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(vertical = 6.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) { heading(); contentDescription = a.mahlzeit.anzeigename() }) {
            Text(a.mahlzeit.chronikName(), Modifier.weight(1f), style = Sage.titel(12f, 0.18f), color = f.nebentext)
            Text(if (a.eintraege.isEmpty()) "–" else kcalText(summe, vollstaendig), style = Sage.text(14f), color = f.nebentext)
        }
        a.eintraege.forEach { e -> Eintrag(e, an = e.id == offenId) { onWahl(e) } }
        if (a.eintraege.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEintragen)
                    .semantics { contentDescription = "Noch nichts eingetragen. ${a.mahlzeit.anzeigename()} eintragen" }.padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("nichts verzeichnet", style = Sage.text(17f).copy(fontStyle = FontStyle.Italic), color = f.nebentext) }
        }
    }
}

@Composable
private fun Eintrag(e: TagebuchEintragEntity, an: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val kcal = e.werte().kcal?.let { if (e.vollstaendig) ganzzahlDeutsch(it) else "≥ ${ganzzahlDeutsch(it)}" }
    val wert = listOfNotNull(eintragMenge(e), kcal).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (an) glut.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClickLabel = "Eintrag berichtigen", onClick = onKlick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(e.name, eintragMenge(e), kcalText(e.werte().kcal, e.vollstaendig)).joinToString(", ")
                stateDescription = if (an) "geöffnet" else ""
                onClick("ändern oder löschen") { onKlick(); true }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(e.name, Modifier.weight(1f), style = Sage.text(17f), color = f.text)
        if (wert.isNotEmpty()) Text(wert, style = Sage.text(14f), color = f.nebentext, textAlign = TextAlign.End)
    }
}

/** „Eintrag berichtigen“: Name und Menge, „Ändern“ (Menge, Mahlzeit, Tag im Dialog) und „Tilgen“. */
@Composable
private fun Berichtigen(e: TagebuchEintragEntity, onAendern: () -> Unit, onTilgen: () -> Unit, onSchliessen: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Column(
        Modifier.fillMaxWidth().border(1.dp, glut).background(FoodyTheme.werte.glas).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "EINTRAG BERICHTIGEN", Modifier.weight(1f).semantics { heading(); contentDescription = "Eintrag bearbeiten" },
                style = Sage.titel(11f, 0.2f), color = f.primaer,
            )
            Box(
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onSchliessen)
                    .semantics { contentDescription = "Schließen" },
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = Sage.text(18f), color = f.nebentext) }
        }
        Text(listOfNotNull(e.name, eintragMenge(e)).joinToString(" · "), style = Sage.text(18f), color = f.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SageNebenknopf("ÄNDERN", "${e.name} ändern", onAendern, Modifier.weight(1f))
            SageNebenknopf("TILGEN", "${e.name} löschen", onTilgen, Modifier.weight(1f), farbe = TILGEN)
        }
    }
}

/** Die Raute unten rechts: „Eintragen“. */
@Composable
private fun Eintragsraute(onKlick: () -> Unit, modifier: Modifier) {
    val glut = FoodyTheme.stil.zier
    Box(
        modifier.size(64.dp).clickable(role = Role.Button, onClick = onKlick).semantics { contentDescription = "Eintragen" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(46.dp).rotate(45f).shadow(12.dp, RectangleShape, ambientColor = glut, spotColor = glut)
                .background(FoodyTheme.farben.grund).border(1.5.dp, glut),
        )
        Text("+", style = Sage.titel(26f, 0f), color = glut)
    }
}

/** Rotbraun für „Tilgen“ wie im Entwurf (#E89A84, AA auf dunklem Grund). */
private val TILGEN = Color(0xFFE89A84)
