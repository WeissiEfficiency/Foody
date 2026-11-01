package de.foody.feature.heute.tagebuch

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.komponenten.FoodyFab
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeistenTag
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.komponenten.Tagesleiste
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.TAGEBUCH_INHALT
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/**
 * Auftrag für das Eintragen-Blatt: [mahlzeit] `null` = nach Uhrzeit, [datum] beim Öffnen festgehalten (ein Wechsel um
 * Mitternacht leert das Blatt nicht), [id] je Öffnen neu – nur ein neuer Auftrag setzt die Eingaben zurück, ein
 * Neuaufbau (Drehen, Dunkelmodus) nicht. Mit [Saver] überlebt er den Neuaufbau.
 */
data class BlattAuftrag(val mahlzeit: Mahlzeit?, val datum: LocalDate, val id: String = UUID.randomUUID().toString()) {
    companion object {
        val Saver: Saver<BlattAuftrag?, Any> = Saver(
            save = { a -> a?.let { arrayListOf(it.mahlzeit?.name.orEmpty(), it.datum.toString(), it.id) } },
            restore = { w ->
                @Suppress("UNCHECKED_CAST")
                (w as List<String>).let { (m, d, id) -> BlattAuftrag(m.takeIf { it.isNotEmpty() }?.let(Mahlzeit::valueOf), LocalDate.parse(d), id) }
            },
        )
    }
}

/**
 * „Alle Einträge“ eines Tages wie im Entwurf: Kopf mit Vor/Zurück und Datumsauswahl, Tagesleiste ([onDatum] wechselt
 * das Ziel), Ring mit Makros, je Mahlzeit eine Karte mit Ändern und Löschen, „+ Eintragen“ unten rechts („Rückgängig“ stellt den Eintrag mit derselben ID wieder her), Eintragen-Blatt.
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
    // Gespeichert, damit Dialog und Blatt Drehen und Dunkelmodus überstehen
    var bearbeitenId by rememberSaveable { mutableStateOf<String?>(null) }
    var blatt by rememberSaveable(stateSaver = BlattAuftrag.Saver) { mutableStateOf(null) }
    var datumWahl by rememberSaveable { mutableStateOf(false) }

    val loeschen: (TagebuchEintragEntity) -> Unit = { e ->
        bereich.launch {
            val weg = vm.loeschen(e.id) ?: return@launch
            val antwort = snackbar.showSnackbar("Eintrag gelöscht", actionLabel = "Rückgängig", duration = SnackbarDuration.Long)
            if (antwort == SnackbarResult.ActionPerformed) vm.wiederherstellen(weg)
        }
    }

    Box(Modifier.fillMaxSize()) {
        TagebuchTagInhalt(
            datum, zustand, onDatum, onWahl = { datumWahl = true }, onAendern = { bearbeitenId = it.id }, onLoeschen = loeschen,
            onEintragen = { blatt = BlattAuftrag(it, datum) },
        )
        FoodyFab(
            "Eintragen", Icons.Outlined.Add, { blatt = BlattAuftrag(null, datum) },
            Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = FoodyAbstaende.s4, bottom = ueberLeiste(96.dp)),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = ueberLeiste(160.dp)))
    }

    val bearbeiten = bearbeitenId?.let { id -> zustand?.abschnitte?.firstNotNullOfOrNull { a -> a.eintraege.firstOrNull { it.id == id } } }
    bearbeiten?.let { e ->
        EintragBearbeiten(e, onSpeichern = { menge, m, tag, werte ->
            vm.aendern(e.id, menge, m, tag, werte)
            bearbeitenId = null
        }, onAbbrechen = { bearbeitenId = null })
    }
    blatt?.let { b -> EintragenBlatt(b, onFertig = { blatt = null }, vm = eintragenVm ?: hiltViewModel()) }
    if (datumWahl) DatumWahl(datum, onDatum = { datumWahl = false; onDatum(it) }, onAbbrechen = { datumWahl = false })
}

/** Inhalt ohne eigenen Zustand: Kopf, Bilanz, Mahlzeiten mit Einträgen. */
@Composable
fun TagebuchTagInhalt(
    datum: LocalDate,
    zustand: TagZustand?,
    onDatum: (LocalDate) -> Unit,
    onWahl: () -> Unit,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onEintragen: (Mahlzeit?) -> Unit,
) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
                .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s3, bottom = ueberLeiste(168.dp)).testTag(TAGEBUCH_INHALT),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4),
        ) { TagInhalt(datum, zustand, onDatum, onWahl, onAendern, onLoeschen, onEintragen) }
    }
}

@Composable
private fun TagInhalt(
    datum: LocalDate,
    zustand: TagZustand?,
    onDatum: (LocalDate) -> Unit,
    onWahl: () -> Unit,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onEintragen: (Mahlzeit?) -> Unit,
) {
    TagKopf(datum, onDatum, onWahl)
    val montag = datum.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Tagesleiste(
        (0L..6L).map { i -> montag.plusDays(i) }.map { d ->
            LeistenTag(wochentagKurz(d), d.dayOfMonth, markiert = false, beschreibung = TAG_LANG.format(d))
        },
        gewaehlt = (datum.toEpochDay() - montag.toEpochDay()).toInt(),
        onWahl = { i -> onDatum(montag.plusDays(i.toLong())) },
    )
    val z = zustand?.takeIf { it.datum == datum } ?: return
    TagBilanz(z.summe.kcal, z.ziel, z.summe.eiweiss, z.summe.kohlenhydrate, z.summe.fett)
    z.abschnitte.forEach { a -> MahlzeitKarte(a, onAendern, onLoeschen) { onEintragen(a.mahlzeit) } }
}

/** Kopf wie im Entwurf: Datum als Überzeile, Titel „Tagebuch“, rechts runde Knöpfe für Vortag, Kalender und Folgetag. */
@Composable
private fun TagKopf(datum: LocalDate, onDatum: (LocalDate) -> Unit, onWahl: () -> Unit) {
    Seitenkopf("Tagebuch", ueberzeile = TAG_LANG.format(datum)) {
        RunderKnopf(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Vorheriger Tag", { onDatum(datum.minusDays(1)) })
        RunderKnopf(Icons.Outlined.CalendarMonth, "Datum wählen", onWahl)
        RunderKnopf(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Nächster Tag", { onDatum(datum.plusDays(1)) })
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
