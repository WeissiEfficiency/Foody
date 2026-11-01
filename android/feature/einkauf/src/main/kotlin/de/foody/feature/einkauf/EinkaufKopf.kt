package de.foody.feature.einkauf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Seitenkopf
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe

/**
 * Kopf wie im Entwurf: Titel, rechts die Aktionen (Menü); darunter „26.10.–1.11. · 12 offen“ und, sobald Artikel da
 * sind, „7 von 13 erledigt“ mit Fortschrittsbalken in Oliv.
 */
@Composable
internal fun EinkaufKopf(ansicht: EinkaufsAnsicht?, aktionen: @Composable RowScope.() -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Seitenkopf("Einkauf", aktionen = aktionen)
        ansicht?.let {
            Text("${it.name} · ${it.anzahlOffen} offen", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            val gesamt = it.alle.size
            if (gesamt > 0) {
                val erledigt = it.alle.count { a -> a.gekauft }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$erledigt von $gesamt") }
                        append(" erledigt")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodyTheme.farben.text,
                )
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(FoodyTheme.farben.balkenSpur)) {
                    Box(Modifier.fillMaxWidth(erledigt.toFloat() / gesamt).height(6.dp).clip(CircleShape).background(FoodyTheme.farben.oliv))
                }
            }
        }
    }
}

/** Erscheint, solange der Abgleich mit dem Plan etwas ändern würde. */
@Composable
internal fun AbgleichHinweis(onKlick: () -> Unit) {
    GlasFlaeche(
        GlasStufe.DICHT,
        Modifier.fillMaxWidth().clip(FoodyFormen.mittel).clickable(role = Role.Button, onClick = onKlick),
        form = FoodyFormen.mittel,
    ) {
        Row(Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Plan geändert – abgleichen", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.primaer,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.primaer)
        }
    }
}
