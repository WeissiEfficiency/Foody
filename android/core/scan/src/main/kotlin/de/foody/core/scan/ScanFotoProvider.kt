package de.foody.core.scan

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * FileProvider für das Kamerafoto der Nährwerttabelle (Authority `<applicationId>.scan`, Pfade `res/xml/scan_pfade`).
 * Eigene Klasse, weil das Manifest `androidx.core.content.FileProvider` schon für die Rezeptfotos einträgt.
 */
class ScanFotoProvider : FileProvider()

/** Ordner der Kamerafotos (`cache/scan/`); es gibt immer nur ein Foto zur Zeit. */
internal fun scanOrdner(context: Context): File = File(context.cacheDir, "scan").apply { mkdirs() }

/** Neue Datei für das nächste Kamerafoto; Reste früherer Versuche räumt [aufraeumen] weg. */
internal fun neuesScanFoto(context: Context): File {
    val ordner = scanOrdner(context)
    aufraeumen(ordner, behalten = null)
    return File(ordner, "packung-${UUID.randomUUID()}.jpg")
}

/** Content-Uri für die Kamera-App (über [ScanFotoProvider]). */
internal fun scanFotoUri(context: Context, datei: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.scan", datei)

/**
 * Löscht alte Kamerafotos (Prozess beendet, App abgestürzt, Bildschirm verlassen). [behalten]: das Foto einer noch
 * offenen Kamera, deren Ergebnis nach einem Neustart erst noch kommt.
 */
internal fun aufraeumen(ordner: File, behalten: String?) {
    ordner.listFiles()?.filter { it.path != behalten }?.forEach { it.delete() }
}
