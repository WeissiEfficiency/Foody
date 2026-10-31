package de.foody.core.designsystem.komponenten

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe

/** Was der Sync-Status zeigt; die Zuordnung der Kennungen aus `:core:sync` folgt in Phase 5. */
sealed interface SyncAnzeige {
    data object Synchron : SyncAnzeige
    data object Laeuft : SyncAnzeige
    data object Wartet : SyncAnzeige
    data class Fehler(val kurztext: String) : SyncAnzeige
}

/** Das Symbol dreht sich nur bei laufendem Sync und erlaubten Animationen. */
internal fun symbolDreht(anzeige: SyncAnzeige, animationen: Boolean): Boolean = anzeige == SyncAnzeige.Laeuft && animationen

/** Chip mit Symbol und Text (nie nur Farbe); Änderungen meldet TalkBack höflich (Live-Region). */
@Composable
fun SyncStatus(anzeige: SyncAnzeige, modifier: Modifier = Modifier) {
    val f = FoodyTheme.farben
    val (symbol: ImageVector, text: String) = when (anzeige) {
        SyncAnzeige.Synchron -> Icons.Outlined.Check to "Synchron"
        SyncAnzeige.Laeuft -> Icons.Outlined.Sync to "Läuft …"
        SyncAnzeige.Wartet -> Icons.Outlined.CloudOff to "Wartet auf Netz"
        is SyncAnzeige.Fehler -> Icons.Outlined.WarningAmber to anzeige.kurztext
    }
    val tint = when (anzeige) {
        SyncAnzeige.Synchron -> f.oliv
        is SyncAnzeige.Fehler -> f.primaer
        else -> f.nebentext
    }
    val winkel = if (symbolDreht(anzeige, animationenErlaubt())) {
        val drehung = rememberInfiniteTransition(label = "sync")
        drehung.animateFloat(
            initialValue = 0f,
            targetValue = -360f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "sync-winkel",
        ).value
    } else {
        0f
    }
    GlasFlaeche(GlasStufe.LEICHT, modifier.semantics { liveRegion = LiveRegionMode.Polite }, form = CircleShape) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = FoodyAbstaende.s3, vertical = FoodyAbstaende.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).rotate(winkel))
            Text(text, style = MaterialTheme.typography.labelLarge, color = f.text)
        }
    }
}
