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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeistenTag
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.komponenten.Tagesleiste
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Abstand der Meldungen über der schwebenden Navigationsleiste; die Liste endet darüber. */
private val UEBER_LEISTE = 96.dp
private val LISTE_UNTEN = 104.dp

/**
 * Der Tab „Woche“ wie im Entwurf: Kopf mit Kalenderwoche und runden Pfeilen, Tagesleiste (Tippen springt zum Tag),
 * Tageskarten mit Einträgen und Plätzen für fehlende Mahlzeiten (ab 840 dp in zwei Spalten), am Ende „x von 28
 * Mahlzeiten geplant“ und „Leere Tage füllen“. „+ Mahlzeit“ führt über die Mahlzeitwahl, ein Platz direkt zur
 * Rezeptwahl; je Eintrag das Mahlzeit-Blatt.
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

    val scroll = rememberScrollState()
    // Lage der Tageskarten in der Liste, für den Sprung aus der Tagesleiste
    val lagen = remember { mutableStateMapOf<Int, Int>() }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val zweispaltig = maxWidth >= FoodyBreiten.zweispaltig
        Column(
            Modifier.widthIn(max = FoodyBreiten.zweiBereiche).fillMaxSize().verticalScroll(scroll)
                .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s3, bottom = ueberLeiste(LISTE_UNTEN)),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            Kopf(woche, zeigtHeute, vm::blaettern, vm::heute)
            val tage = woche?.tage.orEmpty()
            if (tage.isNotEmpty()) {
                val heuteIndex = tage.indexOfFirst { it.datum == vm.heuteDatum }
                Tagesleiste(
                    tage.map { LeistenTag(wochentagKurz(it.datum), it.datum.dayOfMonth, it.eintraege.isNotEmpty(), tagLang(it.datum)) },
                    gewaehlt = heuteIndex,
                    onWahl = { i -> lagen[i]?.let { y -> bereich.launch { scroll.animateScrollTo(y) } } },
                )
            }
            val karte = @Composable { i: Int, tag: WochenTag ->
                TagKarte(
                    tag, tag.datum == vm.heuteDatum, ziel,
                    onEintrag = { offen = it.id },
                    onPlus = { neuAm = tag.datum.toString() },
                    modifier = Modifier.onPlaced { lagen[i] = it.positionInParent().y.toInt() },
                    onPlatz = { m -> auswahlFuer = "${tag.datum}|${m.name}" },
                )
            }
            if (zweispaltig) {
                Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                        tage.take(4).forEachIndexed { i, t -> karte(i, t) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                        tage.drop(4).forEachIndexed { i, t -> karte(i + 4, t) }
                    }
                }
            } else {
                tage.forEachIndexed { i, t -> karte(i, t) }
            }
            if (tage.isNotEmpty()) Fuss(tage) { vm.fuellenOeffnen() }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = ueberLeiste(UEBER_LEISTE)))
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
    Seitenkopf("Woche", ueberzeile = woche?.let { wochenTitel(it.montag) }) {
        RunderKnopf(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Vorige Woche", { onBlaettern(-1) })
        RunderKnopf(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Nächste Woche", { onBlaettern(1) })
    }
    val schnitt = woche?.durchschnitt
    if (!zeigtHeute || schnitt != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            schnitt?.let {
                Text(
                    "Ø ${if (it.vollstaendig) "" else "mind. "}${ganzzahlDeutsch(it.kcal)} kcal/Tag",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodyTheme.farben.nebentext,
                )
            }
            if (!zeigtHeute) FoodyChip("Heute", ausgewaehlt = false, onKlick = onHeute)
        }
    }
}

/** Fuß wie im Entwurf: wie viele der vier Mahlzeiten je Tag geplant sind, daneben „Leere Tage füllen“. */
@Composable
private fun Fuss(tage: List<WochenTag>, onFuellen: () -> Unit) {
    val geplant = tage.sumOf { t -> t.eintraege.mapNotNull { Mahlzeit.ausText(it.mahlzeit) }.toSet().size }
    Row(Modifier.padding(top = FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$geplant von ${tage.size * TAGES_MAHLZEITEN.size} Mahlzeiten geplant",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = FoodyTheme.farben.nebentext,
        )
        FoodyKnopf("Leere Tage füllen", onFuellen, art = KnopfArt.UMRISS)
    }
}
