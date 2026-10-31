package de.foody.feature.rezepte.importieren

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.token.FoodyAbstaende

/** Dateitypen für die Dateiauswahl: Markdown kommt je nach App als Text oder ohne Typ. */
private val TYPEN = arrayOf("text/*", "application/octet-stream")

/**
 * Markdown-Import als Blatt: „Dateien wählen“ (mehrere) oder „Ordner wählen“ über das Android-Dateisystem (auch die
 * OpenCloud-App), Fortschritt, danach die Zusammenfassung. „Portionen nachtragen“ liest einen Ordner nur für
 * [de.foody.core.data.rezeptimport.PortionenNachtrag]. Während des Imports schließt Wischen das Blatt nicht ab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBlatt(onSchliessen: () -> Unit, vm: ImportViewModel = hiltViewModel()) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val dateien = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.dateien(uris)
    }
    val ordner = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { baum -> baum?.let(vm::ordner) }
    val nachtragOrdner = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { baum -> baum?.let(vm::portionen) }
    val schliessen = {
        vm.zuruecksetzen()
        onSchliessen()
    }
    ModalBottomSheet(onDismissRequest = schliessen, containerColor = FoodyTheme.farben.grund) {
        Column(
            Modifier.padding(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, bottom = FoodyAbstaende.s6),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            Text("Markdown importieren", style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            when (val s = z) {
                ImportZustand.Bereit -> {
                    Text(
                        "Rezepte mit schon vorhandener Quelle werden übersprungen. Dateien bekommen den Tag „Import“, ein Ordner „Ideen“.",
                        style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
                    )
                    FoodyKnopf("Dateien wählen", { dateien.launch(TYPEN) }, Modifier.fillMaxWidth())
                    FoodyKnopf("Ordner wählen", { ordner.launch(null) }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
                    Text(
                        "Früher importierte Rezepte mit geschätzten Portionen bekommen die Portionenzahl aus ihrer Datei; Mengen bleiben.",
                        style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext,
                    )
                    FoodyKnopf("Portionen nachtragen", { nachtragOrdner.launch(null) }, Modifier.fillMaxWidth(), art = KnopfArt.TEXT)
                }
                is ImportZustand.Laeuft -> Text(
                    if (s.gesamt == 0) "Dateien werden gelesen …" else "Importiere ${s.fertig} von ${s.gesamt} …",
                    style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text,
                )
                is ImportZustand.Fertig -> {
                    Text(zusammenfassung(s.ergebnis), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    FoodyKnopf("Fertig", schliessen, Modifier.fillMaxWidth())
                }
                is ImportZustand.NachtragFertig -> {
                    Text(nachtragText(s.ergebnis), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    FoodyKnopf("Fertig", schliessen, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
