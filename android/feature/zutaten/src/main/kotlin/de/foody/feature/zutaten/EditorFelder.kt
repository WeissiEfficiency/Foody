package de.foody.feature.zutaten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.foody.core.data.zutaten.ZutatEntwurf
import de.foody.core.data.zutaten.ZutatFeld
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.IngredientCatalog
import de.foody.domain.NutrientBasis

/** Ein Abschnitt des Editors: Glasfläche, optional mit Augenbraue. */
@Composable
private fun Bereich(titel: String?, inhalt: @Composable ColumnScope.() -> Unit) {
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth(), form = FoodyFormen.gross) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            titel?.let { Augenbraue(it) }
            inhalt()
        }
    }
}

@Composable
private fun Klein(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)

/**
 * Eingabefeld; [fehler] als Hinweis darunter. „Fertig“ auf der Tastatur oder Verlassen des Feldes meldet [onFertig] –
 * erst dann zeigt der Editor einen Fehler.
 */
@Composable
private fun Feld(
    beschriftung: String,
    wert: String,
    fehler: String?,
    onWert: (String) -> Unit,
    onFertig: () -> Unit,
    tastatur: KeyboardType = KeyboardType.Decimal,
) {
    var hatteFokus by remember { mutableStateOf(false) }
    OutlinedTextField(
        wert, onWert,
        Modifier.fillMaxWidth().onFocusChanged {
            if (it.isFocused) hatteFokus = true else if (hatteFokus) onFertig()
        },
        label = { Text(beschriftung) },
        singleLine = true,
        isError = fehler != null,
        supportingText = fehler?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = tastatur, imeAction = ImeAction.Done,
            capitalization = if (tastatur == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onDone = { onFertig() }),
    )
}

/** Alle Abschnitte des Editors; jede Eingabe geht als neuer Entwurf an [onAendern]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorFelder(
    e: ZutatEntwurf,
    fehler: Map<ZutatFeld, String>,
    synonyme: List<String>,
    verwendetIn: List<String>,
    onAendern: (ZutatEntwurf) -> Unit,
    onFeldFertig: (ZutatFeld) -> Unit,
) {
    @Composable
    fun zahl(beschriftung: String, feld: ZutatFeld, wert: String, neu: (String) -> ZutatEntwurf, tastatur: KeyboardType = KeyboardType.Decimal) =
        Feld(beschriftung, wert, fehler[feld], { onAendern(neu(it)) }, { onFeldFertig(feld) }, tastatur)

    Bereich(null) {
        Feld("Name", e.name, fehler[ZutatFeld.NAME], { onAendern(e.copy(name = it)) }, { onFeldFertig(ZutatFeld.NAME) }, KeyboardType.Text)
        if (synonyme.isNotEmpty()) Klein("Auch: " + synonyme.joinToString(", "))
    }
    Bereich("Kategorie") {
        val frei = e.kategorie?.takeIf { it !in IngredientCatalog.categories }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (IngredientCatalog.categories + listOfNotNull(frei)).forEach { k ->
                FoodyChip(k, e.kategorie == k, { onAendern(e.copy(kategorie = k)) })
            }
            FoodyChip("Keine", e.kategorie == null, { onAendern(e.copy(kategorie = null)) })
        }
    }
    Bereich("Nährwerte") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodyChip("je 100 g", e.basis != NutrientBasis.PER_100_ML, { onAendern(e.copy(basis = NutrientBasis.PER_100_G)) })
            FoodyChip("je 100 ml", e.basis == NutrientBasis.PER_100_ML, { onAendern(e.copy(basis = NutrientBasis.PER_100_ML)) })
        }
        zahl("kcal", ZutatFeld.KCAL, e.kcal, { e.copy(kcal = it) }, KeyboardType.Number)
        zahl("Eiweiß", ZutatFeld.EIWEISS, e.eiweiss, { e.copy(eiweiss = it) })
        zahl("Kohlenhydrate", ZutatFeld.KOHLENHYDRATE, e.kohlenhydrate, { e.copy(kohlenhydrate = it) })
        zahl("davon Zucker", ZutatFeld.ZUCKER, e.zucker, { e.copy(zucker = it) })
        zahl("Fett", ZutatFeld.FETT, e.fett, { e.copy(fett = it) })
        zahl("Ballaststoffe", ZutatFeld.BALLASTSTOFFE, e.ballaststoffe, { e.copy(ballaststoffe = it) })
        zahl("Salz", ZutatFeld.SALZ, e.salz, { e.copy(salz = it) })
        Feld("Quelle", e.quelle, null, { onAendern(e.copy(quelle = it)) }, {}, KeyboardType.Text)
    }
    Bereich("Umrechnung") {
        zahl("Dichte (g/ml)", ZutatFeld.DICHTE, e.dichte, { e.copy(dichte = it) })
        zahl("Stückgewicht (g)", ZutatFeld.STUECKGEWICHT, e.stueckgewicht, { e.copy(stueckgewicht = it) })
    }
    Bereich("Strichcode") {
        zahl("Strichcode", ZutatFeld.STRICHCODE, e.strichcode, { e.copy(strichcode = it) }, KeyboardType.Number)
    }
    Klein(verwendetInText(verwendetIn))
}
