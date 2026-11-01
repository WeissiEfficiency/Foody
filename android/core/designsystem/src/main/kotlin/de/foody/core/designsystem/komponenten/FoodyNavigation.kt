package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe

/** Höhe der schwebenden [FoodyNavigationsleiste] samt Rand bei normaler Schrift (64 dp + 2 × 12 dp). */
val LEISTE_REGELHOEHE = 88.dp

/**
 * Um wie viel die [FoodyNavigationsleiste] höher ist als [LEISTE_REGELHOEHE]: Bei großer Schrift wächst sie mit (V2).
 * Das Hauptgerüst misst die Leiste; ohne Leiste (Tablet, Kochmodus, Tests) bleibt es 0.
 */
val LocalLeistenZuwachs = compositionLocalOf { 0.dp }

/** [abstand] unter dem Inhalt plus [LocalLeistenZuwachs] – damit das Ende nicht unter einer gewachsenen Leiste liegt. */
@Composable
fun ueberLeiste(abstand: Dp): Dp = abstand + LocalLeistenZuwachs.current

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

/**
 * Ein Eintrag wie im Entwurf: Der aktive trägt eine Pille in der Behälterfarbe hinter dem Symbol, der Text wird fett.
 * Der Druckeffekt bleibt in dieser Pille, statt den ganzen Eintrag als Rechteck zu füllen.
 */
@Composable
private fun NavEintrag(ziel: NavZiel, aktiv: Boolean, onKlick: () -> Unit, modifier: Modifier) {
    val farben = FoodyTheme.farben
    val farbe = if (aktiv) farben.primaer else farben.nebentext
    val druck = remember { MutableInteractionSource() }
    Column(
        modifier
            .heightIn(min = 64.dp)
            .selectable(selected = aktiv, onClick = onKlick, role = Role.Tab, interactionSource = druck, indication = null)
            .padding(vertical = FoodyAbstaende.s2, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(CircleShape)
                .background(if (aktiv) farben.primaerBehaelter else Color.Transparent)
                .indication(druck, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ziel.symbol, contentDescription = null, tint = if (aktiv) farben.aufPrimaerBehaelter else farbe, modifier = Modifier.size(22.dp))
        }
        // Eine Zeile: bei großer Schrift lieber etwas kleiner als „Rezep-te“ auf zwei Zeilen (V2)
        Text(
            ziel.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (aktiv) FontWeight.Bold else FontWeight.Medium,
            color = farbe,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
        )
    }
}
