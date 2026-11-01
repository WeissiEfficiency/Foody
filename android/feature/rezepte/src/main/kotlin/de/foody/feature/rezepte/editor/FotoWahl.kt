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
import de.foody.core.data.fotos.KameraDatei
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.token.FoodyAbstaende
import kotlinx.coroutines.launch
import java.io.File

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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        FoodyKnopf("Foto wählen", {
            auswahl.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, art = KnopfArt.UMRISS)
        FoodyKnopf("Kamera", {
            val datei = KameraDatei.neu(kontext, KameraDatei.REZEPTFOTOS)
            kameraDatei = datei.path
            kamera.launch(KameraDatei.uri(kontext, datei))
        }, art = KnopfArt.UMRISS)
        if (bild != null) FoodyKnopf("Foto entfernen", vm::fotoEntfernen, art = KnopfArt.TEXT)
    }
}
