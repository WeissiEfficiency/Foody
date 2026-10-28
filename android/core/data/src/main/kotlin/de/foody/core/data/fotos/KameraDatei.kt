package de.foody.core.data.fotos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Einziger FileProvider der App (Authority `<applicationId>.kamera`, Pfade `res/xml/kamera_pfade`): Die System-Kamera
 * (`TakePicture`) schreibt in eine Datei im Cache, die Foody danach übernimmt und löscht. Gilt für Rezeptfotos (Editor,
 * „Fertig“ im Kochmodus) und Packungsfotos (`:core:scan`). Eigene Unterklasse, damit kein anderes Modul mit einem
 * zweiten Eintrag für `androidx.core.content.FileProvider` kollidiert.
 */
class KameraFotoProvider : FileProvider()

/** Dateien und Content-Uris für Kamerafotos (über [KameraFotoProvider]). */
object KameraDatei {
    /** Rezeptfotos aus der Kamera; Foody übernimmt sie verkleinert in die Fotoablage. */
    const val REZEPTFOTOS = "kamera"

    /** Fotos der Nährwerttabelle; nach der Erkennung gelöscht. */
    const val SCAN = "scan"

    /** Unterordner [name] im Cache (angelegt). */
    fun ordner(context: Context, name: String): File = File(context.cacheDir, name).apply { mkdirs() }

    /** Neue Datei für das nächste Foto in [ordner]. */
    fun neu(context: Context, ordner: String, praefix: String = ""): File =
        File(ordner(context, ordner), "$praefix${UUID.randomUUID()}.jpg")

    /** Content-Uri, die die Kamera-App befristet beschreiben darf. */
    fun uri(context: Context, datei: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.kamera", datei)
}
