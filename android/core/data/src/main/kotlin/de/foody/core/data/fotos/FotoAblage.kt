package de.foody.core.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import de.foody.core.data.Einstellungsschluessel
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Eigene Rezeptfotos im privaten App-Speicher (`filesDir/recipe_images`, derselbe Ordner wie in der v1).
 *
 * Am Rezept steht ein `file:`-Link auf die Datei, in derselben Form wie die v1 ihn speichert (`Uri.fromFile`), damit
 * Rezepte nach dem Update ihre Fotos behalten. Gespeicherte Fotos sind höchstens [MAX_KANTE] Pixel an der längeren
 * Seite und [MAX_BYTES] groß (JPEG [JPEG_QUALITAET]); die Drehung aus den EXIF-Daten wird beim Verkleinern angewendet.
 */
class FotoAblage(context: Context) {
    private val context = context.applicationContext ?: context

    private val ordner: File get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER).apply { mkdirs() }

    /**
     * Kopiert das Bild hinter [quelle] (`content:` aus Kamera/Fotoauswahl oder `file:`) als neue Datei in den
     * Fotoordner, verkleinert es bei Bedarf und gibt den Link zurück, der am Rezept gespeichert wird. Kleine Fotos
     * werden unverändert kopiert. Die Quelle bleibt unangetastet.
     *
     * @throws IllegalArgumentException wenn [quelle] kein lesbares Bild ist – dann bleibt nichts im Fotoordner zurück.
     */
    suspend fun speichern(quelle: Uri): String = withContext(Dispatchers.IO) {
        val ziel = File(ordner, "${UUID.randomUUID()}.jpg")
        val tmp = File(ordner, ziel.name + TMP)
        try {
            val eingabe = context.contentResolver.openInputStream(quelle)
                ?: throw IllegalArgumentException("Foto nicht lesbar")
            eingabe.use { ein -> tmp.outputStream().use { ein.copyTo(it) } }
            ersetzen(tmp, ziel)
            require(laengereSeite(ziel) > 0) { "Kein Bild" }
            verkleinern(ziel)
            link(ziel)
        } catch (e: Exception) {
            tmp.delete()
            ziel.delete()
            throw e
        }
    }

    /**
     * Beim Start: alle eigenen Fotos prüfen und zu große verkleinern – aber nur einmal je [VERKLEINERUNG_VERSION]
     * (Merker [Einstellungsschluessel.FOTOS_VERKLEINERT] in den Prefs `foody`, wie in der v1). Neue Fotos verkleinert
     * schon [speichern]; ein Durchlauf bei jedem Start läse sonst jedes Mal alle Dateien. Gibt die Zahl verkleinerter
     * Fotos zurück.
     */
    suspend fun verkleinereAlleEinmal(): Int {
        val prefs = context.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(Einstellungsschluessel.FOTOS_VERKLEINERT, 0) >= VERKLEINERUNG_VERSION) return 0
        val anzahl = withContext(Dispatchers.IO) {
            ordner.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }.orEmpty().count { verkleinern(it) }
        }
        prefs.edit { putInt(Einstellungsschluessel.FOTOS_VERKLEINERT, VERKLEINERUNG_VERSION) }
        return anzahl
    }

    /** Ist [link] ein eigenes Foto (`file:`-Link auf eine Datei im Fotoordner)? Die Datei muss nicht existieren. */
    fun istEigeneDatei(link: String): Boolean = datei(link) != null

    /**
     * Die Datei hinter einem eigenen Foto-Link, sonst `null` – für fremde Links (`content:` aus der Galerie) und
     * `file:`-Pfade außerhalb des Fotoordners (auch per `..`-Umweg), die Foody sonst mit eigenen Rechten läse.
     */
    fun datei(link: String): File? {
        if (!link.startsWith("file:")) return null
        val pfad = runCatching { Uri.parse(link).path }.getOrNull() ?: return null
        val datei = runCatching { File(pfad).canonicalFile }.getOrNull() ?: return null
        return datei.takeIf { it.parentFile == ordner.canonicalFile }
    }

    private fun link(datei: File): String = Uri.fromFile(datei).toString()

    // Dateien nur über selbst geschlossene Streams lesen: decodeFile/ExifInterface(pfad) halten die Datei je nach
    // Umgebung offen (unter Windows/Robolectric scheitert dann das Ersetzen und Löschen).
    private fun laengereSeite(datei: File): Int {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        datei.inputStream().use { BitmapFactory.decodeStream(it, null, grenzen) }
        return maxOf(grenzen.outWidth, grenzen.outHeight)
    }

    /**
     * Verkleinert [datei] auf höchstens [MAX_KANTE] px an der längeren Seite, wendet die EXIF-Drehung an und speichert
     * als JPEG [JPEG_QUALITAET] neu. Bleibt das Ergebnis über [MAX_BYTES] (sehr detailreiche Bilder), wird die Kante
     * schrittweise weiter verringert. Ersetzt die Datei atomar, der Link bleibt gleich. Fotos, die schon in beide
     * Grenzen passen, bleiben unberührt. `true`, wenn die Datei ersetzt wurde; Fehler ergeben `false`.
     */
    private fun verkleinern(datei: File): Boolean = runCatching {
        val laengste = laengereSeite(datei)
        if (laengste <= 0 || (laengste <= MAX_KANTE && datei.length() <= MAX_BYTES)) return@runCatching false

        // Erst grob per Zweierpotenz beim Dekodieren (spart Speicher), dann exakt skalieren
        var stichprobe = 1
        while (laengste / (stichprobe * 2) >= MAX_KANTE) stichprobe *= 2
        val dekodiert = datei.inputStream().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = stichprobe })
        } ?: return@runCatching false
        val drehung = datei.inputStream().use { ExifInterface(it).rotationDegrees }

        var kante = MAX_KANTE
        var bytes: ByteArray
        while (true) {
            bytes = kodieren(dekodiert, kante, drehung)
            if (bytes.size <= MAX_BYTES || kante <= MIN_KANTE) break
            kante = maxOf(MIN_KANTE, (kante * SCHRITT).roundToInt())
        }
        dekodiert.recycle()

        val tmp = File(datei.parentFile, datei.name + TMP)
        try {
            tmp.writeBytes(bytes)
            ersetzen(tmp, datei)
        } finally {
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    private fun kodieren(bild: Bitmap, kante: Int, drehung: Int): ByteArray {
        val faktor = kante.toFloat() / maxOf(bild.width, bild.height)
        val matrix = Matrix().apply {
            if (faktor < 1f) postScale(faktor, faktor)
            if (drehung != 0) postRotate(drehung.toFloat())
        }
        val ergebnis = Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, matrix, true)
        val aus = ByteArrayOutputStream()
        ergebnis.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITAET, aus)
        if (ergebnis !== bild) ergebnis.recycle()
        return aus.toByteArray()
    }

    private fun ersetzen(von: File, nach: File) {
        Files.move(von.toPath(), nach.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        /** Erhöhen, wenn sich [MAX_KANTE]/[MAX_BYTES] ändern: dann prüft der nächste Start alle Fotos erneut. */
        const val VERKLEINERUNG_VERSION = 1
        const val MAX_KANTE = 1600
        const val MAX_BYTES = 600L * 1024
        const val JPEG_QUALITAET = 85

        /** Untergrenze beim weiteren Verkleinern über [MAX_BYTES] – kleiner wird ein Rezeptfoto nie. */
        private const val MIN_KANTE = 400
        private const val SCHRITT = 0.8f
        private const val TMP = ".tmp"
    }
}
