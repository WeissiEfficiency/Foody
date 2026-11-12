package de.foody.feature.rezepte.detail.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LEISTE_REGELHOEHE
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
import de.foody.feature.rezepte.detail.DetailAktionen
import de.foody.feature.rezepte.detail.DetailZustand
import de.foody.feature.rezepte.detail.fehlendeWerte
import de.foody.feature.rezepte.detail.gangName
import de.foody.feature.rezepte.detail.kcalJePortion
import de.foody.feature.rezepte.detail.mahlzeitName
import de.foody.feature.rezepte.detail.mengeText
import de.foody.feature.rezepte.detail.naehrwertZeilen
import de.foody.feature.rezepte.detail.zeit
import java.util.Locale

/**
 * Gemeinsames der Rezept-Szenen der Stile R–V in Immersiv (Entwürfe `R/S/T/U/V-Rezept`): Kopf mit „‹“ und Menü „⋯“
 * (Bearbeiten, Duplizieren, Archivieren), der Hinweis „archiviert“ mit Zurückholen, der Titelblock des Stils,
 * Portionen, die drei Reiter Zutaten · Zubereitung · Nährwerte und unten Kochen, Einkaufsliste und Favorit. Dieselben
 * [DetailAktionen] wie die normale Ansicht; TalkBack hört die normalen Begriffe.
 */
internal object RezeptSzene {
    /** Der Stil R–V, dessen Szene das Rezept zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        if (s.variante != StilVariante.IMMERSIV) return null
        return s.stil.takeIf { it != FoodyStil.MILCHGLAS && it != FoodyStil.SAGE }
    }
}

/** Wörter und Schriften eines Stils für das Rezept. */
internal class RezeptArt(
    val kopf: String,
    val reiter: List<String>,
    val kochen: String,
    val liste: String,
    val duplizieren: String,
    val archiviert: String,
    val zurueckholen: String,
    val portionen: (Int) -> String,
    val nummer: (Int) -> String,
    val werteKopf: List<String>,
    val titel: (Float) -> TextStyle,
    val text: (Float) -> TextStyle,
    val klein: (Float) -> TextStyle,
    /** Reiter als gefüllte Pille (Anime, Bayrisch) statt unterstrichen. */
    val pillen: Boolean = false,
    val favorit: Pair<String, String> = "★" to "☆",
)

/** Die normalen Begriffe der Reiter für TalkBack. */
private val REITER = listOf("Zutaten", "Zubereitung", "Nährwerte")

private fun portionenText(n: Int) = if (n == 1) "1 Portion" else "$n Portionen"

internal fun rezeptArt(stil: FoodyStil): RezeptArt = when (stil) {
    FoodyStil.ANIME -> RezeptArt(
        kopf = "", reiter = listOf("Zutaten", "Schritte", "Nährwerte"), kochen = "KOCHEN ▶", liste = "Einkaufen",
        duplizieren = "Duplizieren", archiviert = "Dieses Rezept ist archiviert.", zurueckholen = "Zurückholen",
        portionen = { "$it PORT." }, nummer = { "$it" }, werteKopf = listOf("NÄHRWERT", "PORTION", "GESAMT"),
        titel = { Anime.titel(it, 0f) }, text = { Anime.text(it, FontWeight.Medium) }, klein = { Anime.text(it, FontWeight.ExtraBold) },
        pillen = true,
    )
    FoodyStil.EDO -> RezeptArt(
        kopf = "", reiter = REITER, kochen = "KOCHEN", liste = "Einkaufen", duplizieren = "Duplizieren",
        archiviert = "archiviert", zurueckholen = "zurückholen", portionen = ::portionenText, nummer = { "$it" },
        werteKopf = listOf("NÄHRWERT", "PORTION", "GESAMT"),
        titel = { Edo.titel(it) }, text = { Edo.text(it) }, klein = { Edo.kopf(it) }, favorit = "●" to "○",
    )
    FoodyStil.BAYRISCH -> RezeptArt(
        kopf = "", reiter = listOf("Zuadaten", "So geht's", "Nährwerte"), kochen = "Pack ma's!", liste = "Eikaufn",
        duplizieren = "Duplizieren", archiviert = "archiviert", zurueckholen = "zruckholn",
        portionen = { if (it == 1) "1 Portion" else "$it Portiona" }, nummer = { "$it." },
        werteKopf = listOf("Nährwert", "Portion", "Gesamt"),
        titel = { Bayrisch.titel(it) }, text = { Bayrisch.text(it) }, klein = { Bayrisch.text(it, FontWeight.Bold) },
        pillen = true, favorit = "♥" to "♡",
    )
    FoodyStil.GRAUEN -> RezeptArt(
        kopf = "Aus der Bibliothek", reiter = REITER, kochen = "Das Ritual beginnen", liste = "Liste",
        duplizieren = "Abschrift anfertigen", archiviert = "VERSIEGELT", zurueckholen = "öffnen", portionen = ::portionenText,
        nummer = { roemisch(it) + "." }, werteKopf = listOf("Nährwert", "Portion", "Gesamt"),
        titel = { Grauen.titel(it) }, text = { Grauen.text(it) }, klein = { Grauen.titel(it, 0.1f) }, favorit = "♦" to "◇",
    )
    else -> RezeptArt(
        kopf = "SYSTEMDATEN", reiter = listOf("Zutaten", "Ablauf", "Nährwerte"), kochen = "Kurs setzen", liste = "Ladeliste",
        duplizieren = "Duplizieren", archiviert = "ARCHIVIERT", zurueckholen = "ZURÜCKHOLEN", portionen = { "CREW $it" },
        nummer = { it.toString().padStart(2, '0') }, werteKopf = listOf("WERT", "PORTION", "GESAMT"),
        titel = { Kreuzer.titel(it) }, text = { Kreuzer.titel(it, FontWeight.Normal, 0f) }, klein = { Kreuzer.mono(it) },
    )
}

/** „I“, „II“ … „XXXIX“; darüber die Zahl. */
internal fun roemisch(zahl: Int): String {
    if (zahl !in 1..39) return zahl.toString()
    return "X".repeat(zahl / 10) + listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")[zahl % 10]
}

/** „Abendessen · Hauptspeise · vegetarisch“: Mahlzeiten, Gänge und der erste Tag; `null` ohne Einordnung. */
internal fun einordnungZeile(z: DetailZustand.Da): String? {
    val e = z.voll.einordnung
    val teile = e.mahlzeiten.sortedBy { it.ordinal }.map(::mahlzeitName) + e.gaenge.sortedBy { it.ordinal }.map(::gangName) +
        z.voll.rezept.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(1)
    return teile.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** „45 Min. · 540 kcal je Portion“ – nur, was bekannt ist; `null` ohne Werte. */
internal fun angabenZeile(z: DetailZustand.Da): String? =
    listOfNotNull(zeit(z.voll.rezept.prepMinutes, z.voll.rezept.cookMinutes), kcalJePortion(z.voll)).joinToString(" · ").takeIf { it.isNotEmpty() }

/** Die Szene des Stils [stil] (R–V). */
@Composable
internal fun RezeptStil(stil: FoodyStil, z: DetailZustand.Da, a: DetailAktionen) {
    val art = rezeptArt(stil)
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    // Die Knopfleiste wächst mit der Schrift; so viel Platz bleibt unten frei
    var leiste by remember { mutableStateOf(140.dp) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = leiste + 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Kopf(art, z, a)
                if (z.voll.rezept.archivedAt != null) Archiviert(art) { a.onArchivieren(false) }
                when (stil) {
                    FoodyStil.ANIME -> AnimeHeld(z)
                    FoodyStil.EDO -> EdoHeld(z)
                    FoodyStil.BAYRISCH -> BayrischHeld(z)
                    FoodyStil.GRAUEN -> GrauenHeld(z)
                    else -> KreuzerHeld(z)
                }
                Portionen(art, z.portionen, a.onPortionen)
                Reiterleiste(art, reiter) { reiter = it }
                when (reiter) {
                    0 -> Zutaten(art, z)
                    1 -> Schritte(art, z.voll.schritte)
                    else -> Werte(art, z)
                }
            }
        }
        Knopfleiste(art, z, a) { leiste = it }
    }
}

@Composable
private fun Kopf(art: RezeptArt, z: DetailZustand.Da, a: DetailAktionen) {
    var menue by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StilAktion("‹", a.onZurueck, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Zurück")
        Text(art.kopf, Modifier.weight(1f), style = art.klein(12f), color = FoodyTheme.farben.nebentext, textAlign = TextAlign.Center)
        Box {
            StilAktion("⋯", { menue = true }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Weitere Aktionen")
            DropdownMenu(menue, onDismissRequest = { menue = false }) {
                Eintrag(art, "Bearbeiten", "Bearbeiten") { menue = false; a.onBearbeiten() }
                Eintrag(art, art.duplizieren, "Duplizieren") { menue = false; a.onDuplizieren() }
                if (z.voll.rezept.archivedAt == null) Eintrag(art, "Archivieren", "Archivieren") { menue = false; a.onArchivieren(true) }
            }
        }
    }
}

@Composable
private fun Eintrag(art: RezeptArt, text: String, begriff: String, onKlick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = art.text(16f), color = FoodyTheme.farben.text) },
        onClick = onKlick,
        modifier = Modifier.semantics { contentDescription = begriff },
    )
}

@Composable
private fun Archiviert(art: RezeptArt, onZurueckholen: () -> Unit) {
    val f = FoodyTheme.farben
    Row(
        Modifier.fillMaxWidth().border(1.dp, f.primaer).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(art.archiviert, Modifier.weight(1f), style = art.text(15f), color = f.text)
        StilAktion(art.zurueckholen, onZurueckholen, beschreibung = "Wiederherstellen")
    }
}

@Composable
private fun Portionen(art: RezeptArt, n: Int, onSchritt: (Boolean) -> Unit) {
    val f = FoodyTheme.farben
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StilAktion("−", { onSchritt(false) }, Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp), beschreibung = "Weniger Portionen")
        Text(
            art.portionen(n),
            Modifier.weight(1f).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = portionenText(n)
            },
            style = art.titel(18f), color = f.text, textAlign = TextAlign.Center,
        )
        StilAktion("+", { onSchritt(true) }, Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp), beschreibung = "Mehr Portionen")
    }
}

@Composable
private fun Reiterleiste(art: RezeptArt, aktiv: Int, onWahl: (Int) -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Row(
        Modifier.fillMaxWidth().selectableGroup().then(
            if (art.pillen) Modifier else Modifier.drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
        ),
        horizontalArrangement = Arrangement.spacedBy(if (art.pillen) 8.dp else 0.dp),
    ) {
        art.reiter.forEachIndexed { i, wort ->
            val an = i == aktiv
            val form = FoodyTheme.formen.knopf
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .then(
                        when {
                            art.pillen && an -> Modifier.background(akzent, form).border(2.dp, f.text, form)
                            art.pillen -> Modifier.border(2.dp, f.text, form)
                            an -> Modifier.drawBehind {
                                drawLine(akzent, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 3.dp.toPx())
                            }
                            else -> Modifier
                        },
                    )
                    .selectable(an, role = Role.Tab) { onWahl(i) }
                    .semantics { contentDescription = REITER[i] }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    wort, style = art.klein(13f), textAlign = TextAlign.Center,
                    color = when {
                        art.pillen && an -> if (akzent.luminance() > 0.3f) f.text else Color.White
                        an -> f.text
                        else -> f.nebentext
                    },
                )
            }
        }
    }
}

@Composable
private fun Zutaten(art: RezeptArt, z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    Column {
        z.voll.zeilen.forEach { zeile ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .drawBehind {
                        val punkte = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                        drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = punkte)
                    }
                    .semantics(mergeDescendants = true) { }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(zeile.name + if (zeile.optional) " (optional)" else "", style = art.text(16f), color = f.text)
                    zeile.hinweis?.let { Text(it, style = art.text(14f), color = f.nebentext) }
                }
                Text(mengeText(z, zeile), style = art.klein(14.5f), color = f.text, textAlign = TextAlign.End)
            }
        }
        if (z.voll.zeilen.isEmpty()) Text("Noch keine Zutaten.", style = art.text(16f), color = f.nebentext)
    }
}

@Composable
private fun Schritte(art: RezeptArt, schritte: List<String>) {
    val f = FoodyTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        schritte.forEachIndexed { i, s ->
            Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    art.nummer(i + 1), Modifier.widthIn(min = 34.dp).clearAndSetSemantics { contentDescription = "Schritt ${i + 1}" },
                    style = art.titel(16f), color = f.primaer,
                )
                Text(s, Modifier.weight(1f), style = art.text(16f), color = f.text)
            }
        }
        if (schritte.isEmpty()) Text("Noch keine Schritte.", style = art.text(16f), color = f.nebentext)
    }
}

@Composable
private fun Werte(art: RezeptArt, z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val zeilen = naehrwertZeilen(z.voll, z.portionen)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (zeilen.isEmpty()) {
            Text("Für dieses Rezept sind keine Werte bekannt.", style = art.text(16f), color = f.nebentext)
        } else {
            WertZeile(art, art.werteKopf[0], art.werteKopf[1], art.werteKopf[2], kopf = true)
            zeilen.forEach { WertZeile(art, it.name, it.jePortion, it.gesamt) }
        }
        fehlendeWerte(z.voll)?.let { Text(it, Modifier.padding(top = 6.dp), style = art.text(14.5f), color = f.nebentext) }
        z.voll.rezept.notes?.let { Text("Notiz: $it", Modifier.padding(top = 8.dp), style = art.text(15f), color = f.nebentext) }
        z.voll.rezept.sourceUrl?.let { Quelle(art, it) }
    }
}

@Composable
private fun WertZeile(art: RezeptArt, name: String, je: String, gesamt: String, kopf: Boolean = false) {
    val f = FoodyTheme.farben
    val schrift = if (kopf) art.klein(11.5f) else art.text(15.5f)
    Row(
        Modifier.fillMaxWidth().then(if (kopf) Modifier else Modifier.drawBehind { drawLine(f.balkenSpur, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) })
            .semantics(mergeDescendants = true) { }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, Modifier.weight(1.2f), style = schrift, color = if (kopf) f.nebentext else f.text)
        Text(je, Modifier.weight(1f), style = schrift, color = if (kopf) f.nebentext else f.text, textAlign = TextAlign.End)
        Text(gesamt, Modifier.weight(1f), style = schrift, color = f.nebentext, textAlign = TextAlign.End)
    }
}

@Composable
private fun Quelle(art: RezeptArt, url: String) {
    val uri = LocalUriHandler.current
    val link = LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = FoodyTheme.farben.primaer, textDecoration = TextDecoration.Underline))) {
        runCatching { uri.openUri(url) }
    }
    Text(
        buildAnnotatedString {
            append("Quelle: ")
            withLink(link) { append(url) }
        },
        Modifier.padding(top = 6.dp), style = art.text(14.5f), color = FoodyTheme.farben.nebentext,
    )
}

/** Unten über der Navigation: Kochen, Einkaufsliste und Favorit; meldet ihre Höhe für den Platz darüber. */
@Composable
private fun BoxScope.Knopfleiste(art: RezeptArt, z: DetailZustand.Da, a: DetailAktionen, onHoehe: (Dp) -> Unit) {
    val f = FoodyTheme.farben
    val d = LocalDensity.current
    val fav = z.voll.rezept.favorite
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { onHoehe(with(d) { it.height.toDp() }) }
            .background(Brush.verticalGradient(listOf(Color.Transparent, f.grund.copy(alpha = 0.92f), f.grund)))
            .safeDrawingPadding()
            .padding(top = 16.dp, bottom = ueberLeiste(LEISTE_REGELHOEHE)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StilAktion(art.kochen, { a.onKochen(z.portionen) }, Modifier.weight(1.4f), beschreibung = "Kochen", voll = true)
            StilAktion(art.liste, { a.onEinkauf(z.portionen) }, Modifier.weight(1f), beschreibung = "Auf die Einkaufsliste")
            StilAktion(
                if (fav) art.favorit.first else art.favorit.second, { a.onFavorit(!fav) },
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp), beschreibung = if (fav) "Favorit entfernen" else "Als Favorit merken",
            )
        }
    }
}

/** Die Einordnung groß geschrieben, wie die Zeilen über dem Titel in den Entwürfen. */
internal fun einordnungGross(z: DetailZustand.Da): String? = einordnungZeile(z)?.uppercase(Locale.GERMAN)
