package de.foody.feature.rezepte.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.feature.rezepte.rezeptBild
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Unterordner im Cache für Kamerabilder (siehe `res/xml/rezeptfotos_pfade.xml`). */
private const val KAMERA_ORDNER = "kamera"

/**
 * Foto des Rezepts mit „Foto wählen“ (System-Photo-Picker), „Kamera“ und „Entfernen“. Die Kamera schreibt über den
 * FileProvider in den Cache; das Bild wird verkleinert in die Fotoablage übernommen und die Kameradatei gelöscht.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FotoWahl(bild: String?, vm: EditorViewModel) {
    val kontext = LocalContext.current
    val bereich = rememberCoroutineScope()
    var kameraDatei by rememberSaveable { mutableStateOf<String?>(null) }
    val auswahl = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { bereich.launch { vm.fotoUebernehmen(it) } }
    }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = kameraDatei?.let(::File) ?: return@rememberLauncherForActivityResult
        kameraDatei = null
        bereich.launch {
            if (ok) vm.fotoUebernehmen(Uri.fromFile(datei))
            datei.delete()
        }
    }
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) {
        rezeptBild(bild)?.invoke() ?: RezeptPlatzhalter(Modifier.fillMaxSize())
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodyKnopf("Foto wählen", {
            auswahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, art = KnopfArt.UMRISS)
        FoodyKnopf("Kamera", {
            val datei = File(File(kontext.cacheDir, KAMERA_ORDNER).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
            kameraDatei = datei.path
            kamera.launch(FileProvider.getUriForFile(kontext, "${kontext.packageName}.rezeptfotos", datei))
        }, art = KnopfArt.UMRISS)
        if (bild != null) FoodyKnopf("Foto entfernen", vm::fotoEntfernen, art = KnopfArt.TEXT)
    }
}
