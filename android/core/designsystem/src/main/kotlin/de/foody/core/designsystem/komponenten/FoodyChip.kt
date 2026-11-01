package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Filter-Chip (an/aus) wie im Entwurf: gewählt in Primär gefüllt, sonst mit feinem Rahmen. Meldet „ausgewählt“ an
 * TalkBack; sichtbar 36 dp hoch, die Berührfläche darum 48 dp.
 */
@Composable
fun FoodyChip(text: String, ausgewaehlt: Boolean, onKlick: () -> Unit, modifier: Modifier = Modifier) {
    val farben = FoodyTheme.farben
    val druck = remember { MutableInteractionSource() }
    Box(
        modifier.heightIn(min = 48.dp).selectable(selected = ausgewaehlt, onClick = onKlick, interactionSource = druck, indication = null),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .background(if (ausgewaehlt) farben.primaer else Color.Transparent)
                .border(1.dp, if (ausgewaehlt) Color.Transparent else farben.nebentext.copy(alpha = 0.55f), CircleShape)
                .indication(druck, ripple())
                .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (ausgewaehlt) farben.aufPrimaer else farben.text)
        }
    }
}
