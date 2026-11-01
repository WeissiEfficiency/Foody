package de.foody.feature.kochen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.StepTimer

/**
 * Ein Arbeitsschritt wie im Entwurf: große Glaskarte mit dem Text in Fraunces, „Du brauchst“ mit Teilmengen und je
 * Zeitangabe ein Timer-Kasten („20:00 · Start“); darunter, falls es einen gibt, „Als nächstes“ mit dem folgenden Schritt.
 */
@Composable
internal fun SchrittSeite(s: KochSchritt, abgehakt: Set<Int>, a: KochAktionen, naechster: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth().heightIn(min = 360.dp)) {
            Column(Modifier.padding(FoodyAbstaende.s5), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s5)) {
                Text(s.text, style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
                if (s.zutaten.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                        Augenbraue("Du brauchst")
                        s.zutaten.forEach { z -> SchrittZutatZeile(z, z.zeilenIndex in abgehakt) }
                    }
                }
                s.timer.forEach { t -> TimerKasten(t) { a.onTimerStarten(t) } }
            }
        }
        if (naechster != null) {
            GlasFlaeche(GlasStufe.LEICHT, Modifier.fillMaxWidth(), form = FoodyFormen.mittel) {
                Column(Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3)) {
                    Augenbraue("Als nächstes")
                    Text(naechster, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext, maxLines = 2)
                }
            }
        }
    }
}

/** Kasten in der Schrittkarte: „TIMER“, die Dauer groß, „Start“. */
@Composable
private fun TimerKasten(t: StepTimer, onStart: () -> Unit) {
    GlasFlaeche(GlasStufe.LEICHT, Modifier.fillMaxWidth(), form = FoodyFormen.mittel, unschaerfeErlaubt = false) {
        Row(Modifier.padding(FoodyAbstaende.s4), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Augenbraue("Timer · ${t.label}")
                Text(restzeit(t.duration.toMillis()), style = MaterialTheme.typography.displaySmall, color = FoodyTheme.farben.text)
            }
            FoodyKnopf("Start", onStart)
        }
    }
}

@Composable
private fun SchrittZutatZeile(z: SchrittZutat, bereit: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.4f)) {
            Text(z.menge ?: "nach Bedarf", style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.primaer)
            z.gesamt?.let { Text("von $it", style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext) }
        }
        Text(z.name, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        // Platz immer frei halten, sonst stehen die Namen der Zeilen versetzt
        Box(Modifier.size(24.dp)) {
            if (bereit) Icon(Icons.Outlined.CheckCircle, contentDescription = "bereitgelegt", tint = FoodyTheme.farben.primaer)
        }
    }
}
