package de.foody.feature.kochen.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.SageRaute
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.sage.sageLinieUnten
import de.foody.domain.StepTimer
import de.foody.feature.kochen.KochAktionen
import de.foody.feature.kochen.KochSchritt
import de.foody.feature.kochen.KochZustand
import de.foody.feature.kochen.restzeit

/*
 * Kochmodus der Düsteren Sage in Immersiv (Entwurf `Q-Kochen`): Kopf mit ✕, „Vorbereitung“ bzw. „Schritt 2 / 5“ über
 * dem Rezept, schmale Segmente; „Lege bereit“ mit Rauten zum Abhaken, je Schritt die römische Zahl groß im Akzent und
 * der Text in EB Garamond, Zeitangaben als Sanduhr. Unten „‹“ und „Beginnen“/„Weiter“/„Vollenden“. Dieselben
 * [KochAktionen], derselbe Pager und dasselbe Fertig-Blatt wie die normale Ansicht.
 */

/** Kopf: ✕ zum Schließen, Überzeile und Rezeptname mittig, darunter die Segmente des Fortschritts. */
@Composable
internal fun KochKopfSage(z: KochZustand.Da, seite: Int, onSchliessen: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val nummer = seite - if (z.hatBereitlegen) 1 else 0
    val ueber = if (z.schritt(seite) == null) "VORBEREITUNG" else "SCHRITT ${nummer + 1} / ${z.schritte.size}"
    Column(Modifier.padding(start = 12.dp, end = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onSchliessen)
                    .semantics { contentDescription = "Kochen beenden" },
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = Sage.text(20f), color = f.nebentext) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ueber, style = Sage.titel(11f, 0.22f, FontWeight.Medium), color = f.nebentext, textAlign = TextAlign.Center)
                val port = if (z.portionen == 1) "1 Port." else "${z.portionen} Port."
                Text("${z.name} · $port", style = Sage.text(16f), color = f.text, textAlign = TextAlign.Center, maxLines = 2)
            }
            Box(Modifier.size(48.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(z.seitenzahl) { i -> Box(Modifier.weight(1f).height(3.dp).background(if (i <= seite) glut else f.balkenSpur)) }
        }
    }
}

/** „Lege bereit“: Zahl der bereiten Zutaten, Portionen und die Zutaten mit Raute zum Abhaken. */
@Composable
internal fun BereitlegenSage(z: KochZustand.Da, a: KochAktionen) {
    val f = FoodyTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LEGE BEREIT", Modifier.semantics { heading(); contentDescription = "Bereitlegen" }, style = Sage.titel(24f, 0.08f), color = f.primaer)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${z.abgehakt.size} von ${z.bereit.size} bereit", Modifier.weight(1f), style = Sage.text(16f), color = f.nebentext)
            if (z.abgehakt.isNotEmpty()) SageNebenknopf("VON VORN", "Alle zurücksetzen", a.onAlleZuruecksetzen)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SageNebenknopf("−", "Weniger Portionen", { a.onPortionen(false) }, Modifier.width(52.dp), schrift = Sage.titel(20f, 0f))
            Text(
                if (z.portionen == 1) "1 Portion" else "${z.portionen} Portionen", Modifier.weight(1f),
                style = Sage.titel(16f, 0.08f), color = f.text, textAlign = TextAlign.Center,
            )
            SageNebenknopf("+", "Mehr Portionen", { a.onPortionen(true) }, Modifier.width(52.dp), schrift = Sage.titel(20f, 0f))
        }
        Column {
            z.bereit.forEach { zeile ->
                val bereit = zeile.zeilenIndex in z.abgehakt
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).sageLinieUnten(f.balkenSpur)
                        .toggleable(bereit, role = Role.Checkbox, onValueChange = { a.onAbhaken(zeile.zeilenIndex) })
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { SageRaute(10.dp, gefuellt = bereit) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            zeile.name + if (zeile.optional) " (optional)" else "",
                            style = Sage.text(17f).copy(textDecoration = if (bereit) TextDecoration.LineThrough else null),
                            color = if (bereit) f.nebentext else f.text,
                        )
                        zeile.hinweis?.let { Text(it, style = Sage.text(14.5f, kursiv = true), color = f.nebentext) }
                    }
                    zeile.menge?.let { Text(it, style = Sage.text(15f), color = f.nebentext, textAlign = TextAlign.End) }
                }
            }
        }
    }
}

/** Ein Schritt: römische Zahl mit Glanz, „Schritt 2 von 5“, der Text groß, die genannten Zutaten und je Zeit eine Sanduhr. */
@Composable
internal fun SchrittSage(s: KochSchritt, nummer: Int, gesamt: Int, abgehakt: Set<Int>, a: KochAktionen, naechster: String?) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            Sage.roemisch(nummer), Modifier.clearAndSetSemantics { },
            style = Sage.titel(64f, 0f).copy(shadow = Shadow(glut.copy(alpha = 0.5f), Offset.Zero, 24f)), color = glut,
        )
        Text("SCHRITT $nummer VON $gesamt", Modifier.semantics { heading() }, style = Sage.titel(12f, 0.2f), color = f.nebentext)
        Text(s.text, style = Sage.text(26f), color = sageHell())
        if (s.zutaten.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp)) {
                Text("DU BRAUCHST", style = Sage.titel(10.5f, 0.16f), color = f.nebentext)
                s.zutaten.forEach { z ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 36.dp).sageLinieUnten(f.balkenSpur).semantics(mergeDescendants = true) { },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val bereit = z.zeilenIndex in abgehakt
                        Box(Modifier.size(16.dp).semantics { if (bereit) contentDescription = "bereitgelegt" }, contentAlignment = Alignment.Center) {
                            SageRaute(8.dp, gefuellt = z.zeilenIndex in abgehakt)
                        }
                        Text(z.name, Modifier.weight(1f), style = Sage.text(16.5f), color = f.text)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(z.menge ?: "nach Bedarf", style = Sage.text(16f), color = f.primaer)
                            z.gesamt?.let { Text("von $it", style = Sage.text(13.5f), color = f.nebentext) }
                        }
                    }
                }
            }
        }
        s.timer.forEach { t -> Sanduhr(t) { a.onTimerStarten(t) } }
        naechster?.let {
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().border(1.dp, f.balkenSpur).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("ALS NÄCHSTES", style = Sage.titel(10.5f, 0.16f), color = f.nebentext)
                Text(it, style = Sage.text(15.5f, kursiv = true), color = f.nebentext, maxLines = 2)
            }
        }
    }
}

/** Zeitangabe als Knopf mit Sanduhr: „Sanduhr: Rösten“, „Tippen, um die Sanduhr zu drehen“, rechts die Dauer. */
@Composable
private fun Sanduhr(t: StepTimer, onStart: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val pfade = remember { SanduhrPfade() }
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 64.dp).border(1.dp, glut).background(glut.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onStart)
            .semantics(mergeDescendants = true) { contentDescription = "Timer ${t.label} starten, ${restzeit(t.duration.toMillis())}" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(width = 26.dp, height = 34.dp)) {
            scale(size.width / 26f, Offset.Zero) {
                drawPath(pfade.glas, glut, style = Stroke(1.6f))
                drawPath(pfade.sand, glut)
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Sanduhr: ${t.label}", style = Sage.text(17f), color = f.text)
            Text("Tippen, um die Sanduhr zu drehen", style = Sage.text(14f), color = f.nebentext)
        }
        Text(restzeit(t.duration.toMillis()), style = Sage.titel(24f, 0f), color = sageHell())
    }
}

private class SanduhrPfade {
    private fun pfad(d: String) = PathParser().parsePathString(d).toPath()
    val glas = pfad("M3 2h20M3 32h20M5 2c0 10 16 10 16 15S5 22 5 32M21 2c0 10-16 10-16 15s16 5 16 15")
    val sand = pfad("M9 28h8l-4-5z")
}

/** Unten: „‹“ zurück und der Hauptknopf „Beginnen“, „Weiter“ oder „Vollenden“. */
@Composable
internal fun KochKnoepfeSage(seite: Int, seitenzahl: Int, onSeite: (Int) -> Unit, onFertig: () -> Unit) {
    val letzte = seite >= seitenzahl - 1
    val text = when {
        letzte -> "VOLLENDEN"
        seite == 0 -> "BEGINNEN"
        else -> "WEITER"
    }
    val begriff = if (letzte) "Fertig" else "Weiter"
    // Ab 130 % Schrift bekommt „‹“ mehr Platz nicht, der Hauptknopf bricht dann eher um
    val breite = if (LocalDensity.current.fontScale > 1.2f) 56.dp else 64.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SageNebenknopf(
            "‹", "Zurück", { if (seite > 0) onSeite(seite - 1) }, Modifier.width(breite).heightIn(min = 56.dp),
            schrift = Sage.titel(22f, 0f),
        )
        SageHauptknopf(text, begriff, { if (letzte) onFertig() else onSeite(seite + 1) }, Modifier.weight(1f).heightIn(min = 56.dp))
    }
}
