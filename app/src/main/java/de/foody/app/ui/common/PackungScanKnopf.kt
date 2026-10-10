package de.foody.app.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.foody.app.R
import de.foody.app.data.db.IngredientEntity
import de.foody.app.scan.Packung
import de.foody.app.scan.PackungScan
import de.foody.app.scan.ScanAusgang
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * „Von Packung scannen“: Strichcode (eigener Katalog, dann Open Food Facts) oder Foto der Nährwerttabelle.
 * Liefert nur Vorschläge über [onPackung]; das Formular markiert sie, Speichern übernimmt. Kamerafotos liegen
 * kurz in `cache/scan/` und werden nach der Erkennung gelöscht.
 */
@Composable
fun PackungScanKnopf(
    scan: PackungScan,
    onPackung: (Packung) -> Unit,
    onImKatalog: (IngredientEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verfuegbar = remember { scan.verfuegbar }
    // Sobald der Knopf zu sehen ist (Zutaten-Dialog, Reiter „Frei“): Module im Hintergrund vorladen lassen
    LaunchedEffect(Unit) { scan.vorladen() }
    var menu by remember { mutableStateOf(false) }
    var laeuft by remember { mutableStateOf(false) }
    var meldung by rememberSaveable { mutableStateOf<Int?>(null) }
    var fotoAnbieten by rememberSaveable { mutableStateOf(false) }
    // Strichcode aus einem vorherigen Versuch: wird an die Foto-Werte gehängt und beim Speichern gemerkt
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var fotoPfad by rememberSaveable { mutableStateOf<String?>(null) }

    fun auswerten(a: ScanAusgang) {
        laeuft = false
        meldung = null
        when (a) {
            is ScanAusgang.Gefunden -> { fotoAnbieten = false; code = null; onPackung(a.packung) }
            is ScanAusgang.ImKatalog -> { fotoAnbieten = false; code = null; onImKatalog(a.zutat) }
            is ScanAusgang.NichtGefunden -> {
                code = a.strichcode
                meldung = when (a.grund) {
                    ScanAusgang.Grund.OFFLINE -> R.string.scan_offline
                    ScanAusgang.Grund.ONLINE_AUS -> R.string.scan_online_aus
                    ScanAusgang.Grund.UNBEKANNT -> R.string.scan_nicht_gefunden
                }
                fotoAnbieten = true
            }
            is ScanAusgang.KeineTabelle -> { meldung = R.string.scan_keine_tabelle; fotoAnbieten = true }
            ScanAusgang.WirdGeladen -> meldung = R.string.scan_wird_geladen
            ScanAusgang.NichtVerfuegbar -> meldung = R.string.scan_nicht_verfuegbar
            ScanAusgang.Fehler -> meldung = R.string.scan_fehler
            ScanAusgang.Abgebrochen -> Unit
        }
    }

    fun foto(uri: String, danach: () -> Unit = {}) {
        laeuft = true
        // finally: auch wenn der Bildschirm während der Erkennung verlassen wird, ist die Kameradatei danach weg
        scope.launch { try { auswerten(scan.foto(uri, code)) } finally { danach() } }
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = fotoPfad?.let(::File)
        fotoPfad = null
        when {
            datei == null -> Unit
            !ok -> datei.delete()
            else -> foto(Uri.fromFile(datei).toString()) { datei.delete() }
        }
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) foto(uri.toString())
    }

    fun kameraStarten() {
        val dir = File(context.cacheDir, "scan").apply { mkdirs() }
        // Reste früherer Versuche (Prozess beendet, App abgestürzt) wegräumen: es gibt immer nur ein Foto zur Zeit.
        dir.listFiles()?.forEach { it.delete() }
        val datei = File(dir, "packung-${UUID.randomUUID()}.jpg")
        fotoPfad = datei.path
        kamera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei))
    }
    fun galerieStarten() = galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            OutlinedButton({ menu = true }, enabled = verfuegbar && !laeuft, shape = RoundedCornerShape(50)) {
                if (laeuft) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(18.dp))
                Text(stringResource(R.string.scan_knopf), Modifier.padding(start = 8.dp))
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.scan_strichcode)) }, {
                    menu = false
                    laeuft = true
                    code = null
                    scope.launch { auswerten(scan.strichcode()) }
                })
                // Ein neuer Versuch über das Menü gehört zu keinem früheren Strichcode (vielleicht eine andere Packung);
                // nur die angebotenen Foto-Knöpfe nach „nicht gefunden“ hängen den gelesenen Code an.
                DropdownMenuItem({ Text(stringResource(R.string.scan_foto)) }, { menu = false; code = null; fotoAnbieten = false; kameraStarten() })
                DropdownMenuItem({ Text(stringResource(R.string.scan_galerie)) }, { menu = false; code = null; fotoAnbieten = false; galerieStarten() })
            }
        }
        if (!verfuegbar) {
            Text(stringResource(R.string.scan_nicht_verfuegbar), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        meldung?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (fotoAnbieten) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(::kameraStarten) { Text(stringResource(R.string.scan_foto)) }
                TextButton(::galerieStarten) { Text(stringResource(R.string.scan_galerie)) }
            }
        }
    }
}
