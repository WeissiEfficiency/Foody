package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.komponenten.vorausgefuelltFarben
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.scan.PackungScanKnopf
import de.foody.domain.BlsTreffer
import de.foody.domain.FotoPortion
import de.foody.domain.Mahlzeit
import de.foody.domain.Nutrient
import de.foody.domain.NutritionResult
import de.foody.feature.heute.anzeigename
import de.foody.feature.heute.slotName
import kotlinx.coroutines.launch

/** Test-Tag des Speichern-Knopfs (die Überschrift heißt auch „Eintragen“). */
const val EINTRAGEN_SPEICHERN = "eintragen-speichern"

/** Portionen zum Vorschlag aus dem Foto (Phase 6). */
private val PORTIONEN = listOf(FotoPortion.KLEIN to "klein", FotoPortion.NORMAL to "normal", FotoPortion.GROSS to "groß")

private val WEGE = listOf(
    EintragenWeg.REZEPT to "Rezept",
    EintragenWeg.ZUTAT to "Zutat",
    EintragenWeg.PLAN to "Aus dem Plan",
    EintragenWeg.FREI to "Frei",
)

/** Wege in der Düsteren Sage (Immersiv). */
private val SAGE_WEGE = mapOf(EintragenWeg.REZEPT to "Eigenes Rezept", EintragenWeg.PLAN to "Vorgesehen", EintragenWeg.FREI to "Frei")

/** Kurzname einer Mahlzeit in der Düsteren Sage wie im Entwurf („Morgen“, „Zwischen“). */
private fun Mahlzeit.sageKurz(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Morgen"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.SNACK -> "Zwischen"
    Mahlzeit.ABENDESSEN -> "Abend"
}

/** Blatt von unten zum Eintragen (siehe [BlattAuftrag]). Schließt nach erfolgreichem Speichern. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EintragenBlatt(auftrag: BlattAuftrag, onFertig: () -> Unit, vm: EintragenViewModel) {
    LaunchedEffect(auftrag.id) { vm.oeffnen(auftrag.datum, auftrag.mahlzeit, auftrag = auftrag.id, start = auftrag.start) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    val moeglich by vm.speichernMoeglich.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val e = z.eingaben
    GlasBlatt(onFertig) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FoodyAbstaende.s4).padding(bottom = FoodyAbstaende.s6),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            BlattTitel(
                stilText("Eintragen", sage = "Mahl eintragen", anime = "Eintragen!", bayrisch = "Eitragn", grauen = "Notieren"),
                beschreibung = "Eintragen",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                MAHLZEITEN.forEach { m ->
                    FoodyChip(stilText(m.anzeigename(), sage = m.sageKurz()), m == e.mahlzeit, { vm.mahlzeit(m) }, beschreibung = m.anzeigename())
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                WEGE.forEach { (w, text) -> FoodyChip(stilText(text, sage = SAGE_WEGE[w]), w == e.weg, { vm.weg(w) }, beschreibung = text) }
            }
            when (e.weg) {
                EintragenWeg.REZEPT -> RezeptWahl(z, vm)
                EintragenWeg.ZUTAT -> ZutatWahl(z, vm)
                EintragenWeg.PLAN -> PlanWahl(z, vm)
                EintragenWeg.FREI -> FreiEingabe(z, vm)
            }
            FoodyKnopf(
                stilText("Eintragen", sage = "In die Chronik", anime = "Eintragen! ✓", bayrisch = "Eitragn", grauen = "Ins Logbuch", kreuzer = "Ins Bordbuch"),
                { bereich.launch { if (vm.speichern() != null) onFertig() } },
                Modifier.fillMaxWidth().testTag(EINTRAGEN_SPEICHERN),
                aktiv = moeglich,
            )
        }
    }
}

@Composable
private fun Suche(text: String, beschriftung: String, onText: (String) -> Unit) {
    FoodyTextfeld(beschriftung, text, onWert = onText)
}

/** Auswählbare Trefferzeile mit Haken, mindestens 48 dp hoch. */
@Composable
private fun Treffer(text: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(gewaehlt, onClick = onWahl, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        if (gewaehlt) Icon(Icons.Outlined.Check, contentDescription = null, tint = FoodyTheme.farben.primaer)
    }
}

@Composable
private fun RezeptWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Rezept suchen", vm::suche)
    z.rezepte.forEach { r -> Treffer(r.titel, r.id == z.eingaben.rezeptId) { vm.rezeptWaehlen(r.id) } }
    // Gescannte Produkte zur Suche: Antippen wechselt zu „Zutat“ mit dem Produkt
    if (z.produkte.isNotEmpty()) {
        Text("Gescannte Produkte", style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext)
        z.produkte.forEach { p -> Treffer(p.name, gewaehlt = false) { vm.produktWaehlen(p) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.portionenSchritt(mehr = false) }) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen") }
        OutlinedTextField(
            z.eingaben.portionen, vm::portionen, Modifier.width(120.dp), label = { Text("Portionen") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        IconButton(onClick = { vm.portionenSchritt(mehr = true) }) { Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZutatWahl(z: EintragenZustand, vm: EintragenViewModel) {
    Suche(z.eingaben.suche, "Zutat suchen", vm::suche)
    z.zutaten.forEach { t -> Treffer(t.name, t.id == z.eingaben.zutatId) { vm.zutatWaehlen(t.id) } }
    if (z.bls.isNotEmpty()) {
        Text(
            stilText("Aus dem Bundeslebensmittelschlüssel", sage = "Aus dem großen Verzeichnis"),
            style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext,
        )
        z.bls.forEach { t -> BlsZeile(t) { vm.blsWaehlen(t) } }
    }
    // Nach „im Katalog“ (Scan in „Frei“) geht der Fokus auf die Menge
    val mengeFokus = remember { FocusRequester() }
    LaunchedEffect(vm) { vm.mengeFokus.collect { mengeFokus.requestFocus() } }
    Zahlfeld("Menge", z.eingaben.menge, focusRequester = mengeFokus, onWert = vm::menge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        EINTRAGEN_EINHEITEN.forEach { u -> FoodyChip(u.symbol, u == z.eingaben.einheit, { vm.einheit(u) }) }
    }
}

/** BLS-Treffer: Name und kcal je 100 g; antippen übernimmt ihn in den Katalog und wählt ihn. */
@Composable
private fun BlsZeile(t: BlsTreffer, onWahl: () -> Unit) {
    val kcal = t.lebensmittel.werte[Nutrient.ENERGY_KJ]?.let(NutritionResult::kcal)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onWahl).padding(vertical = FoodyAbstaende.s1),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(t.vorschlagName, style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        val details = listOfNotNull(
            t.lebensmittel.name.takeIf { it != t.vorschlagName },
            kcal?.let { "$it kcal je 100 g" },
            t.stueckG?.let { "Stück ≈ ${it.toPlainString()} g" },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
        }
    }
}

@Composable
private fun PlanWahl(z: EintragenZustand, vm: EintragenViewModel) {
    if (z.plan.isEmpty()) {
        Text(
            stilText("Für diesen Tag ist nichts Geplantes mehr offen.", sage = "Für diesen Tag ist nichts Weiteres vorgesehen."),
            color = FoodyTheme.farben.nebentext,
        )
    }
    z.plan.forEach { p ->
        Treffer("${p.titel} · ${slotName(p.slot)}", p.planEintragId == z.eingaben.planEintragId) { vm.planWaehlen(p.planEintragId) }
    }
}

/**
 * „Frei“: oben der Scan-Knopf und (mit Modell) „Foto erkennen“; nach einem Scan die Packung ([PackungEingabe]) statt der
 * freien Felder. Werte aus dem Foto sind markiert, solange sie dem Vorschlag gleichen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreiEingabe(z: EintragenZustand, vm: EintragenViewModel) {
    val e = z.eingaben
    if (e.packung != null) {
        PackungEingabe(e, z.vorhandeneZutat, vm)
        return
    }
    val fotoKnopf by vm.fotoKnopf.collectAsStateWithLifecycle()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        PackungScanKnopf(vm.scanLauf)
        if (fotoKnopf) FotoErkennungKnopf(vm.fotoLauf)
    }
    if (fotoKnopf) FotoErkennungStand(vm.fotoLauf)
    e.foto?.let { stand ->
        if (fotoHinweis(e)) {
            Text(
                if (stand.ausGedaechtnis) ErkennungTexte.WIE_LETZTES_MAL else ErkennungTexte.BITTE_PRUEFEN,
                style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.primaer,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            PORTIONEN.forEach { (p, text) -> FoodyChip(text, p == stand.portion, { vm.fotoPortion(p) }) }
        }
    }
    val f = e.foto?.angezeigt
    FoodyTextfeld("Name", e.freiName, farben = vorausgefuelltFarben(wieImFoto(e.freiName, f?.name)), onWert = vm::freiName)
    Zahlfeld("kcal", e.freiKcal, ganzzahlig = true, markiert = wieImFoto(e.freiKcal, f?.kcal), onWert = vm::freiKcal)
    Zahlfeld("Eiweiß (g, optional)", e.freiEiweiss, markiert = wieImFoto(e.freiEiweiss, f?.eiweiss), onWert = vm::freiEiweiss)
    Zahlfeld("Kohlenhydrate (g, optional)", e.freiKh, markiert = wieImFoto(e.freiKh, f?.kh), onWert = vm::freiKh)
    Zahlfeld("Fett (g, optional)", e.freiFett, markiert = wieImFoto(e.freiFett, f?.fett), onWert = vm::freiFett)
}
