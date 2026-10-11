package de.foody.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.RecipeDao
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eigene Rezeptfotos aus der Kamera. Die Kamera-App schreibt über einen FileProvider-Link in den privaten
 * App-Speicher – dafür braucht Foody keine Kamera-Berechtigung. Gespeichert wird ein file://-Link, den Coil lädt.
 */
@Singleton
class RecipePhotoStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recipeDao: RecipeDao,
) {
    private val dir get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** Neue Zieldatei und der Link, den die Kamera-App beschreiben darf. */
    fun newPhotoTarget(): Pair<File, Uri> {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Neue, leere Fotodatei – etwa für ein Foto aus einer Sicherung. */
    fun newPhotoFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    /** Löscht alle eigenen Fotos, auf die kein Rezept mehr zeigt (nach Wiederherstellen oder „Alles löschen“). */
    suspend fun pruneUnused() {
        dir.listFiles()?.forEach { if (recipeDao.countByImage(storedUri(it)) == 0) it.delete() }
    }

    /**
     * Verkleinert ein Foto auf höchstens [MAX_EDGE] Pixel an der längeren Seite und speichert es als JPEG neu.
     * Kamera-Apps liefern 12–50 Megapixel (3–8 MB); für Rezeptkarten und Detailkopf reichen 1600 px (≈ 200–400 KB).
     * Die Drehung aus den EXIF-Daten wird dabei angewendet, sonst lägen Hochformat-Fotos danach quer.
     * Ersetzt die Datei atomar (Umbenennen), der gespeicherte Link bleibt gleich. Kleine Fotos bleiben unberührt.
     */
    suspend fun shrink(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0 || (longest <= MAX_EDGE && file.length() <= MAX_BYTES)) return@runCatching false

            // Erst grob per Zweierpotenz beim Dekodieren (spart Speicher), dann exakt skalieren
            var sample = 1
            while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching false
            val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                val degrees = ExifInterface(file.path).rotationDegrees
                if (degrees != 0) postRotate(degrees.toFloat())
            }
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (result !== decoded) result.recycle()
            decoded.recycle()
            tmp.renameTo(file) || run { tmp.delete(); false }
        }.getOrDefault(false)
    }

    /** Alle eigenen Fotos prüfen und zu große verkleinern; gibt die Zahl verkleinerter Fotos zurück. */
    suspend fun shrinkAll(): Int = dir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }.orEmpty().count { shrink(it) }

    /**
     * Beim Start: [shrinkAll] nur einmal je [SHRINK_VERSION]. Neue Fotos verkleinern Editor und Detailansicht schon beim
     * Speichern, Fotos aus einer Sicherung der Import – ein Durchlauf bei jedem Start läse sonst jedes Mal alle Dateien.
     */
    suspend fun shrinkAllEinmal(): Int {
        val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_VERKLEINERT, 0) >= SHRINK_VERSION) return 0
        val n = shrinkAll()
        prefs.edit { putInt(PREF_VERKLEINERT, SHRINK_VERSION) }
        return n
    }

    /** Link, der am Rezept gespeichert wird. */
    fun storedUri(file: File): String = file.toUri().toString()

    /**
     * Löscht ein eigenes Foto, wenn kein Rezept mehr darauf verweist (Kopien teilen sich die Datei).
     * Fremde Links (Fotoauswahl, andere Ordner) werden nie angefasst.
     */
    suspend fun deleteIfUnused(uri: String?) {
        val file = owned(uri) ?: return
        if (recipeDao.countByImage(uri!!) == 0) file.delete()
    }

    /**
     * Darf Foody diesen Bildverweis lesen? Eigene Fotos (file: im Fotoordner) und content:-Links, für die das System
     * ohnehin eine Freigabe verlangt. Alles andere – etwa file:-Pfade zur eigenen Datenbank aus einer manipulierten
     * Sicherung – würde Foody mit seinen eigenen Rechten lesen und beim nächsten Export mit einpacken.
     */
    fun isAllowedImage(uri: String?): Boolean =
        uri != null && (uri.startsWith("content://") || owned(uri) != null)

    /** Ist [uri] ein eigenes Foto (file:-Link auf eine Datei im Fotoordner)? Die Datei muss nicht existieren. */
    fun isOwnPhoto(uri: String): Boolean = owned(uri) != null

    /** Die Datei hinter einem eigenen Foto-Link, sonst `null` (fremde Links, Pfade außerhalb des Fotoordners). */
    fun fileOf(uri: String): File? = owned(uri)

    private fun owned(uri: String?): File? {
        if (uri == null || !uri.startsWith("file:")) return null
        val file = runCatching { File(uri.toUri().path!!).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == dir.canonicalFile }
    }

    companion object {
        /** Merker in den Prefs `foody`: bis zu welcher [SHRINK_VERSION] der Start schon verkleinert hat. */
        const val PREF_VERKLEINERT = "fotosVerkleinert"
        /** Erhöhen, wenn sich [MAX_EDGE]/[MAX_BYTES] ändern: dann prüft der nächste Start alle Fotos erneut. */
        private const val SHRINK_VERSION = 1
        private const val DIR = "recipe_images"
        private const val MAX_EDGE = 1600
        private const val MAX_BYTES = 600L * 1024
        private const val JPEG_QUALITY = 85
    }
}
