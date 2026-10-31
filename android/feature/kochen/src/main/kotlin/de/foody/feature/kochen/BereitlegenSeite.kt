package de.foody.feature.kochen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.token.FoodyAbstaende

/** Seite „Bereitlegen“: alle Zutaten zum Abhaken, Portionen-Stepper; ändern skaliert alle Mengen. */
@Composable
internal fun BereitlegenSeite(z: KochZustand.Da, a: KochAktionen) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${z.abgehakt.size} von ${z.bereit.size} bereit", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text,
            )
            if (z.abgehakt.isNotEmpty()) FoodyKnopf("Alle zurücksetzen", a.onAlleZuruecksetzen, art = KnopfArt.TEXT)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { a.onPortionen(false) }) {
                Icon(Icons.Outlined.Remove, contentDescription = "Weniger Portionen", tint = FoodyTheme.farben.text)
            }
            Text(
                if (z.portionen == 1) "1 Portion" else "${z.portionen} Portionen",
                style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text,
            )
            IconButton(onClick = { a.onPortionen(true) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Mehr Portionen", tint = FoodyTheme.farben.text)
            }
        }
        z.bereit.forEach { zeile -> BereitZeileAnzeige(zeile, zeile.zeilenIndex in z.abgehakt) { a.onAbhaken(zeile.zeilenIndex) } }
    }
}

@Composable
private fun BereitZeileAnzeige(zeile: BereitZeile, bereit: Boolean, onUmschalten: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(bereit, role = Role.Checkbox, onValueChange = { onUmschalten() })
            .padding(vertical = FoodyAbstaende.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (bereit) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
            tint = if (bereit) FoodyTheme.farben.primaer else FoodyTheme.farben.nebentext,
        )
        Text(
            zeile.menge.orEmpty(), Modifier.padding(start = FoodyAbstaende.s3).width(88.dp),
            style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text,
        )
        Column(Modifier.weight(1f)) {
            Text(zeile.name + if (zeile.optional) " (optional)" else "", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            zeile.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
        }
    }
}
