package de.foody.core.scan

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * „Packung scannen“: Strichcode (eigener Katalog, dann Open Food Facts) oder Foto der Nährwerttabelle (Kamera oder
 * Galerie). Liefert nur Vorschläge über [onPackung]; das Formular markiert sie, Speichern übernimmt. Trägt schon eine
 * eigene Zutat den Strichcode, kommt [onImKatalog].
 */
@Composable
fun PackungScanKnopf(
    scan: PackungScan,
    onPackung: (Packungswerte) -> Unit,
    onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
    modifier: Modifier = Modifier,
) {
    PackungScanKnopf(
        verfuegbar = remember(scan) { scan.verfuegbar },
        vorladen = scan::vorladen,
        strichcode = scan::strichcode,
        foto = scan::foto,
        onPackung = onPackung,
        onImKatalog = onImKatalog,
        modifier = modifier,
    )
}

/** Wie oben, mit Funktionen statt [PackungScan] (für Tests ohne Play-Dienste). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PackungScanKnopf(
    verfuegbar: Boolean,
    vorladen: () -> Unit,
    strichcode: suspend () -> ScanAusgang,
    foto: suspend (bildUri: String, strichcode: String?) -> ScanAusgang,
    onPackung: (Packungswerte) -> Unit,
    onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zustand = rememberSaveable(saver = ScanKnopfZustand.Saver) { ScanKnopfZustand() }
    val packungWeiter by rememberUpdatedState(onPackung)
    val katalogWeiter by rememberUpdatedState(onImKatalog)
    var menue by remember { mutableStateOf(false) }

    // Beim Aufbau gelesen: Nach Drehen oder Prozessende liefert der Launcher das Kameraergebnis schon vor den Effekten
    // und vergisst dabei den Pfad – das Aufräumen darf das Foto, das dann gerade erkannt wird, nicht löschen.
    val offenesFoto = remember { zustand.fotoPfad }

    // Sobald der Knopf zu sehen ist: Module vorladen und Reste früherer Kamerafotos wegräumen
    LaunchedEffect(Unit) {
        vorladen()
        withContext(Dispatchers.IO) { aufraeumen(scanOrdner(context), behalten = offenesFoto) }
    }

    fun weitergeben(e: ScanKnopfZustand.Ergebnis?) = when (e) {
        is ScanKnopfZustand.Ergebnis.Packung -> packungWeiter(e.werte)
        is ScanKnopfZustand.Ergebnis.ImKatalog -> katalogWeiter(e.treffer)
        null -> Unit
    }

    fun fotoErkennen(uri: String, danach: () -> Unit = {}) {
        zustand.starten()
        val code = zustand.strichcode
        // finally: auch wenn der Bildschirm während der Erkennung verlassen wird, ist die Kameradatei danach weg
        scope.launch {
            try {
                weitergeben(zustand.auswerten(foto(uri, code)))
            } finally {
                danach()
            }
        }
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = zustand.kameraZurueck()?.let(::File)
        when {
            datei == null -> Unit
            !ok -> datei.delete()
            else -> fotoErkennen(Uri.fromFile(datei).toString()) { datei.delete() }
        }
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) fotoErkennen(uri.toString())
    }

    fun kameraStarten() {
        val datei = neuesScanFoto(context)
        zustand.kameraGestartet(datei.path)
        try {
            kamera.launch(scanFotoUri(context, datei))
        } catch (_: ActivityNotFoundException) {
            // Keine Kamera-App: nichts zu löschen, nur melden
            zustand.kameraZurueck()
            zustand.auswerten(ScanAusgang.Fehler)
        }
    }

    fun galerieStarten() = galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            ScanMenueKnopf(aktiv = verfuegbar && !zustand.laeuft, laeuft = zustand.laeuft) { menue = true }
            DropdownMenu(menue, onDismissRequest = { menue = false }) {
                DropdownMenuItem({ Text(ScanTexte.STRICHCODE) }, {
                    menue = false
                    zustand.menueVersuch()
                    zustand.starten()
                    scope.launch { weitergeben(zustand.auswerten(strichcode())) }
                })
                // Ein neuer Versuch über das Menü vergisst den Strichcode; nur die angebotenen Foto-Knöpfe hängen ihn an.
                DropdownMenuItem({ Text(ScanTexte.FOTO) }, {
                    menue = false
                    zustand.menueVersuch()
                    kameraStarten()
                })
                DropdownMenuItem({ Text(ScanTexte.GALERIE) }, {
                    menue = false
                    zustand.menueVersuch()
                    galerieStarten()
                })
            }
        }
        val hinweis = if (!verfuegbar) ScanTexte.NICHT_VERFUEGBAR else zustand.meldung
        hinweis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext) }
        if (zustand.fotoAnbieten && !zustand.laeuft) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodyKnopf(ScanTexte.FOTO, ::kameraStarten, art = KnopfArt.TEXT)
                FoodyKnopf(ScanTexte.GALERIE, ::galerieStarten, art = KnopfArt.TEXT)
            }
        }
    }
}

/** Umriss-Knopf „Packung scannen“ mit Symbol; während der Erkennung ein Fortschritt statt des Symbols. */
@Composable
private fun ScanMenueKnopf(aktiv: Boolean, laeuft: Boolean, onKlick: () -> Unit) {
    val farben = FoodyTheme.farben
    OutlinedButton(
        onClick = onKlick,
        modifier = Modifier.heightIn(min = 48.dp),
        enabled = aktiv,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (aktiv) farben.primaer else farben.nebentext.copy(alpha = 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = farben.primaer),
    ) {
        if (laeuft) {
            CircularProgressIndicator(Modifier.size(18.dp), color = farben.primaer, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(ScanTexte.KNOPF, Modifier.padding(start = 8.dp))
    }
}
