package de.foody.feature.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.sync.ErsterSync

object Verbinden {
    const val LAEUFT = "Verbinde …"
    const val TRENNEN_BESTAETIGEN = "Ja, trennen"
    const val INHALT = "verbinden-inhalt"

    /** Kennung: angemeldet an anderem Server/Konto, während eine Verbindung besteht. */
    const val ERST_TRENNEN = "erst_trennen"
}

@Composable
fun VerbindenRoute(onZurueck: () -> Unit, vm: VerbindenViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    VerbindenScreen(ui, vm, onZurueck)
}

/** Nicht verbunden: Anmeldeformular (und ggf. die Wahl beim ersten Abgleich); verbunden: Status, Jetzt, Trennen. */
@Composable
private fun VerbindenScreen(ui: VerbindenUi, vm: VerbindenViewModel, onZurueck: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        VerbindenInhalt(ui, vm, onZurueck)
    }
}

@Composable
private fun VerbindenInhalt(ui: VerbindenUi, vm: VerbindenViewModel, onZurueck: () -> Unit) {
    val verbindung = ui.verbindung?.takeIf { it.verbunden }
    Column(
        Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
            .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = ueberLeiste(104.dp)).testTag(Verbinden.INHALT),
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZurueck) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
            }
            Column {
                Augenbraue("Einstellungen")
                Text("Server verbinden", Modifier.ueberschrift(), style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            }
        }
        when {
            ui.schritt == Schritt.WAHL -> ErsterAbgleich(vm)
            verbindung != null && !ui.neuAnmelden -> Verbunden(verbindung, vm, onNeuAnmelden = { vm.neuAnmelden(true) })
            else -> Anmelden(ui, vorbelegt = verbindung?.adresse.orEmpty(), vm)
        }
    }
}

@Composable
private fun Anmelden(ui: VerbindenUi, vorbelegt: String, vm: VerbindenViewModel) {
    var adresse by rememberSaveable { mutableStateOf(vorbelegt) }
    var benutzer by rememberSaveable { mutableStateOf("") }
    // Das Passwort überlebt bewusst keinen Prozess-Neustart (nicht rememberSaveable)
    var passwort by remember { mutableStateOf("") }
    val laeuft = ui.schritt == Schritt.LAEUFT
    Text(
        "Melde dich mit deinem Foody-Konto an. Danach gleicht die App Rezepte, Plan, Einkauf und Tagebuch mit dem Server ab.",
        style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
    )
    FoodyTextfeld("Server-Adresse", adresse, tastatur = KeyboardType.Uri) { adresse = it }
    FoodyTextfeld("Benutzername", benutzer) { benutzer = it }
    OutlinedTextField(
        passwort, { passwort = it }, Modifier.fillMaxWidth(), label = { Text("Passwort") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    ui.fehler?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    if (laeuft) Text(Verbinden.LAEUFT, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
    FoodyKnopf(
        "Anmelden", { vm.anmelden(adresse, benutzer, passwort) }, Modifier.fillMaxWidth(),
        aktiv = !laeuft && adresse.isNotBlank() && benutzer.isNotBlank() && passwort.isNotEmpty(),
    )
}

/** Wahl beim ersten Abgleich, wenn auf dem Gerät schon Daten liegen. */
@Composable
private fun ErsterAbgleich(vm: VerbindenViewModel) {
    Text("Auf diesem Gerät gibt es schon Daten.", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
    Wahl("Zusammenführen", "Deine Rezepte, Pläne, Listen und Einträge von diesem Gerät kommen zu denen auf dem Server dazu.") {
        vm.waehlen(ErsterSync.ZUSAMMENFUEHREN)
    }
    Wahl("Ersetzen", "Die Daten auf diesem Gerät werden durch die des Servers ersetzt. Zutaten bleiben.") {
        vm.waehlen(ErsterSync.ERSETZEN)
    }
    FoodyKnopf("Abbrechen", vm::wahlAbbrechen, Modifier.fillMaxWidth(), art = KnopfArt.TEXT)
}

@Composable
private fun Wahl(titel: String, erklaerung: String, onWahl: () -> Unit) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Text(erklaerung, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
            FoodyKnopf(titel, onWahl, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
        }
    }
}

@Composable
private fun Verbunden(z: VerbindungsZustand, vm: VerbindenViewModel, onNeuAnmelden: () -> Unit) {
    var fragen by remember { mutableStateOf(false) }
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Text("Verbunden mit ${z.adresse.orEmpty()}", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            Text(
                "Zuletzt synchronisiert: ${zuletztText(z.letzterSync, System.currentTimeMillis())}",
                style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
            )
            z.letzterFehler?.let { Text(syncFehlerText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
    FoodyKnopf("Jetzt synchronisieren", vm::jetzt, Modifier.fillMaxWidth())
    if (z.letzterFehler == "anmeldung_abgelaufen") FoodyKnopf("Neu anmelden", onNeuAnmelden, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
    FoodyKnopf("Trennen", { fragen = true }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
    if (fragen) {
        FoodyFrage(
            "Sync trennen?", "Dieses Gerät wird beim Server abgemeldet. Die Daten auf dem Gerät bleiben erhalten.",
            Verbinden.TRENNEN_BESTAETIGEN, onJa = { fragen = false; vm.trennen() }, onNein = { fragen = false },
        )
    }
}
