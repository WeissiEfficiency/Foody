package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import de.foody.core.designsystem.FoodyTheme

/**
 * Aufklappmenü der App (⋮, „+“, Kamera/Galerie, Sortierung) im gewählten Stil: deckend in der Glasfarbe des Stils
 * (`werte.glas` ist ohne Alpha, `KontrastTest` prüft Text darauf), mittlere Ecken und Kante wie Glas und Karten. Die
 * Punkte sind [FoodyMenuePunkt]e.
 */
@Composable
fun FoodyMenue(offen: Boolean, onSchliessen: () -> Unit, modifier: Modifier = Modifier, inhalt: @Composable ColumnScope.() -> Unit) {
    val stil = FoodyTheme.stil
    DropdownMenu(
        expanded = offen,
        onDismissRequest = onSchliessen,
        modifier = modifier,
        shape = FoodyTheme.formen.mittel,
        containerColor = stil.werte.glas,
        border = BorderStroke(stil.kante, FoodyTheme.farben.lichtkante),
        content = inhalt,
    )
}

/**
 * Ein Punkt in [FoodyMenue] in der Textschrift des Stils, mindestens 48 dp hoch; [symbol] davor, [gewaehlt] zeigt am
 * Ende einen Haken (TalkBack: „ausgewählt“). Das Menü schließt der Aufrufer in [onKlick].
 */
@Composable
fun FoodyMenuePunkt(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, symbol: ImageVector? = null, gewaehlt: Boolean = false) {
    val farbe = FoodyTheme.farben.text
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = farbe) },
        onClick = onKlick,
        modifier = if (gewaehlt) modifier.semantics { selected = true } else modifier,
        leadingIcon = symbol?.let { { Icon(it, contentDescription = null, tint = farbe) } },
        trailingIcon = if (gewaehlt) {
            { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.testTag("menue-haken"), tint = farbe) }
        } else {
            null
        },
    )
}
