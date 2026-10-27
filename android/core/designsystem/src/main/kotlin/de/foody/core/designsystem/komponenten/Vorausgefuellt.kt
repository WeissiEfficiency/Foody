package de.foody.core.designsystem.komponenten

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import de.foody.core.designsystem.FoodyTheme

/**
 * Farben eines Textfelds, das ein Scan vorausgefüllt hat: [an] → Rahmen und Beschriftung in der Akzentfarbe, bis der
 * Nutzer das Feld ändert; sonst die üblichen Farben. Für `OutlinedTextField(colors = …)`.
 */
@Composable
fun vorausgefuelltFarben(an: Boolean): TextFieldColors {
    if (!an) return OutlinedTextFieldDefaults.colors()
    val akzent = FoodyTheme.farben.primaer
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = akzent,
        unfocusedBorderColor = akzent,
        focusedLabelColor = akzent,
        unfocusedLabelColor = akzent,
    )
}
