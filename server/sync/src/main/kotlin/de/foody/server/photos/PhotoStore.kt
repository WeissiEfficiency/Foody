package de.foody.server.photos

import de.foody.sync.protocol.PhotoHash
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * Fotoablage auf dem Dateisystem: `<root>/<kontoId>/<sha256>.jpg`. Pfade entstehen nur aus einem
 * validierten Hash und einer Konto-ID aus der Datenbank; alles andere wird abgewiesen.
 */
class PhotoStore(private val root: Path) {
    private fun kontoDir(konto: String): Path {
        require(KONTO_REGEX.matches(konto)) { "ungültige Konto-ID" }
        return root.resolve(konto)
    }

    private fun fileOf(konto: String, sha: String): Path {
        require(PhotoHash.isValid(sha)) { "ungültiger Foto-Hash" }
        return kontoDir(konto).resolve("$sha.jpg")
    }

    fun exists(konto: String, sha: String): Boolean = Files.isRegularFile(fileOf(konto, sha))

    /** Pfad der Datei oder `null`, wenn das Konto dieses Foto nicht hat. */
    fun read(konto: String, sha: String): Path? = fileOf(konto, sha).takeIf { Files.isRegularFile(it) }

    /** Setzt die Änderungszeit auf [now]: Die Schonfrist der Aufräumung beginnt neu (Foto wird gerade gebraucht). */
    fun touch(konto: String, sha: String, now: Instant) {
        try {
            Files.setLastModifiedTime(fileOf(konto, sha), FileTime.from(now))
        } catch (_: IOException) {
            // Datei zwischenzeitlich weg: nichts zu verlängern.
        }
    }

    /** Schreibt über eine temporäre Datei im selben Ordner und benennt atomar um. */
    fun write(konto: String, sha: String, bytes: ByteArray) {
        val target = fileOf(konto, sha)
        val dir = target.parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "upload-", ".tmp")
        try {
            Files.write(tmp, bytes)
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    /** Alle Fotos des Kontos mit ihrer Änderungszeit; angefangene Uploads (`.tmp`) zählen nicht. */
    fun listHashes(konto: String): List<Pair<String, Instant>> {
        val dir = kontoDir(konto)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { files ->
            files.toList().mapNotNull { file ->
                val name = file.fileName.toString()
                val sha = name.removeSuffix(".jpg")
                if (!name.endsWith(".jpg") || !PhotoHash.isValid(sha) || !Files.isRegularFile(file)) return@mapNotNull null
                sha to Files.getLastModifiedTime(file).toInstant()
            }
        }
    }

    fun delete(konto: String, sha: String) {
        Files.deleteIfExists(fileOf(konto, sha))
    }

    internal companion object {
        val KONTO_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
