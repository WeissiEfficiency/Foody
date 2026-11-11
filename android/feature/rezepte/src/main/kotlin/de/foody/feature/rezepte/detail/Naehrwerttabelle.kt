package de.foody.feature.rezepte.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptVoll
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Abschnittskopf
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.Nutrient
import de.foody.domain.NutritionResult
import java.math.BigDecimal
import java.math.RoundingMode

private val NAEHRSTOFFE = listOf(
    Nutrient.PROTEIN_G to "Eiweiß", Nutrient.CARBS_G to "Kohlenhydrate", Nutrient.FAT_G to "Fett",
    Nutrient.FIBER_G to "Ballaststoffe", Nutrient.SUGAR_G to "Zucker", Nutrient.SALT_G to "Salz",
)

/** „717 kcal je Portion“; `null` ohne Energiewerte. */
internal fun kcalJePortion(v: RezeptVoll): String? =
    v.naehrwerte.perServing(Nutrient.ENERGY_KJ)?.let { "${ganzzahlDeutsch(NutritionResult.kcal(it))} kcal je Portion" }

/** „1.434 kcal für 2 Portionen“ (das Rezept skaliert); `null` ohne Energiewerte und bei einer Portion (wie je Portion). */
internal fun kcalFuerPortionen(v: RezeptVoll, portionen: Int): String? =
    v.naehrwerte.perServing(Nutrient.ENERGY_KJ)?.takeIf { portionen > 1 }
        ?.let { "${kcal(it.multiply(BigDecimal(portionen)))} für $portionen Portionen" }

/** Gramm mit höchstens einer Nachkommastelle, deutsch. */
private fun gramm(wert: BigDecimal): String =
    wert.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',') + " g"

/**
 * Nährwerte je Portion und für die gewählten [portionen] (das Rezept skaliert), dazu die Vollständigkeit: fehlen bei
 * Zutaten Werte, sind die Summen Untergrenzen.
 */
@Composable
internal fun Naehrwerttabelle(v: RezeptVoll, portionen: Int) {
    Abschnittskopf("Nährwerte")
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            kcalJePortion(v)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text) }
            Zeile("", "je Portion", if (portionen == 1) "1 Portion" else "$portionen Portionen", kopf = true)
            naehrwertZeilen(v, portionen).forEach { Zeile(it.name, it.jePortion, it.gesamt) }
            fehlendeWerte(v)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
        }
    }
}

/** Eine Zeile der Nährwerttabelle: Name, Wert je Portion, Wert für die gewählten Portionen. */
internal data class NaehrwertZeile(val name: String, val jePortion: String, val gesamt: String)

/** Energie, Brennwert und die Nährstoffe mit Werten, je Portion und für [portionen]. */
internal fun naehrwertZeilen(v: RezeptVoll, portionen: Int): List<NaehrwertZeile> {
    val n = v.naehrwerte
    val faktor = BigDecimal(portionen)
    val energie = n.perServing(Nutrient.ENERGY_KJ)?.let { kj ->
        listOf(NaehrwertZeile("Energie", kcal(kj), kcal(kj.multiply(faktor))), NaehrwertZeile("Brennwert", kilojoule(kj), kilojoule(kj.multiply(faktor))))
    }.orEmpty()
    return energie + NAEHRSTOFFE.mapNotNull { (naehrstoff, name) ->
        n.perServing(naehrstoff)?.let { NaehrwertZeile(name, gramm(it), gramm(it.multiply(faktor))) }
    }
}

/** „Für 2 von 5 Zutaten fehlen Werte“; `null`, wenn alle Werte da sind. */
internal fun fehlendeWerte(v: RezeptVoll): String? =
    v.zeilen.count { !it.hatNaehrwerte }.takeIf { it > 0 }?.let { "Für $it von ${v.zeilen.size} Zutaten fehlen Werte" }

private fun kcal(kj: BigDecimal) = "${ganzzahlDeutsch(NutritionResult.kcal(kj))} kcal"

private fun kilojoule(kj: BigDecimal) = "${ganzzahlDeutsch(kj.setScale(0, RoundingMode.HALF_UP).toInt())} kJ"

@Composable
private fun Zeile(name: String, jePortion: String, gesamt: String, kopf: Boolean = false) {
    val stil = if (kopf) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val farbe = if (kopf) FoodyTheme.farben.nebentext else FoodyTheme.farben.text
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Text(name, Modifier.weight(1.2f), style = stil, color = farbe)
        Text(jePortion, Modifier.weight(1f), style = stil, color = farbe, textAlign = TextAlign.End)
        Text(gesamt, Modifier.weight(1f), style = stil, color = farbe, textAlign = TextAlign.End)
    }
}
