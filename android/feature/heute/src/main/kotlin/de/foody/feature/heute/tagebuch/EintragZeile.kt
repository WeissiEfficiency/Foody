package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte

/** Mahlzeit eines Eintrags; alte Freitext-Mahlzeiten („Sonstiges“) zählen als Snack. */
internal fun TagebuchEintragEntity.eintragMahlzeit(): Mahlzeit = Mahlzeit.ausText(mahlzeit) ?: Mahlzeit.SNACK

internal fun TagebuchEintragEntity.werte() = Naehrwerte(energieKj, eiweiss, kohlenhydrate, fett, vollstaendig)

/** Höchstlänge eines Namens (wie der Server). */
internal const val MAX_NAME = 200

/** Ein Eintrag mit kcal und den Aktionen Ändern und Löschen. */
@Composable
internal fun EintragZeile(e: TagebuchEintragEntity, onAendern: () -> Unit, onLoeschen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        Spacer(Modifier.width(FoodyAbstaende.s2))
        Text(kcalText(e.werte().kcal, e.vollstaendig), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
        IconButton(onClick = onAendern) {
            Icon(Icons.Outlined.Edit, contentDescription = "${e.name} ändern", tint = FoodyTheme.farben.text)
        }
        IconButton(onClick = onLoeschen) {
            Icon(Icons.Outlined.Delete, contentDescription = "${e.name} löschen", tint = FoodyTheme.farben.text)
        }
    }
}
