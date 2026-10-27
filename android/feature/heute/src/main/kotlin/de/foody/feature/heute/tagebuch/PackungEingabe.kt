package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.scan.ScanTexte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult

/** Text des Häkchens: „Zutat „Haferflocken“ aktualisieren“, wenn es die Zutat schon gibt, sonst „Als Zutat speichern“. */
internal fun haekchenText(vorhandeneZutat: String?): String =
    vorhandeneZutat?.let { "Zutat „$it“ aktualisieren" } ?: "Als Zutat speichern"

/** kcal für die eingegebene Menge, so wie gespeichert (kJ nach der Energie-Regel); `null` ohne Menge > 0 oder kcal. */
internal fun vorschauKcal(e: Eingaben): Int? {
    val p = e.packung ?: return null
    val menge = zahl(e.pMenge)?.takeIf { it.signum() > 0 } ?: return null
    val kj = p.kjFuer(e.pKcal) ?: return null
    return NutritionResult.kcal(kj.multiply(menge).movePointLeft(2))
}

/** Markiert, solange das Feld dem Scanwert gleicht (und die Packung den Wert hatte). */
private fun wieGescannt(feld: String, scan: String) = scan.isNotEmpty() && feld.trim() == scan

/**
 * „Frei“ mit gescannter Packung (Spec 6e3, 3.): Herkunft, Name, Menge mit Einheit nach der Basis (ohne Basis Chips
 * g/ml), Werte je 100 g/ml – markiert, solange sie dem Scan gleichen –, Vorschau, Häkchen und „Zurück zu Frei“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackungEingabe(e: Eingaben, vorhandeneZutat: String?, vm: EintragenViewModel) {
    val p = e.packung ?: return
    val farben = FoodyTheme.farben
    val einheit = packungsEinheit(e.pBasis).symbol
    Text(ScanTexte.bittePruefen(p.quelle), style = MaterialTheme.typography.labelLarge, color = farben.primaer)
    OutlinedTextField(e.pName, vm::pName, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
    Zahlfeld("Menge", e.pMenge, einheit = einheit, onWert = vm::pMenge)
    if (p.basis == null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(NutrientBasis.PER_100_G, NutrientBasis.PER_100_ML).forEach { b ->
                FoodyChip(packungsEinheit(b).symbol, b == e.pBasis, { vm.pBasis(b) })
            }
        }
    }
    Text("je 100 $einheit", style = MaterialTheme.typography.labelLarge, color = farben.text)
    Zahlfeld("kcal", e.pKcal, markiert = wieGescannt(e.pKcal, p.kcalText), onWert = vm::pKcal)
    Zahlfeld("Eiweiß (g, optional)", e.pEiweiss, markiert = wieGescannt(e.pEiweiss, p.feldText(Nutrient.PROTEIN_G)), onWert = vm::pEiweiss)
    Zahlfeld("Kohlenhydrate (g, optional)", e.pKh, markiert = wieGescannt(e.pKh, p.feldText(Nutrient.CARBS_G)), onWert = vm::pKh)
    Zahlfeld("Fett (g, optional)", e.pFett, markiert = wieGescannt(e.pFett, p.feldText(Nutrient.FAT_G)), onWert = vm::pFett)
    vorschauKcal(e)?.let {
        Text("= ${ganzzahlDeutsch(it)} kcal", style = MaterialTheme.typography.titleMedium, color = farben.primaer)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(e.alsZutat, role = Role.Checkbox, onValueChange = vm::alsZutat),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(e.alsZutat, onCheckedChange = null)
        Text(
            haekchenText(vorhandeneZutat), Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge, color = farben.text,
        )
    }
    FoodyKnopf("Zurück zu Frei", vm::packungVerwerfen, art = KnopfArt.TEXT)
}
