package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme

/** Primär (gefüllt), Umriss (sekundäre Aktion) oder Text (Link-artig). */
enum class KnopfArt { PRIMAER, UMRISS, TEXT }

/** Knopf mit mindestens 48 dp Höhe; der Text bricht bei großer Schrift um, statt abgeschnitten zu werden. */
@Composable
fun FoodyKnopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    art: KnopfArt = KnopfArt.PRIMAER,
    aktiv: Boolean = true,
) {
    val m = modifier.heightIn(min = 48.dp)
    val farben = FoodyTheme.farben
    when (art) {
        KnopfArt.PRIMAER -> Button(
            onClick = onKlick,
            modifier = m,
            enabled = aktiv,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = farben.primaer, contentColor = farben.aufPrimaer),
        ) { Text(text) }
        KnopfArt.UMRISS -> OutlinedButton(
            onClick = onKlick,
            modifier = m,
            enabled = aktiv,
            shape = CircleShape,
            border = BorderStroke(1.dp, farben.primaer),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = farben.primaer),
        ) { Text(text) }
        KnopfArt.TEXT -> TextButton(
            onClick = onKlick,
            modifier = m,
            enabled = aktiv,
            colors = ButtonDefaults.textButtonColors(contentColor = farben.primaer),
        ) { Text(text) }
    }
}
