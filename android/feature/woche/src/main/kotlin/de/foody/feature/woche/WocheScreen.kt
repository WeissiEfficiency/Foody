package de.foody.feature.woche

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import java.time.LocalDate

private val INHALT_MAX = 1100.dp

/**
 * Der Tab „Woche“: eine Woche Mo–So mit Pfeilen zum Blättern, Tage mit Einträgen und Nährwerten, „+ Mahlzeit“ (Mahlzeit
 * wählen → Rezept wählen) und „Leere Tage füllen“.
 */
@Composable
fun WocheRoute(onKochen: (String, Int) -> Unit, onRezept: (String) -> Unit, vm: WocheViewModel = hiltViewModel()) {
    val woche by vm.woche.collectAsStateWithLifecycle()
    val ziel by vm.tagesziel.collectAsStateWithLifecycle()
    val zeigtHeute by vm.zeigtHeute.collectAsStateWithLifecycle()
    val karten by vm.rezeptAuswahl.collectAsStateWithLifecycle()
    // Hinzufügen in zwei Schritten: erst Mahlzeit, dann Rezept
    var neuAm by rememberSaveable { mutableStateOf<String?>(null) }
    var auswahlFuer by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = INHALT_MAX).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Kopf(woche, zeigtHeute, vm::blaettern, vm::heute)
            woche?.tage?.forEach { tag ->
                TagKarte(tag, tag.datum == vm.heuteDatum, ziel, onEintrag = {}, onPlus = { neuAm = tag.datum.toString() })
            }
            FoodyKnopf("Leere Tage füllen", { vm.fuellenOeffnen() }, Modifier.fillMaxWidth())
        }
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
}

@Composable
private fun Kopf(woche: WochenAnsicht?, zeigtHeute: Boolean, onBlaettern: (Long) -> Unit, onHeute: () -> Unit) {
    Text("Woche", style = MaterialTheme.typography.headlineLarge, color = FoodyTheme.farben.text)
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
        Text("Ø ${ganzzahlDeutsch(it.kcal)} kcal/Tag", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
    }
}
