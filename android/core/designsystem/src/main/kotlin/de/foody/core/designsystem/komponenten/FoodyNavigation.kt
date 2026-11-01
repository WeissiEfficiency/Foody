package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe

/** Ein Ziel der Hauptnavigation. */
data class NavZiel(val text: String, val symbol: ImageVector)

/** Schwebende Glas-Leiste unten (Handy), 12 dp vom Rand. */
@Composable
fun FoodyNavigationsleiste(ziele: List<NavZiel>, aktiv: Int, onWahl: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlasFlaeche(GlasStufe.LEICHT, modifier.padding(FoodyAbstaende.s3), form = FoodyFormen.leiste) {
        Row(Modifier.padding(horizontal = FoodyAbstaende.s1), verticalAlignment = Alignment.CenterVertically) {
            ziele.forEachIndexed { i, ziel ->
                NavEintrag(ziel, i == aktiv, { onWahl(i) }, Modifier.weight(1f))
            }
        }
    }
}

/** Senkrechte Glas-Leiste links (Tablet). */
@Composable
fun FoodyNavigationsRail(ziele: List<NavZiel>, aktiv: Int, onWahl: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlasFlaeche(GlasStufe.LEICHT, modifier.padding(FoodyAbstaende.s3), form = FoodyFormen.leiste) {
        Column(Modifier.padding(vertical = FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
            ziele.forEachIndexed { i, ziel ->
                NavEintrag(ziel, i == aktiv, { onWahl(i) }, Modifier.width(88.dp))
            }
        }
    }
}

@Composable
private fun NavEintrag(ziel: NavZiel, aktiv: Boolean, onKlick: () -> Unit, modifier: Modifier) {
    val farbe = if (aktiv) FoodyTheme.farben.primaer else FoodyTheme.farben.nebentext
    Column(
        modifier
            .heightIn(min = 64.dp)
            .selectable(selected = aktiv, onClick = onKlick, role = Role.Tab)
            .padding(vertical = FoodyAbstaende.s2, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ziel.symbol, contentDescription = null, tint = farbe)
        Text(ziel.text, style = MaterialTheme.typography.labelMedium, color = farbe, textAlign = TextAlign.Center)
    }
}
