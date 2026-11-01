package de.foody.feature.woche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.data.woche.WochenTag
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Abstand des Füllen-Knopfs über der schwebenden Navigationsleiste; die Liste endet darüber. */
private val UEBER_LEISTE = 96.dp
private val LISTE_UNTEN = 168.dp

/**
 * Der Tab „Woche“: eine Woche Mo–So mit Pfeilen zum Blättern, Tage mit Einträgen und Nährwerten (ab 840 dp in zwei
 * Spalten), „+ Mahlzeit“ (Mahlzeit wählen → Rezept wählen), das Mahlzeit-Blatt je Eintrag und „Leere Tage füllen“.
 */
@Composable
fun WocheRoute(onKochen: (String, Int) -> Unit, onRezept: (String) -> Unit, vm: WocheViewModel = hiltViewModel()) {
    val woche by vm.woche.collectAsStateWithLifecycle()
    val ziel by vm.tagesziel.collectAsStateWithLifecycle()
    val zeigtHeute by vm.zeigtHeute.collectAsStateWithLifecycle()
    val karten by vm.rezeptAuswahl.collectAsStateWithLifecycle()
    val fuellen by vm.fuellen.collectAsStateWithLifecycle()
    // Hinzufügen in zwei Schritten: erst Mahlzeit, dann Rezept
    var neuAm by rememberSaveable { mutableStateOf<String?>(null) }
    var auswahlFuer by rememberSaveable { mutableStateOf<String?>(null) }
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    var umbenennen by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val bereich = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val zweispaltig = maxWidth >= FoodyBreiten.zweispaltig
        Column(
            Modifier.widthIn(max = FoodyBreiten.zweiBereiche).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = LISTE_UNTEN),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            Kopf(woche, zeigtHeute, vm::blaettern, vm::heute)
            val tage = woche?.tage.orEmpty()
            val karte = @Composable { tag: WochenTag ->
                TagKarte(tag, tag.datum == vm.heuteDatum, ziel, onEintrag = { offen = it.id }, onPlus = { neuAm = tag.datum.toString() })
            }
            if (zweispaltig) {
                Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) { tage.take(4).forEach { karte(it) } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) { tage.drop(4).forEach { karte(it) } }
                }
            } else {
                tage.forEach { karte(it) }
            }
        }
        // Fest über der schwebenden Navigationsleiste, nicht am Ende der Liste
        FoodyKnopf(
            "Leere Tage füllen", { vm.fuellenOeffnen() },
            Modifier.align(Alignment.BottomCenter).widthIn(max = FoodyBreiten.zweiBereiche).fillMaxWidth()
                .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, bottom = UEBER_LEISTE),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = UEBER_LEISTE + 64.dp))
    }

    neuAm?.let { datum ->
        MahlzeitWahl("Mahlzeit wählen", onWahl = { m -> auswahlFuer = "$datum|$m"; neuAm = null }, onAbbrechen = { neuAm = null })
    }
    auswahlFuer?.let { wert ->
        val (datum, mahlzeit) = wert.split("|", limit = 2)
        RezeptAuswahlBlatt(
            mahlzeit, karten,
            onWahl = { k -> vm.hinzufuegen(LocalDate.parse(datum), mahlzeit, k.id); auswahlFuer = null },
            onSchliessen = { auswahlFuer = null },
        )
    }
    val eintrag: PlanEintrag? = offen?.let { id -> woche?.tage?.flatMap { it.eintraege }?.firstOrNull { it.id == id } }
    if (offen != null && eintrag == null) offen = null
    eintrag?.let { e ->
        MahlzeitBlatt(
            e, woche!!.montag,
            MahlzeitAktionen(
                onPortionen = { vm.portionen(e.id, it) },
                onGekocht = { vm.gekocht(e.id, it) },
                onKochen = { offen = null; onKochen(e.rezeptId, e.portionen) },
                onRezept = { offen = null; onRezept(e.rezeptId) },
                onVerschieben = { vm.verschieben(e.id, it); offen = null },
                onUmbenennen = { umbenennen = e.id; offen = null },
                onEntfernen = {
                    offen = null
                    bereich.launch {
                        val weg = vm.entfernen(e.id) ?: return@launch
                        val antwort = snackbar.showSnackbar("Mahlzeit entfernt", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
                        if (antwort == SnackbarResult.ActionPerformed) vm.wiederherstellen(weg)
                    }
                },
            ),
            onSchliessen = { offen = null },
        )
    }
    umbenennen?.let { id ->
        MahlzeitWahl("Umbenennen", onWahl = { m -> vm.umbenennen(id, m); umbenennen = null }, onAbbrechen = { umbenennen = null })
    }
    fuellen?.let { z ->
        FuellenBlatt(
            z, karten.associate { it.id to it.name },
            FuellenAktionen(
                onMahlzeit = { vm.fuellenOeffnen(it) },
                onAbwaehlen = vm::abwaehlen,
                onTagNeu = { vm.tagNeu(it) },
                onNeuMischen = { vm.neuMischen() },
                onUebernehmen = { bereich.launch { vm.uebernehmen() } },
            ),
            onSchliessen = vm::fuellenSchliessen,
        )
    }
}

@Composable
private fun Kopf(woche: WochenAnsicht?, zeigtHeute: Boolean, onBlaettern: (Long) -> Unit, onHeute: () -> Unit) {
    Text("Woche", Modifier.ueberschrift(), style = MaterialTheme.typography.headlineLarge, color = FoodyTheme.farben.text)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onBlaettern(-1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Vorige Woche", tint = FoodyTheme.farben.text)
        }
        Text(woche?.let { wochenTitel(it.montag) }.orEmpty(), style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
        IconButton(onClick = { onBlaettern(1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Nächste Woche", tint = FoodyTheme.farben.text)
        }
        if (!zeigtHeute) FoodyChip("Heute", ausgewaehlt = false, onKlick = onHeute)
    }
    woche?.durchschnitt?.let {
        Text(
            "Ø ${if (it.vollstaendig) "" else "mind. "}${ganzzahlDeutsch(it.kcal)} kcal/Tag",
            style = MaterialTheme.typography.bodyMedium,
            color = FoodyTheme.farben.nebentext,
        )
    }
}
