package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyAbstaende

/** Filter-Chip (an/aus); meldet „ausgewählt“ an TalkBack, Berührfläche mindestens 48 dp hoch. */
@Composable
fun FoodyChip(text: String, ausgewaehlt: Boolean, onKlick: () -> Unit, modifier: Modifier = Modifier) {
    val farben = FoodyTheme.farben
    Surface(
        selected = ausgewaehlt,
        onClick = onKlick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = if (ausgewaehlt) farben.text else farben.grund.copy(alpha = 0f),
        contentColor = if (ausgewaehlt) farben.grund else farben.text,
        border = if (ausgewaehlt) null else BorderStroke(1.dp, farben.nebentext),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
