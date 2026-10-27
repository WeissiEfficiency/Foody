package de.foody.feature.einstellungen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.data.Darstellung
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe

/** Ein Abschnitt der Seite: Glasfläche mit Augenbraue. */
@Composable
internal fun Abschnitt(titel: String, inhalt: @Composable ColumnScope.() -> Unit) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth(), form = FoodyFormen.gross) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Augenbraue(titel)
            inhalt()
        }
    }
}

@Composable
private fun Klein(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)

/** Konto: Status, Tipp öffnet „Server verbinden“. */
@Composable
internal fun KontoAbschnitt(konto: VerbindungsZustand?, jetzt: Long, onServer: () -> Unit) = Abschnitt("Konto") {
    val verbunden = konto?.takeIf { it.verbunden }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onServer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (verbunden != null) "Verbunden mit ${verbunden.adresse.orEmpty()}" else "Nicht verbunden",
                style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text,
            )
            Klein(if (verbunden != null) "Zuletzt synchronisiert: ${zuletztText(verbunden.letzterSync, jetzt)}" else "Server verbinden")
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.text)
    }
}

@Composable
internal fun DarstellungAbschnitt(gewaehlt: Darstellung, onWahl: (Darstellung) -> Unit) = Abschnitt("Darstellung") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Darstellung.SYSTEM to "System", Darstellung.HELL to "Hell", Darstellung.DUNKEL to "Dunkel").forEach { (d, text) ->
            FoodyChip(text, ausgewaehlt = d == gewaehlt, onKlick = { onWahl(d) })
        }
    }
}

/** Tagesziel als Zahlenfeld; [fehler] zeigt „500 bis 10.000 kcal“ am Feld. */
@Composable
internal fun TageszielAbschnitt(text: String, fehler: Boolean, onFertig: () -> Unit, onText: (String) -> Unit) = Abschnitt("Tagesziel") {
    OutlinedTextField(
        text, onText, Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onFertig() },
        label = { Text("Tagesziel") },
        suffix = { Text("kcal") },
        singleLine = true,
        isError = fehler,
        supportingText = { Text(if (fehler) "500 bis 10.000 kcal" else "Leer lassen = kein Ziel") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onFertig() }),
    )
}

/** Zutatenkatalog: ganze Zeile mit Anzahl und „›“; darunter der Schalter „Online-Produktsuche“ (ganze Zeile tippbar). */
@Composable
internal fun ZutatenAbschnitt(anzahl: Int?, onlineSuche: Boolean, onZutaten: () -> Unit, onOnlineSuche: (Boolean) -> Unit) = Abschnitt("Zutaten") {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onZutaten),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Zutatenkatalog", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        anzahl?.let { Klein(it.toString()) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.text)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(onlineSuche, role = Role.Switch, onValueChange = onOnlineSuche),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Online-Produktsuche", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            Klein("Open Food Facts; gesendet wird nur die Produktnummer")
        }
        Switch(onlineSuche, onCheckedChange = null)
    }
}

@Composable
internal fun SicherungAbschnitt(z: EinstellungenZustand, onErstellen: () -> Unit, onWiederherstellen: () -> Unit) = Abschnitt("Sicherung") {
    Klein("Rezepte, Zutaten, Plan, Einkauf, Tagebuch und Fotos als ZIP-Datei.")
    FoodyKnopf(
        if (z.laeuft == Aktion.ERSTELLEN) "Läuft …" else "Sicherung erstellen", onErstellen, Modifier.fillMaxWidth(),
        aktiv = z.laeuft == null,
    )
    FoodyKnopf(
        if (z.laeuft == Aktion.WIEDERHERSTELLEN) "Läuft …" else "Wiederherstellen", onWiederherstellen, Modifier.fillMaxWidth(),
        art = KnopfArt.UMRISS, aktiv = z.laeuft == null && !z.gesperrt,
    )
    if (z.gesperrt) Klein(ERST_TRENNEN)
}

@Composable
internal fun DatenAbschnitt(z: EinstellungenZustand, onLoeschen: () -> Unit) = Abschnitt("Daten") {
    FoodyKnopf(
        if (z.laeuft == Aktion.LOESCHEN) "Läuft …" else "Alle Daten löschen", onLoeschen, Modifier.fillMaxWidth(),
        art = KnopfArt.UMRISS, aktiv = z.laeuft == null && !z.gesperrt,
    )
    if (z.gesperrt) Klein(ERST_TRENNEN)
}

@Composable
internal fun HinweiseAbschnitt() = Abschnitt("Hinweise") {
    Klein("Nährwerte sind rechnerische Näherungswerte und ersetzen keine ernährungsmedizinische Beratung.")
    Klein("Ohne verbundenen Server bleiben alle Daten auf diesem Gerät. Mit einem eigenen Server werden sie nur dorthin übertragen.")
}
