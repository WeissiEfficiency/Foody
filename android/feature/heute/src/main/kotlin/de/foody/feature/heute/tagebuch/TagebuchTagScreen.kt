package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeererZustandZeile
import de.foody.core.designsystem.komponenten.Naehrwertbalken
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.anzeigename
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/** Auftrag für das Eintragen-Blatt; [mahlzeit] `null` = nach Uhrzeit. */
data class BlattAuftrag(val mahlzeit: Mahlzeit?)

/**
 * „Alle Einträge“ eines Tages: Vor/Zurück und Datumsauswahl ([onDatum] wechselt das Ziel), Tagesbilanz, Einträge je
 * Mahlzeit mit Ändern und Löschen („Rückgängig“ stellt den Eintrag mit derselben ID wieder her), Eintragen-Blatt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagebuchTagRoute(
    datum: LocalDate,
    onDatum: (LocalDate) -> Unit,
    vm: TagebuchTagViewModel = hiltViewModel(),
    eintragenVm: EintragenViewModel? = null,
) {
    LaunchedEffect(datum) { vm.zeigen(datum) }
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val bereich = rememberCoroutineScope()
    var bearbeiten by remember { mutableStateOf<TagebuchEintragEntity?>(null) }
    var blatt by remember { mutableStateOf<BlattAuftrag?>(null) }
    var datumWahl by remember { mutableStateOf(false) }

    val loeschen: (TagebuchEintragEntity) -> Unit = { e ->
        bereich.launch {
            val weg = vm.loeschen(e.id) ?: return@launch
            val antwort = snackbar.showSnackbar("Eintrag gelöscht", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
            if (antwort == SnackbarResult.ActionPerformed) vm.wiederherstellen(weg)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TagKopf(datum, onDatum, onWahl = { datumWahl = true })
            val z = zustand?.takeIf { it.datum == datum } ?: return@Column
            GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
                Naehrwertbalken(z.summe.kcal, z.ziel, z.summe.eiweiss, z.summe.kohlenhydrate, z.summe.fett, Modifier.padding(16.dp))
            }
            z.abschnitte.forEach { a ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Augenbraue(a.mahlzeit.anzeigename())
                    a.eintraege.forEach { e -> EintragZeile(e, onAendern = { bearbeiten = e }, onLoeschen = { loeschen(e) }) }
                    if (a.eintraege.isEmpty()) {
                        LeererZustandZeile("Noch kein ${a.mahlzeit.anzeigename()}", "Eintragen", { blatt = BlattAuftrag(a.mahlzeit) })
                    }
                }
            }
            FoodyKnopf("Eintragen", { blatt = BlattAuftrag(null) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 88.dp))
    }

    bearbeiten?.let { e ->
        EintragBearbeiten(e, onSpeichern = { menge, m, tag, werte ->
            vm.aendern(e.id, menge, m, tag, werte)
            bearbeiten = null
        }, onAbbrechen = { bearbeiten = null })
    }
    blatt?.let { b -> EintragenBlatt(datum, b.mahlzeit, onFertig = { blatt = null }, vm = eintragenVm ?: hiltViewModel()) }
    if (datumWahl) DatumWahl(datum, onDatum = { datumWahl = false; onDatum(it) }, onAbbrechen = { datumWahl = false })
}

@Composable
private fun TagKopf(datum: LocalDate, onDatum: (LocalDate) -> Unit, onWahl: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDatum(datum.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Vorheriger Tag", tint = FoodyTheme.farben.text)
        }
        Column(Modifier.weight(1f)) {
            Augenbraue("Tagebuch")
            Text(TAG_LANG.format(datum), style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
        }
        IconButton(onClick = onWahl) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Datum wählen", tint = FoodyTheme.farben.text)
        }
        IconButton(onClick = { onDatum(datum.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Nächster Tag", tint = FoodyTheme.farben.text)
        }
    }
}

/** Datumsauswahl; der DatePicker rechnet in UTC-Millisekunden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumWahl(datum: LocalDate, onDatum: (LocalDate) -> Unit, onAbbrechen: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = datum.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onAbbrechen,
        confirmButton = {
            FoodyKnopf("Übernehmen", {
                state.selectedDateMillis?.let { onDatum(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onAbbrechen()
            }, art = KnopfArt.TEXT)
        },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    ) { DatePicker(state) }
}
