package de.foody.app.scan

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.domain.NaehrwertScan
import de.foody.domain.OcrElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Fehlt ein Modul der Play-Dienste, wird es nachgeladen; `true`, wenn der Download angenommen wurde (bis dahin meldet
 * der Scanner „wird geladen“). Lehnen die Play-Dienste ab (offline, kein Speicher, …), `false` – sonst stünde dauerhaft
 * „wird vorbereitet“ da, obwohl nichts geladen wird.
 */
private suspend fun Context.modulLaden(api: OptionalModuleApi): Boolean = try {
    ModuleInstall.getClient(this).installModules(ModuleInstallRequest.newBuilder().addApi(api).build()).await()
    true
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}

@Singleton
class GooglePlayDienste @Inject constructor(@ApplicationContext private val context: Context) : PlayDienste {
    private var vorgeladen = false

    override fun verfuegbar(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /**
     * `deferredInstall`: Die Play-Dienste laden Code Scanner und Texterkennung, wenn es passt (unauffällig, ohne Dialog);
     * fehlen sie noch beim ersten Scan, greift weiterhin `modulLaden`. Einmal je Prozess genügt.
     */
    @Synchronized
    override fun vorladen() {
        if (vorgeladen) return
        vorgeladen = true
        val scanner = GmsBarcodeScanning.getClient(context)
        val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ModuleInstall.getClient(context).deferredInstall(scanner, text)
            .addOnCompleteListener { text.close() }
    }
}

/** Texterkennung auf dem Gerät (ML Kit über die Play-Dienste); das Bild verlässt das Gerät nicht. */
@Singleton
class MlKitTabellenScanner @Inject constructor(@ApplicationContext private val context: Context) : TabellenScanner {
    private val erkenner by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun scan(bildUri: String): ScanStatus = try {
        val text = erkenner.process(InputImage.fromFilePath(context, Uri.parse(bildUri))).await()
        val elemente = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { zeile -> zeile.boundingBox?.let { OcrElement(zeile.text, it.left, it.top, it.bottom, zeile.angle) } }
        }
        ScanStatus.Erkannt(NaehrwertScan.auswerten(elemente))
    } catch (e: CancellationException) {
        throw e
    } catch (e: MlKitException) {
        if (e.errorCode == MlKitException.UNAVAILABLE && context.modulLaden(erkenner)) {
            ScanStatus.WirdGeladen
        } else {
            ScanStatus.Fehler
        }
    } catch (_: Exception) {
        ScanStatus.Fehler
    }
}

/** Google Code Scanner: eigene Kameraansicht der Play-Dienste, keine Kamera-Berechtigung nötig. */
@Singleton
class GmsStrichcodeLeser @Inject constructor(@ApplicationContext private val context: Context) : StrichcodeLeser {
    override suspend fun lesen(): StrichcodeStatus {
        val optionen = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, optionen)
        val status = suspendCancellableCoroutine { cont ->
            scanner.startScan()
                .addOnSuccessListener { b ->
                    val code = b.rawValue?.filter(Char::isDigit).orEmpty()
                    cont.resume(if (code.isEmpty()) StrichcodeStatus.Fehler else StrichcodeStatus.Gelesen(code))
                }
                .addOnCanceledListener { cont.resume(StrichcodeStatus.Abgebrochen) }
                .addOnFailureListener { e ->
                    val fehlt = e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE
                    cont.resume(if (fehlt) StrichcodeStatus.WirdGeladen else StrichcodeStatus.Fehler)
                }
        }
        // Modul fehlt: nachladen anstoßen; klappt das nicht, ist es ein Fehler statt „wird vorbereitet“.
        if (status == StrichcodeStatus.WirdGeladen && !context.modulLaden(scanner)) return StrichcodeStatus.Fehler
        return status
    }
}
