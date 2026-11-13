package de.foody.feature.rezepte.editor.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAbschnitt
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilFeld
import de.foody.core.designsystem.stil.StilKarte
import de.foody.core.designsystem.stil.TitelArt
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.stilTitel
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.ImportedIngredient
import de.foody.domain.MeasureUnit
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.editor.EditorZustand
import de.foody.feature.rezepte.editor.Einordnungswahl
import de.foody.feature.rezepte.editor.FotoWahl
import de.foody.feature.rezepte.editor.ZeilenStatus

/** Wörter und Schriften des Editors je Stil R–V wie in den Entwürfen. */
internal class EditorArt(
    val neu: String,
    val bearbeiten: String,
    val ueberNeu: String,
    val zutaten: String,
    val zubereitung: String,
    val portionen: String,
    val speichern: String,
    val text: TextStyle,
    val klein: TextStyle,
)

internal fun editorArt(stil: FoodyStil): EditorArt = when (stil) {
    FoodyStil.ANIME -> EditorArt(
        "Rezept-Edit!", "Rezept-Edit!", "Rezept", "Zutaten", "Schritte", "Portionen", "Speichern ✓",
        Anime.text(15f), Anime.text(12f),
    )
    FoodyStil.EDO -> EditorArt("Neues Rezept", "Bearbeiten", "Rezept", "Zutaten", "Zubereitung", "Portionen", "SPEICHERN", Edo.text(15.5f), Edo.text(12f))
    FoodyStil.BAYRISCH -> EditorArt(
        "Neis Rezept", "Bearbeiten", "Rezept", "Zuadaten", "So geht’s", "Portiona", "Obacht, speichern!",
        Bayrisch.text(15.5f), Bayrisch.text(12.5f),
    )
    FoodyStil.GRAUEN -> EditorArt(
        "Neue Abschrift", "Abschrift berichtigen", "Ein neuer Band für die Bibliothek", "Zutaten", "Zubereitung", "Portionen",
        "In die Bibliothek stellen", Grauen.maschine(14f), Grauen.maschine(11.5f),
    )
    else -> EditorArt(
        "Rezept anlegen", "Rezept bearbeiten", "NEUES SYSTEM KARTIEREN", "Ressourcen", "Missionsablauf", "Crew", "Ins Archiv speichern",
        Kreuzer.titel(15f, FontWeight.Medium, 0f), Kreuzer.mono(11.5f, 0.02f),
    )
}

/** „400 g“, „2“ – Menge einer erkannten Zutatenzeile wie in der Vorschau. */
internal fun mengeText(z: ImportedIngredient): String? = z.amount?.let { m ->
    val zahl = m.stripTrailingZeros().toPlainString().replace('.', ',')
    if (z.unit == null || z.unit == MeasureUnit.PIECE) zahl else "$zahl ${z.unit?.symbol}"
}

/**
 * Editor der Stile R–V in Immersiv wie in den Entwürfen: Kopf mit ✕, Überzeile und Titel des Stils (Edo mit Stempel
 * „新“ bei neuen Rezepten), Grunddaten auf einer Karte mit Etiketten über den Feldern, Foto und Einordnung, Abschnitte
 * „Zutaten“ (mit den erkannten Zeilen) und „Zubereitung“, Notizen und Quelle; unten „Verwerfen“ und „Speichern“.
 * Dieselben Felder und Prüfungen wie sonst; TalkBack hört die normalen Begriffe.
 */
@Composable
internal fun EditorStil(
    neu: Boolean,
    z: EditorZustand,
    hinweise: List<Pair<String, Boolean>>,
    speichernMoeglich: Boolean,
    onZurueck: () -> Unit,
    onSpeichern: () -> Unit,
    vm: EditorViewModel,
) {
    val stil = FoodyTheme.stil.stil
    val a = editorArt(stil)
    val f = FoodyTheme.farben
    val dichte = LocalDensity.current
    var leiste by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding()
                .widthIn(max = FoodyBreiten.inhalt).padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2)
                .padding(bottom = with(dichte) { leiste.toDp() } + FoodyAbstaende.s4),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Kopf(neu, a, onZurueck)
            hinweise.forEach { (text, fehler) ->
                Text(text, style = a.text, color = if (fehler) MaterialTheme.colorScheme.error else f.primaer)
            }
            if (z.geladen) Felder(z, a, vm)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(f.grund).navigationBarsPadding().imePadding()
                .onSizeChanged { leiste = it.height }.widthIn(max = FoodyBreiten.inhalt)
                .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3),
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StilAktion("Verwerfen", onZurueck, Modifier.weight(1f))
            StilAktion(a.speichern, onSpeichern, Modifier.weight(1.5f), beschreibung = "Speichern", voll = true, aktiv = speichernMoeglich)
        }
    }
}

@Composable
private fun Kopf(neu: Boolean, a: EditorArt, onZurueck: () -> Unit) {
    val f = FoodyTheme.farben
    val stil = FoodyTheme.stil.stil
    val normal = if (neu) "Neues Rezept" else "Bearbeiten"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        IconButton(onClick = onZurueck) { Icon(Icons.Outlined.Close, contentDescription = "Abbrechen", tint = f.text) }
        Column(Modifier.weight(1f)) {
            Text(if (neu) a.ueberNeu else "Rezept", style = a.klein, color = f.nebentext)
            Text(
                if (neu) a.neu else a.bearbeiten,
                Modifier.ueberschrift().semantics { contentDescription = normal },
                style = stilTitel(TitelArt.SEITE_ZURUECK) ?: a.text,
                color = f.text,
            )
        }
        if (neu && stil == FoodyStil.EDO) {
            Box(Modifier.size(34.dp).rotate(-6f).background(edoShu(), Edo.form).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Text("新", style = Edo.titel(17f), color = Color.White)
            }
        }
    }
}

@Composable
private fun Felder(z: EditorZustand, a: EditorArt, vm: EditorViewModel) {
    val e = z.eingaben
    StilKarte {
        StilFeld("Name", e.name, { v -> vm.aendern { it.copy(name = v) } }, satzanfangGross = true)
        Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            StilFeld(a.portionen, e.portionen, { v -> vm.aendern { it.copy(portionen = v) } }, Modifier.weight(1f), KeyboardType.Number)
            StilFeld("Vorb. Min.", e.vorbereitung, { v -> vm.aendern { it.copy(vorbereitung = v) } }, Modifier.weight(1f), KeyboardType.Number)
            StilFeld("Kochen Min.", e.kochzeit, { v -> vm.aendern { it.copy(kochzeit = v) } }, Modifier.weight(1f), KeyboardType.Number)
        }
        StilFeld("Tags (mit Komma)", e.tags, { v -> vm.aendern { it.copy(tags = v) } })
    }
    FotoWahl(e.bild, vm)
    Einordnungswahl(z, vm)
    StilAbschnitt(a.zutaten, beschreibung = "Zutaten")
    StilFeld("Eine Zutat je Zeile, Menge zuerst", e.zutatenText, { v -> vm.aendern { it.copy(zutatenText = v) } }, einzeilig = false, zeilen = 4)
    z.zeilen.forEach { ZutatZeile(it, a) }
    StilAbschnitt(a.zubereitung, beschreibung = "Zubereitung")
    StilFeld("Ein Schritt je Zeile", e.schritteText, { v -> vm.aendern { it.copy(schritteText = v) } }, einzeilig = false, zeilen = 4)
    StilFeld("Notizen", e.notizen, { v -> vm.aendern { it.copy(notizen = v) } }, einzeilig = false, zeilen = 2)
    StilFeld("Quelle (Link)", e.quelle, { v -> vm.aendern { it.copy(quelle = v) } }, tastatur = KeyboardType.Uri)
}

/** Eine Zutatenzeile wie im Entwurf: Name, darunter „neu“ oder der Fehler (rot), rechts die Menge. */
@Composable
private fun ZutatZeile(s: ZeilenStatus, a: EditorArt) {
    val f = FoodyTheme.farben
    val linie = f.nebentext.copy(alpha = 0.35f)
    val erkannt = s.erkannt
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).drawBehind {
            drawLine(linie, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
    ) {
        Column(Modifier.weight(1f)) {
            Text(erkannt?.let { listOfNotNull(it.name, it.note).joinToString(" · ") } ?: s.zeile, style = a.text, color = f.text)
            val hinweis = s.fehler ?: if (!s.imKatalog) "neu im Katalog" else null
            if (hinweis != null) {
                Text(hinweis, style = a.klein, color = if (s.fehler != null) MaterialTheme.colorScheme.error else f.nebentext)
            }
        }
        erkannt?.let(::mengeText)?.let { Text(it, style = a.text, color = if (FoodyTheme.stil.stil == FoodyStil.KREUZER) FoodyTheme.stil.zier else f.text) }
    }
}
