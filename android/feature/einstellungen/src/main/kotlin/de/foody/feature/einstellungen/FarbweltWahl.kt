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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Wahl der Farbwelt: je ein rundes Muster aus Verlauf und Hauptfarbe (im aktuellen Modus), darunter der Name. Die
 * Gruppe verhält sich wie Optionsfelder; die gewählte Farbwelt trägt einen Ring und einen Haken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FarbweltWahl(gewaehlt: Farbwelt, onWahl: (Farbwelt) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = VerteiltMitAbstand,
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        maxItemsInEachRow = 4,
    ) {
        Farbwelt.entries.forEach { w -> FarbMuster(w, w == gewaehlt) { onWahl(w) } }
    }
}

/**
 * Wie [Arrangement.SpaceBetween], aber mit mindestens [FoodyAbstaende.s2] dazwischen: Danach bricht die Reihe um, sonst
 * stießen die Namen bei großer Schrift aneinander (V2).
 */
private object VerteiltMitAbstand : Arrangement.Horizontal {
    override val spacing = FoodyAbstaende.s2

    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) =
        with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

@Composable
private fun FarbMuster(farbwelt: Farbwelt, gewaehlt: Boolean, onKlick: () -> Unit) {
    val dunkel = FoodyTheme.dunkel
    val werte = farbwelt.werte(dunkel)
    val ring = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.lichtkante
    Column(
        Modifier
            // mindestens 80 dp, bei großer Schrift so breit wie der Name: die Reihe bricht dann um, nicht das Wort (V2)
            .widthIn(min = 80.dp)
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
