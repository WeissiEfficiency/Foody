package de.foody.feature.kochen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.token.GlasStufe

/** Ein Arbeitsschritt: Text groß, „Du brauchst“ mit Teilmengen, Timer-Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SchrittSeite(s: KochSchritt, abgehakt: Set<Int>, a: KochAktionen) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Groß für Armlänge, aber normal gewichtet: lange fette Absätze lesen sich schlechter
        Text(
            s.text, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
            color = FoodyTheme.farben.text,
        )
        if (s.timer.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.timer.forEach { t -> FoodyChip("▶ ${t.label}", ausgewaehlt = false, onKlick = { a.onTimerStarten(t) }) }
            }
        }
        if (s.zutaten.isNotEmpty()) {
            GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Augenbraue("Du brauchst")
                    s.zutaten.forEach { z -> SchrittZutatZeile(z, z.zeilenIndex in abgehakt) }
                }
            }
        }
    }
}

@Composable
private fun SchrittZutatZeile(z: SchrittZutat, bereit: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.4f)) {
            Text(z.menge ?: "nach Bedarf", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
            z.gesamt?.let { Text("von $it", style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext) }
        }
        Text(z.name, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        // Platz immer frei halten, sonst stehen die Namen der Zeilen versetzt
        Box(Modifier.size(24.dp)) {
            if (bereit) Icon(Icons.Outlined.CheckCircle, contentDescription = "bereitgelegt", tint = FoodyTheme.farben.primaer)
        }
    }
}
