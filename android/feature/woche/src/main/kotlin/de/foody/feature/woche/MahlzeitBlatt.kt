package de.foody.feature.woche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import java.time.LocalDate

/** Was das Mahlzeit-Blatt auslösen kann. */
internal class MahlzeitAktionen(
    val onPortionen: (Int) -> Unit,
    val onGekocht: (Boolean) -> Unit,
    val onKochen: () -> Unit,
    val onRezept: () -> Unit,
    val onVerschieben: (LocalDate) -> Unit,
    val onUmbenennen: () -> Unit,
    val onEntfernen: () -> Unit,
)

/**
 * Ein Eintrag: Portionen ±, Gekocht (umschalten), Kochen, Rezept ansehen, Verschieben auf einen Tag der gezeigten
 * Woche ([montag]) oder ±7 Tage, Umbenennen, Entfernen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MahlzeitBlatt(e: PlanEintrag, montag: LocalDate, a: MahlzeitAktionen, onSchliessen: () -> Unit) {
    // Ganz geöffnet und scrollbar: die Knöpfe unten sollen ohne Ziehen erreichbar sein
    GlasBlatt(onSchliessen, ganzOffen = true) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FoodyAbstaende.s6)
                .padding(bottom = FoodyAbstaende.s6).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            Text(e.rezeptName, Modifier.ueberschrift(), style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            Text(mahlzeitAnzeige(e.mahlzeit), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { a.onPortionen(e.portionen - 1) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen", tint = FoodyTheme.farben.text)
                }
                Text("${e.portionen} P.", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
                IconButton(onClick = { a.onPortionen(e.portionen + 1) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen", tint = FoodyTheme.farben.text)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                FoodyChip("Gekocht", ausgewaehlt = e.gekocht, onKlick = { a.onGekocht(!e.gekocht) })
                FoodyKnopf("Kochen", a.onKochen)
                FoodyKnopf("Rezept ansehen", a.onRezept, art = KnopfArt.UMRISS)
            }
            Augenbraue("Verschieben")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                (0L..6L).forEach { i ->
                    val tag = montag.plusDays(i)
                    FoodyChip(wochentagKurz(tag), ausgewaehlt = tag == e.datum, onKlick = { if (tag != e.datum) a.onVerschieben(tag) })
                }
                FoodyChip("Vorige Woche", ausgewaehlt = false, onKlick = { a.onVerschieben(e.datum.minusDays(7)) })
                FoodyChip("Nächste Woche", ausgewaehlt = false, onKlick = { a.onVerschieben(e.datum.plusDays(7)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                FoodyKnopf("Umbenennen", a.onUmbenennen, art = KnopfArt.TEXT)
                FoodyKnopf("Entfernen", a.onEntfernen, art = KnopfArt.TEXT)
            }
        }
    }
}
