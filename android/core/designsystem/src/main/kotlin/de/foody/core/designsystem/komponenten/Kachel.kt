package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import java.util.Locale

/** Testkennung des Monogramm-Kreises. */
const val MONOGRAMM_TAG = "kachel-monogramm"

/**
 * Kachel im Raster (Einkauf, Katalog): Monogramm oder Symbol, Name, Menge, optional Untertitel. Abgehakt wird der
 * Name durchgestrichen und gedimmt (Nebentext hält 4,5:1) und an TalkBack als „abgehakt“ gemeldet.
 * [aufDerListe] (Katalog) zeigt einen Rand in Primär und einen Haken; abgehakt ohne eigenes Symbol steht ein Haken in
 * Oliv statt des Monogramms (Entwurf). [onLangdruck] öffnet Details
 * (TalkBack: „Details“); [zustandsText] ersetzt die Zustandsmeldung („auf der Liste“, „gekauft“, …).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Kachel(
    name: String,
    menge: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    abgehakt: Boolean = false,
    symbol: (@Composable () -> Unit)? = null,
    aufDerListe: Boolean = false,
    onLangdruck: (() -> Unit)? = null,
    zustandsText: String? = null,
) {
    val farben = FoodyTheme.farben
    val zustand = zustandsText ?: if (abgehakt) "abgehakt" else null
    val bedienung = modifier
        .heightIn(min = 92.dp)
        .clip(FoodyFormen.mittel)
        .combinedClickable(onLongClickLabel = onLangdruck?.let { "Details" }, onLongClick = onLangdruck, onClick = onKlick)
        .semantics { if (zustand != null) stateDescription = zustand }
    val inhalt = @Composable {
        Column(Modifier.padding(FoodyAbstaende.s3), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (symbol != null) {
                symbol()
            } else if (abgehakt) {
                Box(
                    Modifier.quadratisch(mindestens = 30.dp).clip(CircleShape).background(farben.oliv).semantics { hideFromAccessibility() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            } else {
                Box(
                    Modifier
                        .quadratisch(mindestens = 30.dp)
                        .clip(CircleShape)
                        .background(farben.primaerBehaelter)
                        // Kennung hinter dem Hintergrund: misst den gezeichneten Kreis, nicht das äußere Quadrat
                        .testTag(MONOGRAMM_TAG)
                        .semantics { hideFromAccessibility() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(1).uppercase(Locale.GERMAN),
                        style = MaterialTheme.typography.labelLarge,
                        color = farben.aufPrimaerBehaelter,
                    )
                }
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    aufDerListe -> farben.primaer
                    abgehakt -> farben.nebentext
                    else -> farben.text
                },
                textDecoration = if (abgehakt) TextDecoration.LineThrough else null,
            )
            Text(
                listOfNotNull(menge.takeIf { it.isNotEmpty() }, untertitel).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = farben.nebentext,
            )
        }
    }
    // Kacheln stehen in Listen: Unschärfe hinter jedem Eintrag kostet zu viel – nur Tönung
    GlasFlaeche(GlasStufe.DICHT, bedienung, form = FoodyFormen.mittel, unschaerfeErlaubt = false) {
        inhalt()
        if (aufDerListe) {
            Box(Modifier.matchParentSize().border(2.dp, farben.primaer, FoodyFormen.mittel))
            Box(
                Modifier.align(Alignment.TopEnd).padding(FoodyAbstaende.s2).size(20.dp).clip(CircleShape).background(farben.primaer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, contentDescription = null, tint = farben.aufPrimaer, modifier = Modifier.size(14.dp)) }
        }
    }
}

/** Katalog-Emoji als Kachelsymbol; für TalkBack verborgen, der Name sagt dasselbe. */
@Composable
fun KachelEmoji(emoji: String) {
    Text(emoji, fontSize = 28.sp, modifier = Modifier.semantics { hideFromAccessibility() })
}

/** Quadrat mit Kante = max(Inhalt, [mindestens]) – der Monogramm-Kreis wächst mit großer Schrift mit. */
private fun Modifier.quadratisch(mindestens: Dp): Modifier = layout { messbar, vorgabe ->
    // Kante aus der natürlichen Größe des Inhalts, dann fest messen: so füllen Clip und Hintergrund das ganze Quadrat.
    val breite = messbar.maxIntrinsicWidth(vorgabe.maxHeight)
    val hoehe = messbar.maxIntrinsicHeight(breite)
    val kante = maxOf(breite, hoehe, mindestens.roundToPx())
    val platziert = messbar.measure(Constraints.fixed(kante, kante))
    layout(kante, kante) { platziert.placeRelative(0, 0) }
}
