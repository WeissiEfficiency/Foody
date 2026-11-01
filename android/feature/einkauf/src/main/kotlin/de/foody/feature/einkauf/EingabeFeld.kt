package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.komponenten.RunderKnopf
import de.foody.core.designsystem.komponenten.Suchfeld
import de.foody.core.designsystem.token.FoodyAbstaende

/** „Was willst du einkaufen?“ als Glas-Pille, daneben ein runder „+“: Tippen filtert den Katalog, „+“ oder „Fertig“ trägt ein. */
@Composable
internal fun EingabeFeld(text: String, onText: (String) -> Unit, onFertig: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Suchfeld(text, onText, "Was willst du einkaufen?", Modifier.weight(1f), onFertig = onFertig)
        RunderKnopf(Icons.Outlined.Add, "Hinzufügen", onFertig, primaer = true, aktiv = text.isNotBlank())
    }
}
