package de.foody.feature.kochen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import java.io.File
import java.util.UUID

/** Cache-Unterordner und FileProvider der Kamerabilder – angelegt von `:feature:rezepte` (`rezeptfotos_pfade.xml`). */
private const val KAMERA_ORDNER = "kamera"

/**
 * „Guten Appetit!“ nach dem letzten Schritt: eigenes Foto (nur ohne Rezeptfoto), „Rezept anpassen“ und „Schließen“.
 * Wegwischen schließt ebenfalls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FertigBlatt(ohneFoto: Boolean, onFoto: (Uri, File) -> Unit, onAnpassen: () -> Unit, onSchliessen: () -> Unit) {
    val kontext = LocalContext.current
    var kameraDatei by rememberSaveable { mutableStateOf<String?>(null) }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = kameraDatei?.let(::File) ?: return@rememberLauncherForActivityResult
        kameraDatei = null
        if (ok) onFoto(Uri.fromFile(datei), datei) else datei.delete()
    }
    ModalBottomSheet(onDismissRequest = onSchliessen, containerColor = FoodyTheme.farben.grund) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Guten Appetit!", style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
            if (ohneFoto) {
                FoodyKnopf("Foto aufnehmen", {
                    val datei = File(File(kontext.cacheDir, KAMERA_ORDNER).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                    kameraDatei = datei.path
                    kamera.launch(FileProvider.getUriForFile(kontext, "${kontext.packageName}.rezeptfotos", datei))
                }, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
            }
            FoodyKnopf("War etwas anders? Rezept anpassen", onAnpassen, Modifier.fillMaxWidth(), art = KnopfArt.UMRISS)
            FoodyKnopf("Schließen", onSchliessen, Modifier.fillMaxWidth())
        }
    }
}
