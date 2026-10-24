package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import java.util.Locale

/**
 * Kachel im Raster (Einkauf, Katalog): Monogramm oder Symbol, Name, Menge, optional Untertitel. Abgehakt wird der
 * Name durchgestrichen und gedimmt (Nebentext hält 4,5:1) und an TalkBack als „abgehakt“ gemeldet.
 */
@Composable
fun Kachel(
    name: String,
    menge: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    abgehakt: Boolean = false,
    symbol: (@Composable () -> Unit)? = null,
) {
    val farben = FoodyTheme.farben
    GlasFlaeche(
        GlasStufe.DICHT,
        modifier
            .heightIn(min = 92.dp)
            .clip(FoodyFormen.mittel)
            .clickable(onClick = onKlick)
            .semantics { if (abgehakt) stateDescription = "abgehakt" },
        form = FoodyFormen.mittel,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (symbol != null) {
                symbol()
            } else {
                Box(
                    Modifier
                        .quadratisch(mindestens = 30.dp)
                        .clip(CircleShape)
                        .background(farben.primaerBehaelter)
                        .semantics { hideFromAccessibility() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(Locale.GERMAN),
                        style = MaterialTheme.typography.labelLarge,
                        color = farben.aufPrimaerBehaelter,
                    )
                }
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = if (abgehakt) farben.nebentext else farben.text,
                textDecoration = if (abgehakt) TextDecoration.LineThrough else null,
            )
            Text(
                listOfNotNull(menge, untertitel).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = farben.nebentext,
            )
        }
    }
}

/** Quadrat mit Kante = max(Inhalt, [mindestens]) – der Monogramm-Kreis wächst mit großer Schrift mit. */
private fun Modifier.quadratisch(mindestens: Dp): Modifier = layout { messbar, vorgabe ->
    val inhalt = messbar.measure(vorgabe.copy(minWidth = 0, minHeight = 0))
    val kante = maxOf(inhalt.width, inhalt.height, mindestens.roundToPx())
    layout(kante, kante) { inhalt.placeRelative((kante - inhalt.width) / 2, (kante - inhalt.height) / 2) }
}
