package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.anzeigename
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAG_KURZ = DateTimeFormatter.ofPattern("EE, d. MMMM", Locale.GERMAN)

/**
 * Ändern eines Eintrags: Portionen (Rezept) bzw. Menge (Zutat) – die Werte skaliert das Repository –, Mahlzeit und Tag;
 * bei freien Einträgen die Werte selbst. „Speichern“ nur mit gültigen Eingaben.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EintragBearbeiten(
    e: TagebuchEintragEntity,
    onSpeichern: (menge: BigDecimal?, mahlzeit: Mahlzeit, tag: LocalDate, freiWerte: Naehrwerte?) -> Unit,
    onAbbrechen: () -> Unit,
) {
    var menge by rememberSaveable { mutableStateOf(zahlText(if (e.art == TagebuchArt.REZEPT) e.portionen else e.menge)) }
    var kcal by rememberSaveable { mutableStateOf(e.werte().kcal?.toString().orEmpty()) }
    var eiweiss by rememberSaveable { mutableStateOf(zahlText(e.eiweiss)) }
    var kh by rememberSaveable { mutableStateOf(zahlText(e.kohlenhydrate)) }
    var fett by rememberSaveable { mutableStateOf(zahlText(e.fett)) }
    var mahlzeit by rememberSaveable { mutableStateOf(e.eintragMahlzeit()) }
    var tag by rememberSaveable { mutableStateOf(e.datum) }

    val frei = e.art == TagebuchArt.FREI
    fun makroOk(t: String) = t.isBlank() || zahl(t)?.let { it.signum() >= 0 } == true
    val gueltig = if (frei) {
        (kcal.trim().toIntOrNull() ?: -1) >= 0 && makroOk(eiweiss) && makroOk(kh) && makroOk(fett)
    } else {
        zahl(menge)?.let { it.signum() > 0 } == true
    }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(e.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (e.art) {
                    TagebuchArt.REZEPT -> Zahlfeld("Portionen", menge) { menge = it }
                    TagebuchArt.ZUTAT -> Zahlfeld("Menge (${e.einheit?.symbol.orEmpty()})", menge) { menge = it }
                    TagebuchArt.FREI -> {
                        Zahlfeld("kcal", kcal, ganzzahlig = true) { kcal = it }
                        Zahlfeld("Eiweiß (g)", eiweiss) { eiweiss = it }
                        Zahlfeld("Kohlenhydrate (g)", kh) { kh = it }
                        Zahlfeld("Fett (g)", fett) { fett = it }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MAHLZEITEN.forEach { m -> FoodyChip(m.anzeigename(), m == mahlzeit, { mahlzeit = m }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { tag = tag.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Einen Tag früher")
                    }
                    Text(TAG_KURZ.format(tag), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { tag = tag.plusDays(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Einen Tag später")
                    }
                }
            }
        },
        confirmButton = {
            FoodyKnopf("Speichern", {
                val werte = if (frei) Tagebuch.freiWerte(kcal.trim().toInt(), zahl(eiweiss), zahl(kh), zahl(fett)) else null
                onSpeichern(if (frei) null else zahl(menge), mahlzeit, tag, werte)
            }, aktiv = gueltig)
        },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}

/** Eingabefeld für Zahlen mit Ziffern-Tastatur. */
@Composable
internal fun Zahlfeld(beschriftung: String, wert: String, ganzzahlig: Boolean = false, onWert: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = onWert,
        label = { Text(beschriftung) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (ganzzahlig) KeyboardType.Number else KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
