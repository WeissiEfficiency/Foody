package de.foody.feature.einstellungen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Wahl der Farbwelt: je ein rundes Muster aus Verlauf und Hauptfarbe (im aktuellen Modus), darunter der Name. Die
 * Gruppe verhält sich wie Optionsfelder; die gewählte Farbwelt trägt einen Ring und einen Haken.
 *
 * Festes Raster mit gleich breiten Spalten (höchstens vier): Eine kürzere letzte Reihe steht links unter den Spalten
 * statt verteilt mit Loch. Die Spaltenzahl richtet sich nach Breite und Schriftgröße, damit „Heidelbeere“ auch bei
 * großer Schrift nicht umbricht (V2).
 */
@Composable
internal fun FarbweltWahl(gewaehlt: Farbwelt, onWahl: (Farbwelt) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val zelle = maxOf(80.dp, 66.dp * LocalDensity.current.fontScale)
        val abstand = FoodyAbstaende.s2
        val spalten = ((maxWidth + abstand) / (zelle + abstand)).toInt().coerceIn(1, 4)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
            Farbwelt.entries.chunked(spalten).forEach { reihe ->
                Row(horizontalArrangement = Arrangement.spacedBy(abstand)) {
                    reihe.forEach { w -> FarbMuster(w, w == gewaehlt, Modifier.weight(1f)) { onWahl(w) } }
                    repeat(spalten - reihe.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FarbMuster(farbwelt: Farbwelt, gewaehlt: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val dunkel = FoodyTheme.dunkel
    val werte = farbwelt.werte(dunkel)
    val ring = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.lichtkante
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onKlick)
            .padding(vertical = FoodyAbstaende.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .border(if (gewaehlt) 2.dp else 1.dp, ring, CircleShape)
                .padding(if (gewaehlt) FoodyAbstaende.s1 else 1.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(werte.farben.grund)
                    val r = size.width * 0.75f
                    fun fleck(farbe: Color, x: Float, y: Float) =
                        drawRect(Brush.radialGradient(listOf(farbe.copy(alpha = 1f), Color.Transparent), Offset(size.width * x, size.height * y), r))
                    fleck(werte.verlauf.oben, 0.3f, 0.25f)
                    fleck(werte.verlauf.unten, 0.75f, 0.8f)
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
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
