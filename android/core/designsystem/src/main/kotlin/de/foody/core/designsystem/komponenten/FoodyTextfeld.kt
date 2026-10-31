package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * Eingabefeld mit [beschriftung]; mehrzeilig ([einzeilig] `false`) mindestens drei Zeilen hoch. [fehler] steht als
 * Hinweis darunter. Mit [onFertig] trägt die Tastatur „Fertig“ und meldet es; [satzanfangGross] schreibt den ersten
 * Buchstaben groß.
 */
@Composable
fun FoodyTextfeld(
    beschriftung: String,
    wert: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    tastatur: KeyboardType = KeyboardType.Text,
    einzeilig: Boolean = true,
    fehler: String? = null,
    onFertig: (() -> Unit)? = null,
    satzanfangGross: Boolean = false,
    farben: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    onWert: (String) -> Unit,
) {
    OutlinedTextField(
        wert, onWert, modifier,
        label = { Text(beschriftung) },
        singleLine = einzeilig,
        minLines = if (einzeilig) 1 else 3,
        isError = fehler != null,
        supportingText = fehler?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = tastatur,
            imeAction = if (onFertig != null) ImeAction.Done else ImeAction.Default,
            capitalization = if (satzanfangGross) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onDone = onFertig?.let { { it() } }),
        colors = farben,
    )
}
