package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Filter-Chip (an/aus) wie im Entwurf: gewählt in Primär gefüllt, sonst mit feinem Rahmen. Meldet „ausgewählt“ an
 * TalkBack; sichtbar 36 dp hoch, die Berührfläche darum 48 dp.
 */
@Composable
fun FoodyChip(text: String, ausgewaehlt: Boolean, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val farben = FoodyTheme.farben
    val druck = remember { MutableInteractionSource() }
    // Düstere Sage, Immersiv: eckig, gewählt mit Rahmen im Akzent und leichtem Schimmer statt gefüllt
    val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
    val zier = FoodyTheme.stil.zier
    Box(
        modifier.heightIn(min = 48.dp).selectable(selected = ausgewaehlt, onClick = onKlick, interactionSource = druck, indication = null)
            .then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(FoodyTheme.formen.knopf)
                .background(
                    when {
                        !ausgewaehlt -> Color.Transparent
                        sage -> zier.copy(alpha = 0.16f)
                        else -> farben.primaer
                    },
                )
                .border(
                    1.dp,
                    when {
                        sage && ausgewaehlt -> zier
                        ausgewaehlt -> Color.Transparent
                        sage -> farben.lichtkante
                        else -> farben.nebentext.copy(alpha = 0.55f)
                    },
                    FoodyTheme.formen.knopf,
                )
                .indication(druck, ripple())
                .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = if (sage) Sage.text(15.5f) else MaterialTheme.typography.labelLarge,
                color = when {
                    sage && ausgewaehlt -> sageHell()
                    ausgewaehlt -> farben.aufPrimaer
                    else -> farben.text
                },
            )
        }
    }
}
