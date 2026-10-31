package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.AugenbraueStil
import java.util.Locale

/** Abschnittstitel (für TalkBack eine Überschrift) mit optionalem Link rechts („6 offen“). */
@Composable
fun Abschnittskopf(
    titel: String,
    modifier: Modifier = Modifier,
    linkText: String? = null,
    onLink: (() -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            titel,
            style = MaterialTheme.typography.titleLarge,
            color = FoodyTheme.farben.text,
            modifier = Modifier.weight(1f).ueberschrift(),
        )
        if (linkText != null && onLink != null) {
            FoodyKnopf(linkText, onKlick = onLink, art = KnopfArt.TEXT)
        }
    }
}

/** Markiert einen Titel für TalkBack als Überschrift (Seiten- und Blatttitel, Abschnittsköpfe). */
fun Modifier.ueberschrift(): Modifier = semantics { heading() }

/** Kleine Zeile in Großbuchstaben über Überschriften („ABENDESSEN · 18:30“). */
@Composable
fun Augenbraue(text: String, modifier: Modifier = Modifier, farbe: Color = FoodyTheme.farben.nebentext) {
    Text(text.uppercase(Locale.GERMAN), style = AugenbraueStil, color = farbe, modifier = modifier)
}
