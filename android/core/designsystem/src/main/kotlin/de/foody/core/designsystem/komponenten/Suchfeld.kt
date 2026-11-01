package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe

/**
 * Such- oder Eingabefeld als Glas-Pille ohne Rahmen, wie im Entwurf („Rezepte, Zutaten …“, „Artikel hinzufügen …“).
 * [symbol] links (Standard Lupe, `null` = keins); ist etwas eingegeben, leert ein Kreuz rechts das Feld. Mit [onFertig]
 * trägt die Tastatur „Fertig“.
 */
@Composable
fun Suchfeld(
    wert: String,
    onWert: (String) -> Unit,
    platzhalter: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector? = Icons.Outlined.Search,
    onFertig: (() -> Unit)? = null,
) {
    val farben = FoodyTheme.farben
    GlasFlaeche(GlasStufe.DICHT, modifier.fillMaxWidth().heightIn(min = 52.dp), form = CircleShape, unschaerfeErlaubt = false) {
        Row(
            Modifier.padding(start = FoodyAbstaende.s5, end = FoodyAbstaende.s1).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            if (symbol != null) Icon(symbol, contentDescription = null, tint = farben.nebentext, modifier = Modifier.size(20.dp))
            BasicTextField(
                wert,
                onWert,
                Modifier.weight(1f).semantics { contentDescription = platzhalter },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = farben.text),
                singleLine = true,
                cursorBrush = SolidColor(farben.primaer),
                keyboardOptions = KeyboardOptions(imeAction = if (onFertig != null) ImeAction.Done else ImeAction.Search),
                keyboardActions = KeyboardActions(onDone = onFertig?.let { { it() } }, onSearch = onFertig?.let { { it() } }),
                decorationBox = { feld ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (wert.isEmpty()) Text(platzhalter, style = MaterialTheme.typography.bodyLarge, color = farben.nebentext)
                        feld()
                    }
                },
            )
            if (wert.isNotEmpty()) {
                IconButton(onClick = { onWert("") }) { Icon(Icons.Outlined.Close, contentDescription = "Eingabe leeren", tint = farben.nebentext) }
            } else {
                Box(Modifier.size(FoodyAbstaende.s3))
            }
        }
    }
}
