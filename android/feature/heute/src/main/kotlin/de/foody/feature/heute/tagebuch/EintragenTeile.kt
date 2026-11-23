package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyTextfeld
import de.foody.core.designsystem.komponenten.WahlZeile
import de.foody.core.designsystem.komponenten.vorausgefuelltFarben
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.erkennung.ErkennungTexte
import de.foody.domain.BlsTreffer
import de.foody.domain.FotoPortion
import de.foody.domain.Nutrient
import de.foody.domain.NutritionResult

// Teile der Seite Eintragen, die Erfassen und Bestätigen nutzen

/** Portionen zum Vorschlag aus dem Foto (Phase 6). */
private val PORTIONEN = listOf(FotoPortion.KLEIN to "klein", FotoPortion.NORMAL to "normal", FotoPortion.GROSS to "groß")

/** Titel von Eintragen im Stil („Mahl eintragen“, „Eintragen!“ …). */
@Composable
internal fun eintragenTitel() = stilText("Eintragen", sage = "Mahl eintragen", anime = "Eintragen!", bayrisch = "Eitragn", grauen = "Notieren")

/** Die eine Suche in Erfassen über Rezepte, Zutaten und den BLS. */
@Composable
internal fun Suche(text: String, onText: (String) -> Unit) {
    FoodyTextfeld("Rezept oder Zutat suchen", text, onWert = onText)
}

/**
 * Trefferzeile (im Stil R–V wie im Entwurf), mindestens 48 dp hoch: ein Knopf, der nach Bestätigen führt, keine
 * Auswahl (TalkBack sagt nicht „Optionsfeld“), wie die BLS-Zeilen.
 */
@Composable
internal fun Treffer(text: String, onWahl: () -> Unit) {
    WahlZeile(text, gewaehlt = false, onWahl, knopf = true)
}

/** Portionen des gewählten Rezepts: − / Feld / + in Schritten von 0,5. */
@Composable
internal fun PortionenZeile(e: Eingaben, vm: EintragenViewModel) {
    // Zeichen in der Textfarbe: Auf den dunklen Seiten der Stile wären sie sonst schwarz
    val farbe = FoodyTheme.farben.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.portionenSchritt(mehr = false) }) { Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen", tint = farbe) }
        OutlinedTextField(
            e.portionen, vm::portionen, Modifier.width(120.dp), label = { Text("Portionen") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        IconButton(onClick = { vm.portionenSchritt(mehr = true) }) { Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen", tint = farbe) }
    }
}

/** Menge und Einheit einer Zutat; nach „im Katalog“ (Scan) und einem BLS-Treffer geht der Fokus auf die Menge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MengeEingabe(e: Eingaben, vm: EintragenViewModel) {
    val mengeFokus = remember { FocusRequester() }
    LaunchedEffect(vm) { vm.mengeFokus.collect { mengeFokus.requestFocus() } }
    Zahlfeld("Menge", e.menge, focusRequester = mengeFokus, onWert = vm::menge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        EINTRAGEN_EINHEITEN.forEach { u -> FoodyChip(u.symbol, u == e.einheit, { vm.einheit(u) }) }
    }
}

/** BLS-Treffer: Name und kcal je 100 g; antippen übernimmt ihn in den Katalog und wählt ihn. */
@Composable
internal fun BlsZeile(t: BlsTreffer, onWahl: () -> Unit) {
    val kcal = t.lebensmittel.werte[Nutrient.ENERGY_KJ]?.let(NutritionResult::kcal)
    val details = listOfNotNull(
        t.lebensmittel.name.takeIf { it != t.vorschlagName },
        kcal?.let { "$it kcal je 100 g" },
        t.stueckG?.let { "Stück ≈ ${it.toPlainString()} g" },
    )
    WahlZeile(t.vorschlagName, gewaehlt = false, onWahl, info = details.joinToString(" · ").ifEmpty { null }, knopf = true)
}

/**
 * Die freien Felder Name, kcal, Eiweiß, Kohlenhydrate und Fett; nach einem Foto darüber der Hinweis („bitte prüfen“
 * bzw. „wie beim letzten Mal“) und die Portionen zum Vorschlag. Werte aus dem Foto sind markiert, solange sie dem
 * Vorschlag gleichen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FreieFelder(e: Eingaben, vm: EintragenViewModel) {
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
