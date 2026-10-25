package de.foody.feature.einkauf

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import de.foody.core.designsystem.FoodyTheme

/** „Was willst du einkaufen?“ – Tippen filtert den Katalog, „Fertig“ trägt ein. */
@Composable
internal fun EingabeFeld(text: String, onText: (String) -> Unit, onFertig: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        text, onText,
        modifier.fillMaxWidth(),
        placeholder = { Text("Was willst du einkaufen?") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = FoodyTheme.farben.nebentext) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) { Icon(Icons.Outlined.Close, contentDescription = "Eingabe leeren") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onFertig() }),
    )
}
