package de.foody.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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

    private fun owned(uri: String?): File? {
        if (uri == null || !uri.startsWith("file:")) return null
        val file = runCatching { File(uri.toUri().path!!).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == dir.canonicalFile }
    }

    private companion object {
        const val DIR = "recipe_images"
    }
}
