package de.foody.app.sync

import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SyncPhotoLocalEntity
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hash (SHA-256 als Kleinbuchstaben-Hex) je eigener Fotodatei und Suche einer Datei zu einem Hash.
 * Der Hash wird je Datei mit Größe und Änderungszeit zwischengespeichert (`sync_photo_local`); ändert sich eine
 * Datei (etwa durch [RecipePhotoStore.shrink]), wird sie neu gehasht. Fremde Links (`content:` u. a.) haben
 * keinen Hash und werden nicht synchronisiert.
 */
@Singleton
class PhotoIndex @Inject constructor(
    private val db: FoodyDatabase,
    private val photoStore: RecipePhotoStore,
) {
    private val dao get() = db.syncDao()

    /** Hash des eigenen Fotos hinter [imageUri]; `null` bei `null`, fremden Links oder fehlender Datei. */
    suspend fun hashOf(imageUri: String?): String? {
        if (imageUri == null) return null
        val file = photoStore.fileOf(imageUri)?.takeIf { it.isFile } ?: return null
        // Größe und Zeit vor dem Hashen lesen: ändert sich die Datei währenddessen, passt der Cache beim nächsten Mal nicht.
        val size = file.length()
        val modifiedAt = file.lastModified()
        dao.photoLocal(imageUri)?.takeIf { it.size == size && it.modifiedAt == modifiedAt }?.let { return it.sha256 }
        val sha = withContext(Dispatchers.IO) { runCatching { sha256Of(file) }.getOrNull() } ?: return null
        dao.upsertPhotoLocal(SyncPhotoLocalEntity(imageUri, sha, size, modifiedAt))
        return sha
    }

    /** Link einer vorhandenen eigenen Fotodatei mit diesem Hash (Cache-Eintrag aktuell); sonst `null`. */
    suspend fun uriFor(sha256: String): String? = dao.photoLocalsByHash(sha256).firstOrNull { entry ->
        val file = photoStore.fileOf(entry.uri)
        file != null && file.isFile && file.length() == entry.size && file.lastModified() == entry.modifiedAt
    }?.uri

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
