package de.foody.server.photos

import de.foody.sync.protocol.PhotoHash
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Fotoablage auf dem Dateisystem: `<root>/<householdId>/<sha256>.jpg`. Pfade entstehen nur aus einem
 * validierten Hash und einer Haushalts-ID aus der Datenbank; alles andere wird abgewiesen.
 */
class PhotoStore(private val root: Path) {
    private fun householdDir(household: String): Path {
        require(HOUSEHOLD_REGEX.matches(household)) { "ungültige Haushalts-ID" }
        return root.resolve(household)
    }

    private fun fileOf(household: String, sha: String): Path {
        require(PhotoHash.isValid(sha)) { "ungültiger Foto-Hash" }
        return householdDir(household).resolve("$sha.jpg")
    }

    fun exists(household: String, sha: String): Boolean = Files.isRegularFile(fileOf(household, sha))

    /** Pfad der Datei oder `null`, wenn der Haushalt dieses Foto nicht hat. */
    fun read(household: String, sha: String): Path? = fileOf(household, sha).takeIf { Files.isRegularFile(it) }

    /** Schreibt über eine temporäre Datei im selben Ordner und benennt atomar um. */
    fun write(household: String, sha: String, bytes: ByteArray) {
        val target = fileOf(household, sha)
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

    /** Alle Fotos des Haushalts mit ihrer Änderungszeit; angefangene Uploads (`.tmp`) zählen nicht. */
    fun listHashes(household: String): List<Pair<String, Instant>> {
        val dir = householdDir(household)
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

    fun delete(household: String, sha: String) {
        Files.deleteIfExists(fileOf(household, sha))
    }

    private companion object {
        val HOUSEHOLD_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
