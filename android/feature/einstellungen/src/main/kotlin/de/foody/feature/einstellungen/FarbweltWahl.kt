package de.foody.feature.einstellungen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.Farbwelt

/**
 * Wahl der Farbwelt: je ein rundes Muster aus Verlauf und Hauptfarbe (im aktuellen Modus), darunter der Name. Die
 * Gruppe verhält sich wie Optionsfelder; die gewählte Farbwelt trägt einen Ring und einen Haken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FarbweltWahl(gewaehlt: Farbwelt, onWahl: (Farbwelt) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 4,
    ) {
        Farbwelt.entries.forEach { w -> FarbMuster(w, w == gewaehlt) { onWahl(w) } }
    }
}

@Composable
private fun FarbMuster(farbwelt: Farbwelt, gewaehlt: Boolean, onKlick: () -> Unit) {
    val dunkel = FoodyTheme.dunkel
    val werte = farbwelt.werte(dunkel)
    val ring = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.lichtkante
    Column(
        Modifier
            .width(80.dp)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onKlick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .border(if (gewaehlt) 2.dp else 1.dp, ring, CircleShape)
                .padding(if (gewaehlt) 4.dp else 1.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(werte.farben.grund)
                    val r = size.width * 0.75f
                    drawRect(Brush.radialGradient(listOf(werte.verlauf.oben.copy(alpha = 1f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.25f), r))
                    drawRect(Brush.radialGradient(listOf(werte.verlauf.unten.copy(alpha = 1f), Color.Transparent), Offset(size.width * 0.75f, size.height * 0.8f), r))
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(werte.farben.primaer).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (gewaehlt) Icon(Icons.Filled.Check, contentDescription = null, tint = werte.farben.aufPrimaer, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            farbwelt.anzeigename,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (gewaehlt) FontWeight.Bold else FontWeight.Medium,
            color = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
