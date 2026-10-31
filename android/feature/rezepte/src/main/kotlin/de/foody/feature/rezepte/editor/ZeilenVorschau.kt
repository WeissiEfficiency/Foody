package de.foody.feature.rezepte.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.ImportedIngredient
import de.foody.domain.MeasureUnit

/** Was je Zeile erkannt wurde: „200 g · Reis · gesiebt · im Katalog“; Fehler in Fehlerfarbe. */
@Composable
internal fun ZeilenVorschau(zeilen: List<ZeilenStatus>) {
    if (zeilen.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
        zeilen.forEach { s ->
            val fehler = s.fehler
            val erkannt = s.erkannt
            when {
                fehler != null -> Text("${s.zeile}: $fehler", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                erkannt != null -> Text(
                    beschreibung(erkannt) + if (s.imKatalog) " · im Katalog" else " · neu",
                    style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext,
                )
            }
        }
    }
}

private fun beschreibung(z: ImportedIngredient): String {
    val menge = z.amount?.let { m ->
        val zahl = m.stripTrailingZeros().toPlainString().replace('.', ',')
        if (z.unit == null || z.unit == MeasureUnit.PIECE) zahl else "$zahl ${z.unit?.symbol}"
    }
    return listOfNotNull(menge, z.name, z.note, "optional".takeIf { z.optional }).joinToString(" · ")
}
