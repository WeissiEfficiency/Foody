package de.foody.core.sync

import de.foody.api.protocol.AuthResponse
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.ErrorDto
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PhotosMissingRequest
import de.foody.api.protocol.PhotosMissingResponse
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.PushRequest
import de.foody.api.protocol.PushResponse
import de.foody.api.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
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

/**
 * [ServerZugang] über Ktor. [basisAdresse] ist die geprüfte Server-Adresse ([ServerAdresse.pruefen]), ohne `/api/v3`.
 *
 * Der [client] braucht kein ContentNegotiation: Bodies werden hier selbst mit [Protocol.json] verarbeitet. So läuft
 * derselbe Code mit [standardHttpClient] und mit dem Client einer `testApplication`. [token] wird je Anfrage gelesen,
 * damit ein neu gespeichertes Token sofort gilt.
 */
class KtorServerZugang(
    basisAdresse: String,
    private val client: HttpClient,
    private val token: () -> String?,
) : ServerZugang {
    private val basis = basisAdresse.trimEnd('/') + "/api/v3"

    override suspend fun anmelden(anfrage: LoginRequest): AuthResponse =
        anfragen(HttpMethod.Post, "/auth/login", mitToken = false) { json(LoginRequest.serializer(), anfrage) }
            .lesen(AuthResponse.serializer())

    override suspend fun abmelden() {
        anfragen(HttpMethod.Post, "/auth/abmelden")
    }

    override suspend fun push(datensaetze: List<SyncRecord>): PushResponse =
        anfragen(HttpMethod.Post, "/sync/push") { json(PushRequest.serializer(), PushRequest(datensaetze)) }
            .lesen(PushResponse.serializer())

    override suspend fun pull(since: Long, limit: Int): PullResponse =
        anfragen(HttpMethod.Get, "/sync/pull?since=$since&limit=$limit").lesen(PullResponse.serializer())

    override suspend fun fotosFehlend(hashes: List<String>): List<String> =
        // Der Server nimmt höchstens MAX_PUSH_RECORDS Hashes je Anfrage.
        hashes.chunked(Protocol.MAX_PUSH_RECORDS).flatMap { paket ->
            anfragen(HttpMethod.Post, "/photos/missing") { json(PhotosMissingRequest.serializer(), PhotosMissingRequest(paket)) }
                .lesen(PhotosMissingResponse.serializer()).missing
        }

    override suspend fun fotoHochladen(sha256: String, bytes: ByteArray) {
        anfragen(HttpMethod.Put, "/photos/${sha256.encodeURLPathPart()}") {
            contentType(ContentType.Image.JPEG)
            setBody(bytes)
        }
    }

    override suspend fun fotoLaden(sha256: String): ByteArray? = try {
        client.prepareRequest(basis + "/photos/${sha256.encodeURLPathPart()}") {
            method = HttpMethod.Get
            kopf(mitToken = true)
        }.execute { antwort ->
            val status = antwort.status.value
            if (status !in 200..299) {
                val code = fehlerCode(antwort.bodyAsText())
                // Nur ein `not_found` des Foody-Servers heißt „Foto fehlt“; ein 404 ohne Code ist ein Server ohne /api/v3.
                if (status == 404 && code == ErrorCode.NOT_FOUND) return@execute null
                throw fehlerFuer(status, code)
            }
            val angegeben = antwort.contentLength()
            if (angegeben != null && angegeben > Protocol.MAX_PHOTO_BYTES) throw ServerFehler.ZuGross()
            // Gezähltes Lesen: nie mehr als MAX_PHOTO_BYTES puffern, auch wenn die Länge fehlt oder nicht stimmt.
            val kanal = antwort.bodyAsChannel()
            val ausgabe = ByteArrayOutputStream()
            val puffer = ByteArray(8 * 1024)
            while (true) {
                val n = kanal.readAvailable(puffer, 0, puffer.size)
                if (n < 0) break
                if (ausgabe.size() + n > Protocol.MAX_PHOTO_BYTES) throw ServerFehler.ZuGross()
                ausgabe.write(puffer, 0, n)
            }
            ausgabe.toByteArray()
        }
    } catch (e: IOException) {
        throw ServerFehler.Offline(e)
    }

    /** Setzt Protokoll-Header und – falls gewünscht und vorhanden – das Token. */
    private fun HttpRequestBuilder.kopf(mitToken: Boolean) {
        header(Protocol.HEADER, Protocol.VERSION.toString())
        if (mitToken) token()?.let { bearerAuth(it) }
    }

    private fun <T> HttpRequestBuilder.json(serializer: KSerializer<T>, wert: T) {
        contentType(ContentType.Application.Json)
        setBody(Protocol.json.encodeToString(serializer, wert))
    }

    /** Führt die Anfrage aus und liefert den Body als Text; jeder Fehler wird zu einem [ServerFehler]. */
    private suspend fun anfragen(
        methode: HttpMethod,
        pfad: String,
        mitToken: Boolean = true,
        body: HttpRequestBuilder.() -> Unit = {},
    ): String {
        // Verbindungsfehler und Zeitüberschreitungen (auch HttpRequestTimeoutException) sind IOExceptions;
        // Coroutine-Abbrüche laufen unverändert durch.
        val antwort = try {
            client.request(basis + pfad) {
                method = methode
                kopf(mitToken)
                body()
            }
        } catch (e: IOException) {
            throw ServerFehler.Offline(e)
        }
        val text = try {
            antwort.bodyAsText()
        } catch (e: IOException) {
            throw ServerFehler.Offline(e)
        }
        val status = antwort.status.value
        if (status !in 200..299) throw fehlerFuer(status, fehlerCode(text))
        return text
    }

    private fun <T> String.lesen(serializer: KSerializer<T>): T = try {
        Protocol.json.decodeFromString(serializer, this)
    } catch (_: SerializationException) {
        // Bewusst ohne Ursache: Decoder-Meldungen enthalten Ausschnitte des Bodys (bei der Anmeldung das Token).
        throw ServerFehler.Server(200)
    } catch (_: IllegalArgumentException) {
        throw ServerFehler.Server(200)
    }

    /** Fehlercode aus dem Body; `null`, wenn er fehlt oder kein Foody-JSON ist (z. B. Fehlerseite eines Proxys). */
    private fun fehlerCode(text: String): ErrorCode? = try {
        Protocol.json.decodeFromString(ErrorDto.serializer(), text).code
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun fehlerFuer(status: Int, code: ErrorCode?): ServerFehler = when {
        status == 401 -> ServerFehler.AnmeldungAbgelaufen(code)
        status == 409 && code == ErrorCode.PROTOCOL_TOO_OLD -> ServerFehler.AppZuAlt()
        status == 409 && code == ErrorCode.SERVER_TOO_OLD -> ServerFehler.ServerZuAlt()
        status == 404 -> ServerFehler.ServerZuAlt()
        status == 410 -> ServerFehler.CursorAbgelaufen()
        status == 413 -> ServerFehler.ZuGross()
        // 3xx: Weiterleitungen werden nicht verfolgt (das Token geht nur an die eingestellte Adresse).
        status in 300..499 -> ServerFehler.Abgelehnt(status, code)
        else -> ServerFehler.Server(status)
    }
}

/**
 * OkHttp-Client mit den Zeitgrenzen wie in der v1 (Verbindung 15 s, Anfrage und Socket 60 s). Statuscodes wertet
 * [KtorServerZugang] selbst aus; Weiterleitungen werden nicht verfolgt.
 */
fun standardHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    // Das Token darf nur an die eingestellte Adresse gehen, nie an ein Weiterleitungsziel.
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 60_000
        // Ohne diesen Wert gelten die OkHttp-Standards (10 s lesen/schreiben) – zu knapp für große Pushes.
        socketTimeoutMillis = 60_000
    }
}
