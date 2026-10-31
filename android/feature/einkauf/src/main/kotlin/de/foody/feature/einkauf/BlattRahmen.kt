package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Blatt des Einkaufs: ganz geöffnet, Inhalt scrollbar, [knopf] fest unten (ohne Ziehen erreichbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlattRahmen(titel: String, onSchliessen: () -> Unit, knopf: (@Composable () -> Unit)? = null, inhalt: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FoodyTheme.farben.grund,
    ) {
        Column(Modifier.padding(horizontal = FoodyAbstaende.s6).padding(bottom = FoodyAbstaende.s6).navigationBarsPadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
            ) {
                Text(titel, style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
                inhalt()
            }
            knopf?.let { Column(Modifier.fillMaxWidth().padding(top = FoodyAbstaende.s4)) { it() } }
        }
    }
}

/** „Liste löschen?“ – vor dem Löschen einer ganzen Liste samt Artikeln. */
@Composable
internal fun LoeschenFrage(name: String, onLoeschen: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage("Liste löschen?", "„$name“ mit allen Artikeln löschen?", "Löschen", onLoeschen, onAbbrechen)
