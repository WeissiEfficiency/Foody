package de.foody.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.app.ui.theme.EyebrowStyle

/**
 * Seitenkopf aller Hauptbereiche: kleines farbiges Eyebrow-Label, große fette Überschrift, runde Aktionen.
 * Ersetzt die klassische TopAppBar, damit Planer, Einkauf und Vorrat wie die Rezeptseite wirken.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = EyebrowStyle, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

/** Runde, getönte Kopf-Aktion. */
@Composable
fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick) { Icon(icon, description) }
}

/** Großer runder Abhak-Kreis wie in Einkaufs-Apps; mint gefüllt, wenn erledigt. */
@Composable
fun RoundCheck(checked: Boolean, description: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(28.dp).clip(CircleShape)
            .background(if (checked) colors.primary else colors.surfaceContainerLowest)
            .border(2.dp, if (checked) colors.primary else colors.outline, CircleShape)
            .clickable(onClick = onToggle)
            .semantics { role = Role.Checkbox; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = colors.onPrimary)
    }
}
