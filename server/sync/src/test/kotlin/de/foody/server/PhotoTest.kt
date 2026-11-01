package de.foody.server

import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.PhotosMissingRequest
import de.foody.api.protocol.PhotosMissingResponse
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.server.sync.Compactor
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoTest {
    private fun jpeg(seed: Int = 1, size: Int = 64): ByteArray =
        ByteArray(size) { (it * seed).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }

    private suspend fun HttpClient.upload(token: String, sha: String, bytes: ByteArray, type: ContentType = ContentType.Image.JPEG): HttpResponse =
        put("/api/v3/photos/$sha") { protocol(); bearerAuth(token); contentType(type); setBody(bytes) }

    private suspend fun HttpClient.download(token: String, sha: String): HttpResponse =
        get("/api/v3/photos/$sha") { protocol(); bearerAuth(token) }

    private suspend fun HttpClient.missing(token: String, hashes: List<String>): HttpResponse =
        post("/api/v3/photos/missing") {
            protocol(); bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(PhotosMissingRequest.serializer(), PhotosMissingRequest(hashes)))
        }

    private fun recipeWithPhoto(id: String, photo: String?) = SyncRecord(
        id = id, type = RecordType.RECIPE, updatedAt = 1,
        payload = obj(RecipePayload(name = "Brot", servings = 4, photo = photo, tags = "", favorite = false, lines = emptyList(), steps = emptyList())),
    )

    private fun filesUnder(dir: Path): List<Path> = if (!Files.exists(dir)) emptyList() else Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) }.toList() }

    @Test
    fun uploadThenDownloadRoundTrip() = testServer { env ->
        val (token, konto) = env.setupKonto()
        val bytes = jpeg()
        val sha = PhotoHash.of(bytes)
        assertEquals(HttpStatusCode.NoContent, client.upload(token, sha, bytes).status)
        val response = client.download(token, sha)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.JPEG, response.contentType()?.withoutParameters())
        assertContentEquals(bytes, response.bodyAsBytes())
        assertTrue(Files.isRegularFile(env.photoDir.resolve(konto).resolve("$sha.jpg")))
        // Content-Type wird nicht vertraut: ein anderer Typ mit JPEG-Inhalt wird angenommen.
        val other = jpeg(3)
        assertEquals(HttpStatusCode.NoContent, client.upload(token, PhotoHash.of(other), other, ContentType.Application.OctetStream).status)
    }

    @Test
    fun uploadIsIdempotent() = testServer { env ->
        val (token, _) = env.setupKonto()
        val bytes = jpeg()
        val sha = PhotoHash.of(bytes)
        assertEquals(HttpStatusCode.NoContent, client.upload(token, sha, bytes).status)
        assertEquals(HttpStatusCode.NoContent, client.upload(token, sha, bytes).status)
        assertEquals(1, filesUnder(env.photoDir).size)
    }

    @Test
    fun missingListsOnlyUnknownHashes() = testServer { env ->
        val (token, _) = env.setupKonto()
        val known = jpeg(1)
        val unknown = PhotoHash.of(jpeg(2))
        client.upload(token, PhotoHash.of(known), known)
        val response = client.missing(token, listOf(PhotoHash.of(known), unknown))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(unknown), Protocol.json.decodeFromString(PhotosMissingResponse.serializer(), response.bodyAsText()).missing)

        val tooMany = client.missing(token, List(501) { PhotoHash.of(byteArrayOf(it.toByte(), (it shr 8).toByte())) })
        assertEquals(HttpStatusCode.PayloadTooLarge, tooMany.status)
        assertEquals(ErrorCode.TOO_LARGE, tooMany.errorCode())
        val invalid = client.missing(token, listOf(unknown, "ABC"))
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals(ErrorCode.INVALID_INPUT, invalid.errorCode())
    }

    @Test
    fun wrongHashIsRejected() = testServer { env ->
        val (token, _) = env.setupKonto()
        val response = client.upload(token, PhotoHash.of(jpeg(2)), jpeg(1))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.INVALID_INPUT, response.errorCode())
        assertTrue(filesUnder(env.photoDir).isEmpty())
    }

    @Test
    fun nonJpegIsRejected() = testServer { env ->
        val (token, _) = env.setupKonto()
        val bytes = "kein Bild".toByteArray()
        val response = client.upload(token, PhotoHash.of(bytes), bytes)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.INVALID_INPUT, response.errorCode())
        assertTrue(filesUnder(env.photoDir).isEmpty())
    }

    @Test
    fun tooLargeIsRejected() = testServer { env ->
        val (token, _) = env.setupKonto()
        val bytes = jpeg(size = (Protocol.MAX_PHOTO_BYTES + 1).toInt())
        val response = client.upload(token, PhotoHash.of(bytes), bytes)
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.TOO_LARGE, response.errorCode())
        assertTrue(filesUnder(env.photoDir).isEmpty())

        val limit = jpeg(size = Protocol.MAX_PHOTO_BYTES.toInt())
        assertEquals(HttpStatusCode.NoContent, client.upload(token, PhotoHash.of(limit), limit).status)
    }

    @Test
    fun invalidPathSegmentsAre400() = testServer { env ->
        val (token, _) = env.setupKonto()
        val bytes = jpeg()
        val upper = PhotoHash.of(bytes).uppercase()
        for (segment in listOf("..%2Fx", "..%2F..%2Fescape", "a".repeat(63), "a".repeat(65), upper, "%2E%2E")) {
            val put = client.upload(token, segment, bytes)
            assertEquals(HttpStatusCode.BadRequest, put.status, "PUT $segment")
            assertEquals(ErrorCode.INVALID_INPUT, put.errorCode())
            val get = client.download(token, segment)
            assertEquals(HttpStatusCode.BadRequest, get.status, "GET $segment")
        }
        // Nichts im Foto-Ordner und nichts daneben (Geschwister des Foto-Ordners).
        assertTrue(filesUnder(env.photoDir).isEmpty())
        assertFalse(Files.exists(env.photoDir.resolve("x")))
        assertFalse(Files.exists(env.photoDir.parent.resolve("x")))
        assertFalse(Files.exists(env.photoDir.parent.resolve("escape")))
    }

    @Test
    fun kontenCannotReadEachOthersPhotos() = testServer { env ->
        val (tokenA, _) = env.setupKonto()
        val (tokenB, _) = env.setupKonto()
        val bytes = jpeg()
        val sha = PhotoHash.of(bytes)
        client.upload(tokenA, sha, bytes)
        val response = client.download(tokenB, sha)
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCode.NOT_FOUND, response.errorCode())
        val missing = Protocol.json.decodeFromString(PhotosMissingResponse.serializer(), client.missing(tokenB, listOf(sha)).bodyAsText()).missing
        assertEquals(listOf(sha), missing)
    }

    @Test
    fun compactorRemovesUnreferencedOldPhotos() = testServer { env ->
        val (token, konto) = env.setupKonto()
        val kept = jpeg(1)
        val oldUnreferenced = jpeg(2)
        val youngUnreferenced = jpeg(3)
        val deletedRecipe = jpeg(4)
        for (b in listOf(kept, oldUnreferenced, youngUnreferenced, deletedRecipe)) {
            assertEquals(HttpStatusCode.NoContent, client.upload(token, PhotoHash.of(b), b).status)
        }
        client.pushOk(token, listOf(recipeWithPhoto("r1", PhotoHash.of(kept)), recipeWithPhoto("r2", PhotoHash.of(deletedRecipe))))
        client.pushOk(token, listOf(SyncRecord("r2", RecordType.RECIPE, deleted = true, updatedAt = 2)))

        val old = FileTime.from(env.clock.instant().minus(Duration.ofDays(31)))
        fun file(b: ByteArray) = env.photoDir.resolve(konto).resolve("${PhotoHash.of(b)}.jpg")
        for (b in listOf(kept, oldUnreferenced, deletedRecipe)) Files.setLastModifiedTime(file(b), old)
        Files.setLastModifiedTime(file(youngUnreferenced), FileTime.from(env.clock.instant().minus(Duration.ofDays(29))))

        Compactor(env.deps.db, env.clock, env.deps.photos).run()

        assertTrue(Files.exists(file(kept)))
        assertFalse(Files.exists(file(oldUnreferenced)))
        assertTrue(Files.exists(file(youngUnreferenced)))
        assertFalse(Files.exists(file(deletedRecipe)))
    }

    @Test
    fun missingAndRepeatedUploadRefreshModifiedTimeSoCompactorKeepsPhoto() = testServer { env ->
        val (token, konto) = env.setupKonto()
        val viaMissing = jpeg(2)
        val viaPut = jpeg(3)
        client.upload(token, PhotoHash.of(viaMissing), viaMissing)
        client.upload(token, PhotoHash.of(viaPut), viaPut)
        fun file(b: ByteArray) = env.photoDir.resolve(konto).resolve("${PhotoHash.of(b)}.jpg")
        val old = FileTime.from(env.clock.instant().minus(Duration.ofDays(31)))
        Files.setLastModifiedTime(file(viaMissing), old)
        Files.setLastModifiedTime(file(viaPut), old)

        // Ein Gerät fragt nach dem Hash (es will ihn gleich referenzieren) bzw. lädt dieselbe Datei erneut hoch.
        assertTrue(client.missing(token, listOf(PhotoHash.of(viaMissing))).bodyAsText().contains("[]"))
        assertEquals(HttpStatusCode.NoContent, client.upload(token, PhotoHash.of(viaPut), viaPut).status)

        assertEquals(env.clock.instant(), Files.getLastModifiedTime(file(viaMissing)).toInstant())
        assertEquals(env.clock.instant(), Files.getLastModifiedTime(file(viaPut)).toInstant())
        Compactor(env.deps.db, env.clock, env.deps.photos).run()
        assertTrue(Files.exists(file(viaMissing)))
        assertTrue(Files.exists(file(viaPut)))
    }

    /** N7: Braucht ein Gerät das Foto zwischen Auflisten und Löschen wieder (`/photos/missing`), bleibt es liegen. */
    @Test
    fun aufraeumenPrueftDieZeitDirektVorDemLoeschen() = testServer { env ->
        val (token, konto) = env.setupKonto()
        val b = jpeg(5)
        val sha = PhotoHash.of(b)
        client.upload(token, sha, b)
        val datei = env.photoDir.resolve(konto).resolve("$sha.jpg")
        val alt = FileTime.from(env.clock.instant().minus(Duration.ofDays(31)))
        val grenze = env.clock.instant().minus(Duration.ofDays(30))
        Files.setLastModifiedTime(datei, alt)
        // Die Aufräumung hat das Foto als alt aufgelistet; jetzt fragt ein Gerät danach.
        client.missing(token, listOf(sha))
        assertFalse(env.deps.photos.loeschenWennAelter(konto, sha, grenze))
        assertTrue(Files.exists(datei))
        Files.setLastModifiedTime(datei, alt)
        assertTrue(env.deps.photos.loeschenWennAelter(konto, sha, grenze))
        assertFalse(Files.exists(datei))
    }
}
