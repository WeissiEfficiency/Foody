package de.foody.feature.heute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.domain.Mahlzeit

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
    onEintragen: (Mahlzeit?) -> Unit,
    vm: HeuteViewModel = hiltViewModel(),
) {
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    HeuteScreen(zustand, HeuteAktionen(onKochen, onRezept, onEinkauf, onAlleEintraege, onEinstellungen, onEintragen, vm::gegessen))
}

/**
 * Startseite: Kopf, nächste Mahlzeit, Inspiration (nur ohne Plan für heute), Tagebuch des Tages, offener Einkauf.
 * Unten Platz für die schwebende Navigationsleiste.
 */
@Composable
fun HeuteScreen(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = PLATZ_FUER_LEISTE),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
