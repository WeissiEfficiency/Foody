package de.foody.feature.einkauf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe

/** „Einkauf“, darunter „26.10.–1.11. · 12 offen“; rechts die Aktionen (Menü). */
@Composable
internal fun EinkaufKopf(ansicht: EinkaufsAnsicht?, aktionen: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Einkauf", style = MaterialTheme.typography.headlineLarge, color = FoodyTheme.farben.text)
            ansicht?.let {
                Text("${it.name} · ${it.anzahlOffen} offen", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
        aktionen()
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Plan geändert – abgleichen", style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.primaer,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = FoodyTheme.farben.primaer)
        }
    }
}
