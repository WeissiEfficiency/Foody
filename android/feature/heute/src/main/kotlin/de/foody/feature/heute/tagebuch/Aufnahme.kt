package de.foody.feature.heute.tagebuch

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.fotos.KameraDatei
import de.foody.core.scan.ScanAusgang
import de.foody.core.scan.aufraeumen
import de.foody.core.scan.neuesScanFoto
import de.foody.core.scan.scanFotoUri
import de.foody.core.scan.scanOrdner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wege, etwas zu erfassen: Mahlzeitfoto aus Kamera oder Galerie, Strichcode, Foto der Nährwerttabelle aus Kamera oder
 * Galerie. Startet sofort, ohne Menü.
 */
internal class Aufnahme(
    val foto: () -> Unit,
    val galerie: () -> Unit,
    val scannen: () -> Unit,
    val tabelle: () -> Unit,
    val tabelleGalerie: () -> Unit,
)

/**
 * Die Launcher für Kamera und Galerie der Seite Eintragen. Sie hängen an der Seite, nicht an einem Knopf: Beim
 * Sofort-Start zeigt die Seite nichts, das Kameraergebnis muss trotzdem ankommen (auch nach Drehen und Prozessende).
 * Ein Mahlzeitfoto führt nach Bestätigen ([EintragenViewModel.fotoGemacht]); abgebrochen bleibt der Nutzer, wo er war,
 * beim Sofort-Start geht die Seite zu ([EintragenViewModel.aufnahmeAbgebrochen]). Das Foto der Nährwerttabelle erkennt
 * [EintragenViewModel.scanLauf]; ein Strichcode, den der Scan-Knopf sich gemerkt hat, hängt sich an.
 *
 * Beim ersten Aufbau räumt sie Reste früherer Kamerafotos weg und lädt die Scan-Module vor; erst danach öffnet sie die
 * Kamera, wenn der Sofort-Start sie angefragt hat (sonst löschte das Aufräumen das neue Foto).
 */
@Composable
internal fun rememberAufnahme(vm: EintragenViewModel): Aufnahme {
    val context = LocalContext.current
    val fotoLauf = vm.fotoLauf
    val scanLauf = vm.scanLauf
    val fotoZustand by fotoLauf.zustand.collectAsStateWithLifecycle()

    // Beim Aufbau gelesen: Nach Drehen oder Prozessende liefert der Launcher das Kameraergebnis schon vor den Effekten
    // und vergisst dabei den Pfad – das Aufräumen darf das Foto, das dann gerade ankommt, nicht löschen.
    val mahlzeitBehalten = remember { setOfNotNull(fotoLauf.zustand.value.fotoPfad, vm.fotoDatei) }
    val tabelleBehalten = remember { scanLauf.zustand.fotoPfad }
    var aufgeraeumt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scanLauf.vorladen()
        withContext(Dispatchers.IO) {
            KameraDatei.ordner(context, KameraDatei.MAHLZEIT).listFiles()?.filter { it.path !in mahlzeitBehalten }?.forEach { it.delete() }
            aufraeumen(scanOrdner(context), behalten = tabelleBehalten)
        }
        aufgeraeumt = true
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = fotoLauf.kameraZurueck()?.let(::File)
        if (ok && datei != null) {
            vm.fotoGemacht(Uri.fromFile(datei).toString(), datei.path)
        } else {
            datei?.delete()
            vm.aufnahmeAbgebrochen()
        }
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.fotoGemacht(uri.toString(), null) else vm.aufnahmeAbgebrochen()
    }
    val tabelleKamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val datei = scanLauf.zustand.kameraZurueck()?.let(::File)
        when {
            datei == null -> Unit
            !ok -> datei.delete()
            else -> scanLauf.foto(Uri.fromFile(datei).toString()) { datei.delete() }
        }
    }
    val tabelleGalerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanLauf.foto(uri.toString())
    }

    val aufnahme = remember(vm, kamera, galerie, tabelleKamera, tabelleGalerie) {
        val bild = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        Aufnahme(
            foto = {
                val datei = KameraDatei.neu(context, KameraDatei.MAHLZEIT, praefix = "mahlzeit-")
                fotoLauf.kameraGestartet(datei.path)
                try {
                    kamera.launch(KameraDatei.uri(context, datei))
                } catch (_: ActivityNotFoundException) {
                    // Keine Kamera-App: wie abgebrochen
                    fotoLauf.kameraZurueck()
                    vm.aufnahmeAbgebrochen()
                }
            },
            galerie = { galerie.launch(bild) },
            scannen = scanLauf::strichcode,
            tabelle = {
                val datei = neuesScanFoto(context)
                scanLauf.zustand.kameraGestartet(datei.path)
                try {
                    tabelleKamera.launch(scanFotoUri(context, datei))
                } catch (_: ActivityNotFoundException) {
                    // Keine Kamera-App: nichts zu löschen, nur melden
                    scanLauf.zustand.kameraZurueck()
                    scanLauf.zustand.auswerten(ScanAusgang.Fehler)
                }
            },
            tabelleGalerie = { tabelleGalerie.launch(bild) },
        )
    }

    // Sofort-Start mit Foto: erst nach dem Aufräumen, einmal je Anfrage (die Anfrage übersteht Drehen und Prozessende)
    LaunchedEffect(aufgeraeumt, fotoZustand.kameraAngefragt, fotoZustand.laeuft) {
        if (aufgeraeumt && fotoZustand.kameraAngefragt && !fotoZustand.laeuft) {
            fotoLauf.kameraAnfrageErledigt()
            aufnahme.foto()
        }
    }
    return aufnahme
}
