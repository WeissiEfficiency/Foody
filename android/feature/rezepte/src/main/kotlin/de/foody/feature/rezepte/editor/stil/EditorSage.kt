package de.foody.feature.rezepte.editor.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.platzFuerLeiste
import de.foody.core.designsystem.stil.StilFeld
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.editor.EditorZustand
import de.foody.feature.rezepte.editor.Einordnungswahl
import de.foody.feature.rezepte.editor.FotoWahl
import de.foody.feature.rezepte.editor.ZeilenStatus
import de.foody.feature.rezepte.editor.zeilenText

/** Reiter des Editors der Sage: Titel wie im Entwurf, für TalkBack der normale Name. */
private enum class SageReiter(val titel: String, val beschreibung: String, val ruhig: String) {
    GRUNDLAGEN("GRUNDLAGEN", "Grundlagen", "ALLGEMEIN"),
    ZUTATEN("ZUTATEN", "Zutaten", "ZUTATEN"),
    RITUAL("RITUAL", "Zubereitung", "SCHRITTE"),
}

/**
 * Editor der Düsteren Sage (Stil Q) in Immersiv wie im Entwurf: Kopf mit ✕ und Titel in Gold („Neue Seite“, beim
 * Bearbeiten „Neu niederschreiben“), Reiter „Grundlagen · Zutaten · Ritual“ mit goldenem Strich unter dem gewählten,
 * unten „Ins Grimoire schreiben“ über der schwebenden Navigation und bei offener Tastatur über dieser. Grundlagen: Bild,
 * Name, Portionen und Zeiten, Tags, Einordnung. Zutaten: das Feld und darunter „Was die Schrift verrät“ (✓ erkannt,
 * ! Fehler). Ritual: Schritte, Notizen, Quelle. Dieselben Felder und Prüfungen wie sonst; TalkBack hört die normalen
 * Begriffe. Den Markdown-Import („Abschrift“) des Entwurfs gibt es hier wie in R–V nicht (eigener Weg über die Liste).
 */
@Composable
internal fun EditorSage(
    neu: Boolean,
    z: EditorZustand,
    hinweise: List<Pair<String, Boolean>>,
    speichernMoeglich: Boolean,
    onZurueck: () -> Unit,
    onSpeichern: () -> Unit,
    vm: EditorViewModel,
) {
    val f = FoodyTheme.farben
    val dichte = LocalDensity.current
    var leiste by remember { mutableIntStateOf(0) }
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    val ueberNavigation = platzFuerLeiste()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding()
                .widthIn(max = FoodyBreiten.inhalt).padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2)
                .padding(bottom = with(dichte) { leiste.toDp() } + ueberNavigation + FoodyAbstaende.s4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Kopf(neu, onZurueck)
            Reiter(SageReiter.entries[reiter]) { reiter = it.ordinal }
            hinweise.forEach { (text, fehler) ->
                Text(text, style = Sage.text(15f, kursiv = true), color = if (fehler) MaterialTheme.colorScheme.error else f.primaer)
            }
            if (z.geladen) {
                when (SageReiter.entries[reiter]) {
                    SageReiter.GRUNDLAGEN -> Grundlagen(z, vm)
                    SageReiter.ZUTATEN -> Zutaten(z, vm)
                    SageReiter.RITUAL -> Ritual(z, vm)
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(f.grund)
                .windowInsetsPadding(WindowInsets.navigationBars.add(WindowInsets(bottom = ueberNavigation)).union(WindowInsets.ime))
                .onSizeChanged { leiste = it.height }.widthIn(max = FoodyBreiten.inhalt)
                .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3),
        ) {
            SageHauptknopf(
                szenenWort("INS GRIMOIRE SCHREIBEN", "SPEICHERN"),
                "Speichern",
                onSpeichern,
                Modifier.heightIn(min = 52.dp),
                schrift = Sage.titel(14f, 0.16f),
                aktiv = speichernMoeglich,
            )
        }
    }
}

/** ✕ (Abbrechen), der Titel mittig in Gold; TalkBack hört „Neues Rezept“ oder „Bearbeiten“. */
@Composable
private fun Kopf(neu: Boolean, onZurueck: () -> Unit) {
    val f = FoodyTheme.farben
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onZurueck) { Icon(Icons.Outlined.Close, contentDescription = "Abbrechen", tint = f.nebentext) }
        Text(
            if (neu) szenenWort("NEUE SEITE", "NEUES REZEPT") else szenenWort("NEU NIEDERSCHREIBEN", "REZEPT BEARBEITEN"),
            Modifier.weight(1f).semantics {
                heading()
                contentDescription = if (neu) "Neues Rezept" else "Bearbeiten"
            },
            style = Sage.titel(19f, 0.1f),
            color = f.primaer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(48.dp))
    }
}

/** Reiterzeile mit feiner Linie darunter; der gewählte hell mit goldenem Strich, die anderen gedämpft. */
@Composable
private fun Reiter(gewaehlt: SageReiter, onWahl: (SageReiter) -> Unit) {
    val f = FoodyTheme.farben
    val gold = FoodyTheme.stil.zier
    val linie = f.lichtkante
    Row(
        Modifier.fillMaxWidth().selectableGroup().drawBehind {
            drawLine(linie, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
        },
    ) {
        SageReiter.entries.forEach { r ->
            val an = r == gewaehlt
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = an, role = Role.Tab, onClick = { onWahl(r) })
                    .semantics { contentDescription = r.beschreibung }
                    .drawBehind {
                        if (an) drawLine(gold, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    szenenWort(r.titel, r.ruhig),
                    Modifier.clearAndSetSemantics {},
                    style = Sage.titel(12f, 0.12f),
                    color = if (an) sageHell() else f.nebentext,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Grundlagen(z: EditorZustand, vm: EditorViewModel) {
    val e = z.eingaben
    FotoWahl(e.bild, vm, form = RectangleShape, hoehe = 150.dp)
    StilFeld("Name", e.name, { v -> vm.aendern { it.copy(name = v) } }, satzanfangGross = true)
    Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        StilFeld("Portionen", e.portionen, { v -> vm.aendern { it.copy(portionen = v) } }, Modifier.weight(1f), KeyboardType.Number)
        StilFeld("Vorb. Min.", e.vorbereitung, { v -> vm.aendern { it.copy(vorbereitung = v) } }, Modifier.weight(1f), KeyboardType.Number)
        StilFeld("Kochen Min.", e.kochzeit, { v -> vm.aendern { it.copy(kochzeit = v) } }, Modifier.weight(1f), KeyboardType.Number)
    }
    StilFeld("Tags (mit Komma)", e.tags, { v -> vm.aendern { it.copy(tags = v) } })
    Einordnungswahl(z, vm)
}

@Composable
private fun Zutaten(z: EditorZustand, vm: EditorViewModel) {
    val e = z.eingaben
    Hinweis(szenenWort("Eine Zutat je Zeile, Menge zuerst.", "Eine Zutat pro Zeile, Menge zuerst."))
    StilFeld(
        "Eine Zutat je Zeile, Menge zuerst",
        e.zutatenText,
        { v -> vm.aendern { it.copy(zutatenText = v) } },
        einzeilig = false,
        zeilen = 6,
        etikettZeigen = false,
    )
    if (z.zeilen.isNotEmpty()) {
        Text(
            szenenWort("WAS DIE SCHRIFT VERRÄT", "VORSCHAU"),
            Modifier.semantics {
                heading()
                contentDescription = "Erkannte Zutaten"
            },
            style = Sage.titel(10.5f, 0.18f, FontWeight.Medium),
            color = FoodyTheme.farben.nebentext,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { z.zeilen.forEach { Zeile(it) } }
    }
}

/** Eine Zeile unter „Was die Schrift verrät“: ✓ mit dem Erkannten, ! mit dem Fehler (in Fehlerfarbe). */
@Composable
private fun Zeile(s: ZeilenStatus) {
    val f = FoodyTheme.farben
    val fehler = s.fehler
    val erkannt = s.erkannt
    val (zeichen, text) = when {
        fehler != null -> "!" to "${s.zeile}: $fehler"
        erkannt != null -> "✓" to zeilenText(erkannt) + if (s.imKatalog) " · im Katalog" else " · neu"
        else -> return
    }
    val farbe = if (fehler != null) MaterialTheme.colorScheme.error else f.nebentext
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(zeichen, Modifier.clearAndSetSemantics {}, style = Sage.titel(13f, 0f), color = farbe)
        Text(text, style = Sage.text(15f, kursiv = fehler == null), color = farbe)
    }
}

@Composable
private fun Ritual(z: EditorZustand, vm: EditorViewModel) {
    val e = z.eingaben
    Hinweis(szenenWort("Ein Schritt je Zeile.", "Ein Schritt pro Zeile."))
    StilFeld(
        "Ein Schritt je Zeile",
        e.schritteText,
        { v -> vm.aendern { it.copy(schritteText = v) } },
        einzeilig = false,
        zeilen = 6,
        etikettZeigen = false,
    )
    StilFeld("Notizen", e.notizen, { v -> vm.aendern { it.copy(notizen = v) } }, einzeilig = false, zeilen = 2)
    StilFeld("Quelle (Link)", e.quelle, { v -> vm.aendern { it.copy(quelle = v) } }, tastatur = KeyboardType.Uri)
    Hinweis(szenenWort("Zeitangaben wie „30 Minuten“ werden im Kochmodus zu Sanduhren.", "Zeitangaben wie „30 Minuten“ werden im Kochmodus zu Timern."))
}

@Composable
private fun Hinweis(text: String) {
    Text(text, style = Sage.text(15f, kursiv = true), color = FoodyTheme.farben.nebentext)
}
