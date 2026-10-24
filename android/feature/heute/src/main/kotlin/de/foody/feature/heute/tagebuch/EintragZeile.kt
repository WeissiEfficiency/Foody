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
import androidx.compose.ui.unit.dp
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte
import java.math.BigDecimal

/** Mahlzeit eines Eintrags; alte Freitext-Mahlzeiten („Sonstiges“) zählen als Snack. */
internal fun TagebuchEintragEntity.eintragMahlzeit(): Mahlzeit = Mahlzeit.ausText(mahlzeit) ?: Mahlzeit.SNACK

internal fun TagebuchEintragEntity.werte() = Naehrwerte(energieKj, eiweiss, kohlenhydrate, fett, vollstaendig)

/** „717 kcal“, unvollständig „≥ 717 kcal“, ohne Werte „– kcal“. */
internal fun kcalText(kcal: Int?, vollstaendig: Boolean): String = when {
    kcal == null -> "– kcal"
    !vollstaendig -> "≥ ${ganzzahlDeutsch(kcal)} kcal"
    else -> "${ganzzahlDeutsch(kcal)} kcal"
}

/** Zahl aus einem Eingabefeld („1,5“ oder „1.5“); leer oder ungültig → `null`. */
internal fun zahl(text: String): BigDecimal? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/** Zahl für ein Eingabefeld: ohne Nullen am Ende, mit Komma. */
internal fun zahlText(wert: BigDecimal?): String = wert?.stripTrailingZeros()?.toPlainString()?.replace('.', ',').orEmpty()

/** Ein Eintrag mit kcal und den Aktionen Ändern und Löschen. */
@Composable
internal fun EintragZeile(e: TagebuchEintragEntity, onAendern: () -> Unit, onLoeschen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        Spacer(Modifier.width(8.dp))
        Text(kcalText(e.werte().kcal, e.vollstaendig), style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
        IconButton(onClick = onAendern) {
            Icon(Icons.Outlined.Edit, contentDescription = "${e.name} ändern", tint = FoodyTheme.farben.text)
        }
        IconButton(onClick = onLoeschen) {
            Icon(Icons.Outlined.Delete, contentDescription = "${e.name} löschen", tint = FoodyTheme.farben.text)
        }
    }
}
