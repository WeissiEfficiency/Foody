package de.foody.feature.kochen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyAbstaende

/** Tablet: alle Seiten links zum Springen – „Bereitlegen“ und die Schritte, je auf eine Zeile gekürzt. */
@Composable
internal fun SeitenListe(z: KochZustand.Da, aktuell: Int, onSeite: (Int) -> Unit) {
    Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(FoodyAbstaende.s3)) {
        (0 until z.seitenzahl).forEach { seite ->
            val schritt = z.schritt(seite)
            val titel = if (schritt == null) "Bereitlegen" else "Schritt ${seite + if (z.hatBereitlegen) 0 else 1}"
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (seite == aktuell) FoodyTheme.farben.primaerBehaelter else Color.Transparent)
                    .clickable { onSeite(seite) }.padding(horizontal = FoodyAbstaende.s3, vertical = FoodyAbstaende.s2),
            ) {
                Text(titel, style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.text)
                schritt?.let {
                    Text(
                        it.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
                    )
                }
            }
        }
    }
}
