package de.foody.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.foody.app.R
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit

@StringRes
fun Mahlzeit.label(): Int = when (this) {
    Mahlzeit.FRUEHSTUECK -> R.string.slot_breakfast
    Mahlzeit.MITTAGESSEN -> R.string.slot_lunch
    Mahlzeit.ABENDESSEN -> R.string.slot_dinner
    Mahlzeit.SNACK -> R.string.slot_snack
}

@StringRes
fun Gang.label(): Int = when (this) {
    Gang.VORSPEISE -> R.string.gang_vorspeise
    Gang.HAUPTSPEISE -> R.string.gang_hauptspeise
    Gang.NACHSPEISE -> R.string.gang_nachspeise
    Gang.BROTZEIT -> R.string.gang_brotzeit
}

/** Anzeigename eines Plan-Eintrags: feste Mahlzeit übersetzt, alter Freitext („Sonstiges“) unverändert. */
@Composable
fun slotLabel(slotType: String): String = Mahlzeit.ausText(slotType)?.let { stringResource(it.label()) } ?: slotType

/**
 * Eine Reihe Mehrfachauswahl-Chips für eine Dimension der Einordnung. Vermutete Werte erscheinen abgeschwächt mit
 * Hinweis; [onZuruecksetzen] (nur bei Festlegung) kehrt zur Vermutung zurück.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <E> EinordnungChips(
    titel: String,
    werte: List<E>,
    gewaehlt: Set<E>,
    vermutet: Boolean,
    label: (E) -> Int,
    onToggle: (E) -> Unit,
    onZuruecksetzen: (() -> Unit)?,
) {
    // Wie die übrigen Filter-Chips: ausgewählt = dunkel, sonst sind Zustände kaum zu unterscheiden
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (vermutet) {
                Text(stringResource(R.string.einordnung_vermutet), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            onZuruecksetzen?.let { TextButton(it) { Text(stringResource(R.string.einordnung_zuruecksetzen)) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = if (vermutet) Modifier.alpha(0.6f) else Modifier) {
            werte.forEach { w -> FilterChip(w in gewaehlt, { onToggle(w) }, label = { Text(stringResource(label(w))) }, shape = RoundedCornerShape(50), colors = chipColors) }
        }
    }
}
