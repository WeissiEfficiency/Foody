package de.foody.feature.einkauf

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.komponenten.Suchfeld

/**
 * „Was willst du einkaufen?“ als Pille: Tippen filtert den Katalog, „Fertig“ auf der Tastatur trägt ein. Einen eigenen
 * „+“ gibt es nicht; dafür steht unter den Treffern die Kachel „„…“ hinzufügen“.
 */
@Composable
internal fun EingabeFeld(text: String, onText: (String) -> Unit, onFertig: () -> Unit, modifier: Modifier = Modifier) {
    Suchfeld(text, onText, "Was willst du einkaufen?", modifier.fillMaxWidth(), onFertig = onFertig)
}
