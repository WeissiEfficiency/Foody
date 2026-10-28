package de.foody.core.scan

import android.content.Context
import android.net.Uri
import de.foody.core.data.fotos.KameraDatei
import java.io.File

/** Ordner der Kamerafotos (`cache/scan/`); es gibt immer nur ein Foto zur Zeit. */
internal fun scanOrdner(context: Context): File = KameraDatei.ordner(context, KameraDatei.SCAN)

/** Neue Datei für das nächste Kamerafoto; Reste früherer Versuche räumt [aufraeumen] beim Anzeigen des Knopfs weg. */
internal fun neuesScanFoto(context: Context): File = KameraDatei.neu(context, KameraDatei.SCAN, praefix = "packung-")

/** Content-Uri für die Kamera-App (über den FileProvider aus `:core:data`). */
internal fun scanFotoUri(context: Context, datei: File): Uri = KameraDatei.uri(context, datei)

/**
 * Löscht alte Kamerafotos (Prozess beendet, App abgestürzt, Bildschirm verlassen). [behalten]: das Foto einer noch
 * offenen Kamera, deren Ergebnis nach einem Neustart erst noch kommt.
 */
internal fun aufraeumen(ordner: File, behalten: String?) {
    ordner.listFiles()?.filter { it.path != behalten }?.forEach { it.delete() }
}
