package de.foody.app.sync

import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.CreateHouseholdRequest
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.ErrorDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PhotosMissingRequest
import de.foody.sync.protocol.PhotosMissingResponse
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushRequest
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.RegisterRequest
import de.foody.sync.protocol.SelectHouseholdRequest
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/**
 * [SyncApi] über Ktor. [baseUrl] ohne abschließenden `/` (ein vorhandener wird toleriert).
 *
 * Der [client] braucht **kein** ContentNegotiation: Bodies werden hier selbst mit [Protocol.json] (de)serialisiert.
 * So funktioniert derselbe Code mit [defaultHttpClient] und mit dem Client einer `testApplication`.
 * [token] wird je Aufruf gelesen, damit ein neu gespeichertes Token sofort gilt.
 */
class KtorSyncApi(
    baseUrl: String,
    private val client: HttpClient,
    private val token: () -> String?,
) : SyncApi {
    private val base = baseUrl.trimEnd('/') + "/api/v1"

    override suspend fun login(req: LoginRequest): AuthResponse =
        call(HttpMethod.Post, "/auth/login", LoginRequest.serializer(), req, authenticated = false)
            .decode(AuthResponse.serializer())

    override suspend fun register(req: RegisterRequest): AuthResponse =
        call(HttpMethod.Post, "/auth/register", RegisterRequest.serializer(), req, authenticated = false)
            .decode(AuthResponse.serializer())

    override suspend fun households(): List<HouseholdDto> =
        call<Unit>(HttpMethod.Get, "/households").decode(ListSerializer(HouseholdDto.serializer()))

    override suspend fun createHousehold(name: String): HouseholdDto =
        call(HttpMethod.Post, "/households", CreateHouseholdRequest.serializer(), CreateHouseholdRequest(name))
            .decode(HouseholdDto.serializer())

    override suspend fun selectHousehold(id: String) {
        call(HttpMethod.Post, "/device/household", SelectHouseholdRequest.serializer(), SelectHouseholdRequest(id))
    }

    override suspend fun createInvite(): InviteDto =
        call<Unit>(HttpMethod.Post, "/invites").decode(InviteDto.serializer())

    override suspend fun devices(): List<DeviceDto> =
        call<Unit>(HttpMethod.Get, "/devices").decode(ListSerializer(DeviceDto.serializer()))

    override suspend fun revokeDevice(id: String) {
        call<Unit>(HttpMethod.Delete, "/devices/${id.encodeURLPathPart()}")
    }

    override suspend fun push(records: List<SyncRecord>): PushResponse =
        call(HttpMethod.Post, "/sync/push", PushRequest.serializer(), PushRequest(records))
            .decode(PushResponse.serializer())

    override suspend fun pull(since: Long, limit: Int): PullResponse =
        call<Unit>(HttpMethod.Get, "/sync/pull?since=$since&limit=$limit").decode(PullResponse.serializer())

    override suspend fun photosMissing(hashes: List<String>): List<String> =
        // Der Server nimmt höchstens MAX_PUSH_RECORDS Hashes je Anfrage.
        hashes.chunked(Protocol.MAX_PUSH_RECORDS).flatMap { chunk ->
            call(HttpMethod.Post, "/photos/missing", PhotosMissingRequest.serializer(), PhotosMissingRequest(chunk))
                .decode(PhotosMissingResponse.serializer()).missing
        }

    override suspend fun uploadPhoto(sha256: String, bytes: ByteArray) {
        call<Unit>(HttpMethod.Put, "/photos/${sha256.encodeURLPathPart()}", rawBody = bytes)
    }

    override suspend fun downloadPhoto(sha256: String): ByteArray? {
        try {
            return client.prepareRequest(base + "/photos/${sha256.encodeURLPathPart()}") {
                method = HttpMethod.Get
                header(Protocol.HEADER, Protocol.VERSION.toString())
                token()?.let { bearerAuth(it) }
            }.execute { response ->
                if (response.status.value == 404) return@execute null
                if (response.status.value !in 200..299) throw response.toException(response.bodyAsText())
                val declared = response.contentLength()
                if (declared != null && declared > Protocol.MAX_PHOTO_BYTES) {
                    throw SyncApiException.TooLarge(response.status.value, null)
                }
                // Gezähltes Lesen: nie mehr als MAX_PHOTO_BYTES puffern, auch wenn die Länge fehlt oder lügt.
                val channel = response.bodyAsChannel()
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n < 0) break
                    if (out.size() + n > Protocol.MAX_PHOTO_BYTES) throw SyncApiException.TooLarge(response.status.value, null)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            throw SyncApiException.Transient(0, null, e)
        }
    }

    /** Führt die Anfrage aus und liefert den Body als Text; jeder Fehler wird zu einer [SyncApiException]. */
    private suspend fun <T> call(
        httpMethod: HttpMethod,
        path: String,
        serializer: KSerializer<T>? = null,
        body: T? = null,
        authenticated: Boolean = true,
        rawBody: ByteArray? = null,
    ): String {
        val response = try {
            client.request(base + path) {
                method = httpMethod
                header(Protocol.HEADER, Protocol.VERSION.toString())
                if (authenticated) token()?.let { bearerAuth(it) }
                if (serializer != null && body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(Protocol.json.encodeToString(serializer, body))
                }
                if (rawBody != null) {
                    contentType(ContentType.Image.JPEG)
                    setBody(rawBody)
                }
            }
        // Verbindungsfehler und Zeitüberschreitungen (auch HttpRequestTimeoutException) sind IOExceptions;
        // Coroutine-Abbrüche laufen unverändert durch.
        } catch (e: IOException) {
            throw SyncApiException.Transient(0, null, e)
        }
        return try {
            val text = response.bodyAsText()
            if (response.status.value in 200..299) text else throw response.toException(text)
        } catch (e: IOException) {
            throw SyncApiException.Transient(response.status.value, null, e)
        }
    }

    private fun <T> String.decode(serializer: KSerializer<T>): T = try {
        Protocol.json.decodeFromString(serializer, this)
    } catch (_: SerializationException) {
        // Bewusst ohne Ursache: Decoder-Meldungen enthalten Ausschnitte des Bodys (bei Login das Token).
        throw SyncApiException.Transient(200, null)
    } catch (_: IllegalArgumentException) {
        throw SyncApiException.Transient(200, null)
    }

    private fun HttpResponse.toException(text: String): SyncApiException {
        val status = status.value
        // Der Body kann fehlen oder kein JSON sein (z. B. Fehlerseite eines Reverse-Proxys).
        val code = try {
            Protocol.json.decodeFromString(ErrorDto.serializer(), text).code
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return when {
            status == 401 -> SyncApiException.Unauthorized(status, code)
            status == 409 && (code == ErrorCode.PROTOCOL_TOO_OLD || code == ErrorCode.SERVER_TOO_OLD) ->
                SyncApiException.ProtocolMismatch(status, code)
            status == 409 && code == ErrorCode.NO_HOUSEHOLD -> SyncApiException.NoHousehold(status, code)
            status == 410 -> SyncApiException.CursorExpired(status, code)
            status == 413 -> SyncApiException.TooLarge(status, code)
            status == 429 -> SyncApiException.Throttled(status, code)
            // 3xx: Weiterleitungen werden nicht verfolgt; eine falsche Basis-URL ist dauerhaft, nicht vorübergehend.
            status in 300..499 -> SyncApiException.ClientError(status, code)
            else -> SyncApiException.Transient(status, code)
        }
    }
}

/** OkHttp-Client mit den Timeouts des Sync (Verbindung 15 s, Anfrage 60 s); Statuscodes wertet [KtorSyncApi] selbst aus. */
fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    // Das Token darf nur an die konfigurierte Basis-URL gehen, nie an ein Weiterleitungsziel.
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 60_000
        // Ohne diesen Wert gelten die OkHttp-Standards (10 s lesen/schreiben) – zu knapp für große Pushes.
        socketTimeoutMillis = 60_000
    }
}
