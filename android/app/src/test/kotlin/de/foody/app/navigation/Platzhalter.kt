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
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche

/** Ersatz für einen Bereich in Navigationstests: zeigt den Titel und die übergebenen Argumente. */
@Composable
fun Platzhalter(titel: String, argumente: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeererZustandFlaeche(Icons.Outlined.Construction, titel, Platzhalter.TEXT, aktionText = null, onAktion = null)
            if (argumente != null) {
                Text(argumente, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
    }
}

object Platzhalter {
    const val TEXT = "Platzhalter für Tests"
}

/** Alle Bereiche als [Platzhalter] – für Navigationstests ohne ViewModels. */
val Bildschirme.Companion.platzhalter: Bildschirme
    get() = Bildschirme(
        heute = { Platzhalter("Heute") },
        tagebuch = { datum, _ -> Platzhalter("Tagebuch", "Tagebuch $datum") },
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
