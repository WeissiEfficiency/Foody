package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme

/** Leerer Zustand als Zeile mit gestrichelter Kante („Noch kein Snack eingetragen · Eintragen“). */
@Composable
fun LeererZustandZeile(text: String, aktionText: String, onAktion: () -> Unit, modifier: Modifier = Modifier) {
    val kante = FoodyTheme.farben.nebentext.copy(alpha = 0.5f)
    val m = modifier
        .fillMaxWidth()
        .drawBehind {
            drawRoundRect(
                color = kante,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
            )
        }
        .padding(horizontal = 14.dp, vertical = 8.dp)
    val textTeil: @Composable (Modifier) -> Unit = { tm ->
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext, modifier = tm)
    }
    if (grosseSchrift()) {
        Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            textTeil(Modifier)
            FoodyKnopf(aktionText, onKlick = onAktion, art = KnopfArt.UMRISS)
        }
    } else {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            textTeil(Modifier.weight(1f))
            FoodyKnopf(aktionText, onKlick = onAktion, art = KnopfArt.UMRISS)
        }
    }
}

/** Leerer Zustand für einen ganzen Bereich: Symbol, Titel, Satz und optional eine Aktion. */
@Composable
fun LeererZustandFlaeche(
    symbol: ImageVector,
    titel: String,
    text: String,
    aktionText: String?,
    onAktion: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = FoodyTheme.farben.nebentext, modifier = Modifier.size(48.dp))
        Text(titel, style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext, textAlign = TextAlign.Center)
        if (aktionText != null && onAktion != null) FoodyKnopf(aktionText, onKlick = onAktion)
    }
}
