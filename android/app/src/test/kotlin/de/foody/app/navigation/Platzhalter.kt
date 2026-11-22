package de.foody.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.tagebuch.EintragenStart
import java.time.LocalDate

/**
 * Ersatz für einen Bereich in Navigationstests: zeigt den Titel und die übergebenen Argumente; mit [aktionText] einen
 * Knopf, der [onAktion] auslöst (etwa wie „Heute“ Eintragen öffnet).
 */
@Composable
fun Platzhalter(
    titel: String,
    argumente: String? = null,
    modifier: Modifier = Modifier,
    aktionText: String? = null,
    onAktion: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeererZustandFlaeche(Icons.Outlined.Construction, titel, Platzhalter.TEXT, aktionText = aktionText, onAktion = onAktion)
            if (argumente != null) {
                Text(argumente, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
    }
}

object Platzhalter {
    const val TEXT = "Platzhalter für Tests"

    /** Knopf im Heute-Platzhalter: öffnet Eintragen für einen Snack am [TAG], wie die leere Zeile in „Heute“. */
    const val SNACK_EINTRAGEN = "Snack eintragen"

    /** Knopf im Tagebuch-Platzhalter: öffnet Eintragen für den gezeigten Tag, wie „+ Eintragen“ im Tagebuch. */
    const val TAGEBUCH_EINTRAGEN = "Im Tagebuch eintragen"

    /** Knopf im Eintragen-Platzhalter: meldet zweimal hintereinander fertig (wie Speichern und Zurück fast zugleich). */
    const val ZWEIMAL_FERTIG = "Zweimal fertig"

    val TAG: LocalDate = LocalDate.of(2026, 10, 24)
}

/** Dieselben Bildschirme mit einem anderen „Heute“. */
fun Bildschirme.mitHeute(heute: @Composable (NavHostController) -> Unit) = Bildschirme(
    heute, tagebuch, eintragen, einstellungen, serverVerbinden, zutaten, zutat, rezepte, rezeptDetail, editor, kochen, woche, einkauf,
)

/** Alle Bereiche als [Platzhalter] – für Navigationstests ohne ViewModels. */
val Bildschirme.Companion.platzhalter: Bildschirme
    get() = Bildschirme(
        heute = { nav ->
            // Wie HeuteRoute in Bildschirme.echt: Eintragen als neues Ziel, nur einmal obenauf
            Platzhalter(
                "Heute",
                aktionText = Platzhalter.SNACK_EINTRAGEN,
                onAktion = { nav.eintragenOeffnen(EintragenStart(Platzhalter.TAG, Mahlzeit.SNACK)) },
            )
        },
        tagebuch = { datum, nav ->
            // Wie TagebuchTagRoute in Bildschirme.echt
            Platzhalter(
                "Tagebuch", "Tagebuch $datum",
                aktionText = Platzhalter.TAGEBUCH_EINTRAGEN,
                onAktion = { nav.eintragenOeffnen(EintragenStart(datum)) },
            )
        },
        eintragen = { ziel, onFertig ->
            Platzhalter(
                "Eintragen", "Eintragen ${ziel.datum} ${ziel.mahlzeit}" + ziel.sofort?.let { " $it" }.orEmpty(),
                aktionText = Platzhalter.ZWEIMAL_FERTIG,
                onAktion = {
                    onFertig()
                    onFertig()
                },
            )
        },
        einstellungen = { Platzhalter("Einstellungen") },
        serverVerbinden = { Platzhalter("Server verbinden") },
        zutaten = { Platzhalter("Zutatenkatalog") },
        zutat = { id, _ -> Platzhalter("Zutat", "Zutat $id") },
        rezepte = { Platzhalter("Rezepte", "Rezeptliste") },
        rezeptDetail = { id, _ -> Platzhalter("Rezept", "Rezept $id") },
        editor = { id, gekochtMit, _ ->
            Platzhalter("Editor", if (id == null) "Neues Rezept" else "Rezept $id bearbeiten" + gekochtMit?.let { " · gekocht mit $it" }.orEmpty())
        },
        kochen = { id, portionen, _ ->
            Platzhalter("Kochen", "Rezept $id" + portionen?.let { n -> " · $n ${if (n == 1) "Portion" else "Portionen"}" }.orEmpty())
        },
        woche = { Platzhalter("Woche", "Wochenplan") },
        einkauf = { listeId, _ -> Platzhalter("Einkauf", listeId?.let { "Liste $it" } ?: "Einkaufsliste") },
    )
