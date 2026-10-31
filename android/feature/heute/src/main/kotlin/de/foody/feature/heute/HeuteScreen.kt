package de.foody.feature.heute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.tagebuch.BlattAuftrag
import de.foody.feature.heute.tagebuch.EintragenBlatt
import de.foody.feature.heute.tagebuch.EintragenViewModel
import java.time.LocalDate

/** Was „Heute“ auslösen kann; die Navigation liegt in der App. */
data class HeuteAktionen(
    val onKochen: (rezeptId: String, portionen: Int) -> Unit,
    val onRezept: (String) -> Unit,
    val onEinkauf: (listeId: String) -> Unit,
    val onAlleEintraege: () -> Unit,
    val onEinstellungen: () -> Unit,
    val onEintragen: (Mahlzeit?) -> Unit,
    val onGegessen: (planEintragId: String) -> Unit,
)

@Composable
fun HeuteRoute(
    onKochen: (String, Int) -> Unit,
    onRezept: (String) -> Unit,
    onEinkauf: (String) -> Unit,
    onAlleEintraege: () -> Unit,
    onEinstellungen: () -> Unit,
    vm: HeuteViewModel = hiltViewModel(),
    eintragenVm: EintragenViewModel? = null,
) {
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    var blatt by rememberSaveable(stateSaver = BlattAuftrag.Saver) { mutableStateOf(null) }
    val aktionen = HeuteAktionen(
        onKochen, onRezept, onEinkauf, onAlleEintraege, onEinstellungen,
        { blatt = BlattAuftrag(it, zustand?.datum ?: LocalDate.now()) }, vm::gegessen,
    )
    HeuteScreen(zustand, aktionen)
    blatt?.let { b -> EintragenBlatt(b, onFertig = { blatt = null }, vm = eintragenVm ?: hiltViewModel()) }
}

/**
 * Startseite: Kopf, nächste Mahlzeit, Inspiration (nur ohne Plan für heute), Tagebuch des Tages, offener Einkauf.
 * Unten Platz für die schwebende Navigationsleiste.
 */
@Composable
fun HeuteScreen(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        HeuteInhalt(zustand, aktionen)
    }
}

@Composable
private fun HeuteInhalt(zustand: HeuteZustand?, aktionen: HeuteAktionen) {
    Column(
        Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
            .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = PLATZ_FUER_LEISTE).testTag(HEUTE_INHALT),
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s5),
    ) {
        HeuteKopf(zustand?.datum, zustand?.sync, aktionen.onEinstellungen)
        if (zustand == null) return@Column
        if (zustand.datenbankLeer) {
            LeererZustandFlaeche(
                Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
                aktionText = "Server verbinden", onAktion = aktionen.onEinstellungen,
            )
        }
        zustand.naechste?.let { NaechsteMahlzeitKarte(it, aktionen) }
        zustand.inspiration?.let { InspirationKarte(it, aktionen.onRezept) }
        TagebuchBereich(zustand.tagebuch, aktionen)
        zustand.einkauf?.let { EinkaufBereich(it, aktionen.onEinkauf) }
    }
}

/** Höhe der schwebenden Leiste samt Abstand, damit der letzte Bereich nicht darunter liegt. */
private val PLATZ_FUER_LEISTE = 104.dp
