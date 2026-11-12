package de.foody.feature.rezepte.detail.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LEISTE_REGELHOEHE
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.sage.sageLinieUnten
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.detail.DetailAktionen
import de.foody.feature.rezepte.detail.DetailZustand
import de.foody.feature.rezepte.detail.fehlendeWerte
import de.foody.feature.rezepte.detail.kcalJePortion
import de.foody.feature.rezepte.detail.mengeText
import de.foody.feature.rezepte.detail.naehrwertZeilen
import de.foody.feature.rezepte.detail.zeit

/** Die drei Reiter des Entwurfs: Wort der Sage und der normale Begriff für TalkBack. */
private enum class Reiter(val wort: String, val begriff: String) {
    ZUTATEN("ZUTATEN", "Zutaten"),
    RITUAL("RITUAL", "Zubereitung"),
    WERTE("WERTE", "Nährwerte"),
}

/**
 * Rezept der Düsteren Sage in Immersiv (Entwurf `Q-Rezept`): Kopf „Aus dem Grimoire“, Raute mit Anfangsbuchstaben,
 * Titel und Angaben, Portionen, die Reiter Zutaten · Ritual · Werte und unten „Am Feuer kochen“, „Auf die Liste“ und
 * die Favoriten-Raute. Dieselben [DetailAktionen] wie die normale Ansicht; Archivieren, Duplizieren und Bearbeiten
 * liegen im Menü „⋯“.
 */
@Composable
internal fun RezeptSage(z: DetailZustand.Da, a: DetailAktionen) {
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = ueberLeiste(LEISTE_REGELHOEHE + 92.dp)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Kopf(z, a)
                if (z.voll.rezept.archivedAt != null) Archiviert { a.onArchivieren(false) }
                Titel(z)
                Portionen(z.portionen, a.onPortionen)
                Reiterleiste(Reiter.entries[reiter]) { reiter = it.ordinal }
                when (Reiter.entries[reiter]) {
                    Reiter.ZUTATEN -> Zutaten(z)
                    Reiter.RITUAL -> Ritual(z.voll.schritte)
                    Reiter.WERTE -> Werte(z)
                }
            }
        }
        Knopfleiste(z, a)
    }
}

@Composable
private fun Kopf(z: DetailZustand.Da, a: DetailAktionen) {
    val glut = FoodyTheme.stil.zier
    var menue by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Zeichenknopf("‹", "Zurück", a.onZurueck)
        Text(
            "AUS DEM GRIMOIRE", Modifier.weight(1f), style = Sage.titel(11f, 0.24f, FontWeight.Medium),
            color = FoodyTheme.farben.nebentext, textAlign = TextAlign.Center,
        )
        Box {
            Zeichenknopf("⋯", "Weitere Aktionen") { menue = true }
            DropdownMenu(menue, onDismissRequest = { menue = false }) {
                Eintrag("Neu niederschreiben", "Bearbeiten") { menue = false; a.onBearbeiten() }
                Eintrag("Abschrift anfertigen", "Duplizieren") { menue = false; a.onDuplizieren() }
                if (z.voll.rezept.archivedAt == null) {
                    Eintrag("Ins Archiv verbannen", "Archivieren", farbe = WARNROT) { menue = false; a.onArchivieren(true) }
                }
            }
        }
    }
    // Feine Goldlinie wie der Rahmen des Menüs im Entwurf
    Box(Modifier.fillMaxWidth().sageLinieUnten(glut.copy(alpha = 0.25f)))
}

@Composable
private fun Eintrag(text: String, begriff: String, farbe: Color? = null, onKlick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = Sage.text(17f), color = farbe ?: FoodyTheme.farben.text) },
        onClick = onKlick,
        modifier = Modifier.semantics { contentDescription = begriff },
    )
}

@Composable
private fun Zeichenknopf(zeichen: String, beschreibung: String, onKlick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onKlick).semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) { Text(zeichen, style = Sage.titel(22f, 0f), color = FoodyTheme.stil.zier) }
}

@Composable
private fun Archiviert(onZurueckholen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, WARNROT.copy(alpha = 0.6f)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Dieses Rezept ruht im Archiv.", Modifier.weight(1f), style = Sage.text(15f), color = WARNROT)
        Box(
            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onZurueckholen)
                .semantics { contentDescription = "Wiederherstellen" }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("ZURÜCKHOLEN", style = Sage.titel(12f, 0.1f), color = FoodyTheme.farben.primaer) }
    }
}

@Composable
private fun Titel(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val r = z.voll.rezept
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(12.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).rotate(45f).shadow(16.dp, RectangleShape, ambientColor = glut, spotColor = glut).background(f.grund).border(1.5.dp, glut))
            Text(r.name.trim().firstOrNull()?.uppercase() ?: "?", style = Sage.titel(26f, 0f), color = glut)
        }
        Text(r.name, style = Sage.titel(22f, 0.03f), color = sageHell(), textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        val angaben = listOfNotNull(zeit(r.prepMinutes, r.cookMinutes), kcalJePortion(z.voll), if (r.favorite) "♦ Favorit" else null)
        if (angaben.isNotEmpty()) Text(angaben.joinToString(" · "), style = Sage.text(15f), color = f.nebentext, textAlign = TextAlign.Center)
        einordnungGross(z)?.let { Text(it, style = Sage.titel(10.5f, 0.14f), color = f.nebentext, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun Portionen(n: Int, onSchritt: (Boolean) -> Unit) {
    val f = FoodyTheme.farben
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
            drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SageNebenknopf("−", "Weniger Portionen", { onSchritt(false) }, Modifier.width(52.dp), schrift = Sage.titel(20f, 0f))
        Text(
            if (n == 1) "1 Portion" else "$n Portionen",
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = Sage.titel(16f, 0.08f), color = f.text, textAlign = TextAlign.Center,
        )
        SageNebenknopf("+", "Mehr Portionen", { onSchritt(true) }, Modifier.width(52.dp), schrift = Sage.titel(20f, 0f))
    }
}

@Composable
private fun Reiterleiste(aktiv: Reiter, onWahl: (Reiter) -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Row(Modifier.fillMaxWidth().sageLinieUnten(f.lichtkante).selectableGroup()) {
        Reiter.entries.forEach { r ->
            val an = r == aktiv
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(an, role = Role.Tab) { onWahl(r) }
                    .semantics { contentDescription = r.begriff }
                    .drawBehind { if (an) drawLine(glut, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(r.wort, style = Sage.titel(12f, 0.12f), color = if (an) sageHell() else f.nebentext, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Zutaten(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    Column {
        z.voll.zeilen.forEach { zeile ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .drawBehind {
                        val punkte = PathEffect.dashPathEffect(floatArrayOf(2f, 3f))
                        drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = punkte)
                    }
                    .semantics(mergeDescendants = true) { }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(zeile.name + if (zeile.optional) " (optional)" else "", style = Sage.text(16.5f), color = f.text)
                    zeile.hinweis?.let { Text(it, style = Sage.text(14.5f, kursiv = true), color = f.nebentext) }
                }
                Text(mengeText(z, zeile), style = Sage.text(16.5f), color = f.nebentext, textAlign = TextAlign.End)
            }
        }
        if (z.voll.zeilen.isEmpty()) Text("Noch keine Zutaten verzeichnet.", style = Sage.text(16f, kursiv = true), color = f.nebentext)
    }
}

@Composable
private fun Ritual(schritte: List<String>) {
    val f = FoodyTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        schritte.forEachIndexed { i, s ->
            Row(Modifier.semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    Sage.roemisch(i + 1), Modifier.width(34.dp).clearAndSetSemantics { contentDescription = "Schritt ${i + 1}" },
                    style = Sage.titel(15f, 0f), color = FoodyTheme.stil.zier,
                )
                Text(s, Modifier.weight(1f), style = Sage.text(16f), color = f.text)
            }
        }
        if (schritte.isEmpty()) Text("Für dieses Rezept ist kein Ritual verzeichnet.", style = Sage.text(16f, kursiv = true), color = f.nebentext)
    }
}

@Composable
private fun Werte(z: DetailZustand.Da) {
    val f = FoodyTheme.farben
    val zeilen = naehrwertZeilen(z.voll, z.portionen)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (zeilen.isEmpty()) {
            Text("Für dieses Rezept sind keine Werte bekannt.", style = Sage.text(16f, kursiv = true), color = f.nebentext)
        } else {
            WertZeile("WERT", "JE PORTION", if (z.portionen == 1) "1 PORTION" else "${z.portionen} PORTIONEN", kopf = true)
            zeilen.forEach { WertZeile(it.name, it.jePortion, it.gesamt) }
        }
        fehlendeWerte(z.voll)?.let { Text(it, Modifier.padding(top = 6.dp), style = Sage.text(14.5f), color = f.nebentext) }
        z.voll.rezept.notes?.let { Text("„$it“", Modifier.padding(top = 8.dp), style = Sage.text(14.5f, kursiv = true), color = f.nebentext) }
        z.voll.rezept.sourceUrl?.let { Quelle(it) }
    }
}

@Composable
private fun WertZeile(name: String, je: String, gesamt: String, kopf: Boolean = false) {
    val f = FoodyTheme.farben
    val schrift = if (kopf) Sage.titel(10.5f, 0.14f) else Sage.text(16f)
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
private fun Quelle(url: String) {
    val uri = LocalUriHandler.current
    val link = LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = FoodyTheme.farben.primaer, textDecoration = TextDecoration.Underline))) {
        runCatching { uri.openUri(url) }
    }
    Text(
        buildAnnotatedString {
            append("Quelle: ")
            withLink(link) { append(url) }
        },
        Modifier.padding(top = 6.dp), style = Sage.text(14.5f), color = FoodyTheme.farben.nebentext,
    )
}

/** Unten über der Navigation: „Am Feuer kochen“, „Auf die Liste“ und die Favoriten-Raute. */
@Composable
private fun BoxScope.Knopfleiste(z: DetailZustand.Da, a: DetailAktionen) {
    val f = FoodyTheme.farben
    val fav = z.voll.rezept.favorite
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, f.grund.copy(alpha = 0.92f), f.grund)))
            .safeDrawingPadding()
            .padding(top = 16.dp, bottom = ueberLeiste(LEISTE_REGELHOEHE)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SageHauptknopf("AM FEUER KOCHEN", "Kochen", { a.onKochen(z.portionen) }, Modifier.weight(1.4f), schrift = Sage.titel(13f, 0.12f))
            SageNebenknopf(
                "AUF DIE LISTE", "Auf die Einkaufsliste", { a.onEinkauf(z.portionen) }, Modifier.weight(1f).heightIn(min = 52.dp),
                schrift = Sage.titel(12f, 0.1f),
            )
            SageNebenknopf(
                if (fav) "♦" else "◇", if (fav) "Favorit entfernen" else "Als Favorit merken", { a.onFavorit(!fav) },
                Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp), akzent = fav, schrift = Sage.titel(20f, 0f),
            )
        }
    }
}

/** Rotbraun für „Archiv“ wie im Entwurf (#E89A84 auf dunklem Grund, AA). */
private val WARNROT = Color(0xFFE89A84)
