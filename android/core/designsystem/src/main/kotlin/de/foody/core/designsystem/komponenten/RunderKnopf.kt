package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe

/**
 * Runder Knopf mit Symbol: als Glas (40 dp, Zurück, Pfeile, Konto) oder [primaer] gefüllt (48 dp, „+“ im Kopf).
 * Die Berührfläche ist immer mindestens 48 dp.
 */
@Composable
fun RunderKnopf(
    symbol: ImageVector,
    beschreibung: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    primaer: Boolean = false,
    aktiv: Boolean = true,
) {
    val farben = FoodyTheme.farben
    val druck = remember { MutableInteractionSource() }
    val groesse = if (primaer) 48.dp else 40.dp
    // Klick und Beschreibung auf der 48-dp-Fläche, Bild und Druckeffekt im sichtbaren Kreis
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = aktiv, role = Role.Button, interactionSource = druck, indication = null, onClick = onKlick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        val kreis = Modifier.size(groesse).clip(CircleShape)
        if (primaer) {
            Box(kreis.background(farben.primaer).indication(druck, ripple()), contentAlignment = Alignment.Center) {
                Icon(symbol, contentDescription = null, tint = farben.aufPrimaer)
            }
        } else {
            GlasFlaeche(GlasStufe.LEICHT, Modifier.size(groesse), form = CircleShape, unschaerfeErlaubt = false) {
                Box(kreis.indication(druck, ripple()), contentAlignment = Alignment.Center) {
                    Icon(symbol, contentDescription = null, tint = if (aktiv) farben.text else farben.nebentext, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Schwebender Pillen-Knopf unten rechts („+ Eintragen“, „+ Zutat anlegen“), in Primär. */
@Composable
fun FoodyFab(text: String, symbol: ImageVector, onKlick: () -> Unit, modifier: Modifier = Modifier) {
    val farben = FoodyTheme.farben
    Surface(
        onKlick,
        modifier.heightIn(min = 56.dp),
        shape = CircleShape,
        color = farben.primaer,
        contentColor = farben.aufPrimaer,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = FoodyAbstaende.s6, vertical = FoodyAbstaende.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        ) {
            Icon(symbol, contentDescription = null)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
