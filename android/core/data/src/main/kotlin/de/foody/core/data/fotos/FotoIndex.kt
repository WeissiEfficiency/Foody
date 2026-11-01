package de.foody.core.data.fotos

import de.foody.core.data.db.SyncDao
import de.foody.core.data.db.SyncFotoLokalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Prüfsumme (SHA-256, Kleinbuchstaben-Hex – so adressiert der Server die Fotos) je eigener Fotodatei.
 *
 * Der Hash wird mit Größe und Änderungszeit der Datei in `sync_photo_local` gemerkt (Schlüssel: der Link); passt
 * beides noch, wird die Datei nicht erneut gelesen. Ändert sich die Datei (etwa durch Verkleinern), wird neu gehasht.
 * Fremde Links (`content:` aus der Galerie, Pfade außerhalb des Fotoordners) haben keinen Hash – [ablage] entscheidet,
 * was ein eigenes Foto ist.
 */
class FotoIndex(private val dao: SyncDao, private val ablage: FotoAblage) {

    /** Hash des eigenen Fotos hinter [link]; `null` bei fremden Links, fehlender oder unlesbarer Datei. */
    suspend fun hash(link: String): String? {
        val datei = ablage.datei(link)?.takeIf { it.isFile } ?: return null
        // Größe und Zeit vor dem Hashen lesen: ändert sich die Datei währenddessen, passt der Eintrag beim nächsten Mal nicht.
        val groesse = datei.length()
        val geaendert = datei.lastModified()
        dao.fotoLokal(link)?.takeIf { it.size == groesse && it.modifiedAt == geaendert }?.let { return it.sha256 }
        val sha = withContext(Dispatchers.IO) { runCatching { sha256(datei) }.getOrNull() } ?: return null
        dao.upsertFotoLokal(SyncFotoLokalEntity(link, sha, groesse, geaendert))
        return sha
    }

    /**
     * Link einer vorhandenen eigenen Fotodatei mit diesem Hash, sonst `null`. Zählt nur, wenn der gemerkte Eintrag noch
     * zur Datei passt (Größe und Änderungszeit) – eine inzwischen geänderte oder gelöschte Datei hat einen anderen Inhalt.
     */
    suspend fun link(sha256: String): String? = dao.fotosLokalNachHash(sha256).firstOrNull { eintrag ->
        val datei = ablage.datei(eintrag.uri)
        datei != null && datei.isFile && datei.length() == eintrag.size && datei.lastModified() == eintrag.modifiedAt
    }?.uri

    private fun sha256(datei: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        datei.inputStream().use { ein ->
            val puffer = ByteArray(PUFFER)
            while (true) {
                val n = ein.read(puffer)
                if (n < 0) break
                digest.update(puffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PUFFER = 64 * 1024
    }
}
