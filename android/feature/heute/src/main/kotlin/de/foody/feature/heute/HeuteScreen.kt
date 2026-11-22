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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.stil.HeuteAnime
import de.foody.feature.heute.stil.HeuteBayrisch
import de.foody.feature.heute.stil.HeuteEdo
import de.foody.feature.heute.stil.HeuteGrauen
import de.foody.feature.heute.stil.HeuteKreuzer
import de.foody.feature.heute.stil.HeuteSage
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.SofortStart
import java.time.LocalDate

/** Was „Heute“ auslösen kann; die Navigation liegt in der App. */
data class HeuteAktionen(
    val onKochen: (rezeptId: String, portionen: Int) -> Unit,
    val onRezept: (String) -> Unit,
    val onEinkauf: (listeId: String) -> Unit,
    val onAlleEintraege: () -> Unit,
    val onEinstellungen: () -> Unit,
    val onEintragen: (Mahlzeit?) -> Unit,
    /** „Scannen“ und „Foto“ in den Szenen: Eintragen gleich mit Scanner bzw. Kamera. */
    val onSofort: (SofortStart) -> Unit,
    val onGegessen: (planEintragId: String) -> Unit,
    val onNichtGegessen: (GeplanteMahlzeit) -> Unit,
)

/**
 * „Heute“ mit ViewModel. [onEintragen] öffnet die Seite Eintragen für den Tag (aus einer Zeile mit deren Mahlzeit,
 * sonst nach Uhrzeit; „Scannen“ und „Foto“ gleich mit Scanner bzw. Kamera).
 */
@Composable
fun HeuteRoute(
    onKochen: (String, Int) -> Unit,
    onRezept: (String) -> Unit,
    onEinkauf: (String) -> Unit,
    onAlleEintraege: () -> Unit,
    onEinstellungen: () -> Unit,
    onEintragen: (EintragenStart) -> Unit,
    vm: HeuteViewModel = hiltViewModel(),
) {
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    val datum = zustand?.datum ?: LocalDate.now()
    val aktionen = HeuteAktionen(
        onKochen, onRezept, onEinkauf, onAlleEintraege, onEinstellungen,
        onEintragen = { onEintragen(EintragenStart(datum, it)) },
        onSofort = { onEintragen(EintragenStart(datum, null, it)) },
        onGegessen = vm::gegessen,
        onNichtGegessen = vm::nichtGegessen,
    )
    HeuteScreen(zustand, aktionen)
}

/**
 * Startseite: Kopf, nächste Mahlzeit, Inspiration (nur ohne Plan für heute), Tagebuch des Tages, offener Einkauf.
 * Unten Platz für die schwebende Navigationsleiste. Ein Stil in Immersiv zeigt stattdessen seine Szene (`stil/`).
 */
@Composable
fun HeuteScreen(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    // Szenen der Stile in Immersiv; sonst die normale Ansicht
    when {
        FoodyTheme.stil.szene(FoodyStil.SAGE) -> return HeuteSage(zustand, aktionen, modifier)
        FoodyTheme.stil.szene(FoodyStil.GRAUEN) -> return HeuteGrauen(zustand, aktionen, modifier)
        FoodyTheme.stil.szene(FoodyStil.KREUZER) -> return HeuteKreuzer(zustand, aktionen, modifier)
        FoodyTheme.stil.szene(FoodyStil.BAYRISCH) -> return HeuteBayrisch(zustand, aktionen, modifier)
        FoodyTheme.stil.szene(FoodyStil.ANIME) -> return HeuteAnime(zustand, aktionen, modifier)
        FoodyTheme.stil.szene(FoodyStil.EDO) -> return HeuteEdo(zustand, aktionen, modifier)
    }
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        HeuteInhalt(zustand, aktionen)
    }
}

@Composable
private fun HeuteInhalt(zustand: HeuteZustand?, aktionen: HeuteAktionen) {
    Column(
        Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().safeDrawingPadding()
            .padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = ueberLeiste(PLATZ_FUER_LEISTE))
            .testTag(HEUTE_INHALT),
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s5),
    ) {
        HeuteKopf(zustand?.datum, zustand?.sync, aktionen.onEinstellungen)
        if (zustand == null) return@Column
        if (zustand.datenbankLeer) DatenbankLeerHinweis(aktionen.onEinstellungen)
        zustand.naechste?.let { NaechsteMahlzeitKarte(it, aktionen) }
        zustand.inspiration?.let { InspirationKarte(it, aktionen.onRezept) }
        TagebuchBereich(zustand.tagebuch, aktionen)
        zustand.einkauf?.let { EinkaufBereich(it, aktionen.onEinkauf) }
    }
}
