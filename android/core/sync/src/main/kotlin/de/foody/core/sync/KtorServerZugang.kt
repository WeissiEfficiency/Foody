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
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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

    override suspend fun anmelden(anfrage: LoginRequest): AuthResponse = abgesichert {
        anfragen(HttpMethod.Post, "/auth/login", mitToken = false) { json(LoginRequest.serializer(), anfrage) }
            .lesen(AuthResponse.serializer())
    }

    override suspend fun abmelden() = abgesichert {
        anfragen(HttpMethod.Post, "/auth/abmelden")
        Unit
    }

    override suspend fun push(datensaetze: List<SyncRecord>): PushResponse = abgesichert {
        anfragen(HttpMethod.Post, "/sync/push") { json(PushRequest.serializer(), PushRequest(datensaetze)) }
            .lesen(PushResponse.serializer())
    }

    override suspend fun pull(since: Long, limit: Int): PullResponse = abgesichert {
        anfragen(HttpMethod.Get, "/sync/pull?since=$since&limit=$limit").lesen(PullResponse.serializer())
    }

    override suspend fun fotosFehlend(hashes: List<String>): List<String> = abgesichert {
        // Der Server nimmt höchstens MAX_PUSH_RECORDS Hashes je Anfrage.
        hashes.chunked(Protocol.MAX_PUSH_RECORDS).flatMap { paket ->
            anfragen(HttpMethod.Post, "/photos/missing") { json(PhotosMissingRequest.serializer(), PhotosMissingRequest(paket)) }
                .lesen(PhotosMissingResponse.serializer()).missing
        }
    }

    override suspend fun fotoHochladen(sha256: String, bytes: ByteArray) = abgesichert {
        anfragen(HttpMethod.Put, "/photos/${sha256.encodeURLPathPart()}") {
            contentType(ContentType.Image.JPEG)
            setBody(bytes)
        }
        Unit
    }

    override suspend fun fotoLaden(sha256: String): ByteArray? = abgesichert {
        client.prepareRequest(basis + "/photos/${sha256.encodeURLPathPart()}") {
            method = HttpMethod.Get
            kopf(mitToken = true)
        }.execute { antwort ->
            val status = antwort.status.value
            if (status !in 200..299) {
                val code = fehlerCode(antwort.fehlerText())
                // Nur ein `not_found` des Foody-Servers heißt „Foto fehlt“; ein 404 ohne Code ist ein Server ohne /api/v3.
                if (status == 404 && code == ErrorCode.NOT_FOUND) return@execute null
                throw fehlerFuer(status, code)
            }
            antwort.begrenztLesen(Protocol.MAX_PHOTO_BYTES)
        }
    }

    /**
     * Führt [block] aus und lässt nur [ServerFehler] und Coroutine-Abbrüche durch. Verbindungsfehler und
     * Zeitüberschreitungen (auch HttpRequestTimeoutException) sind IOExceptions und werden [ServerFehler.Offline];
     * alles andere wird [ServerFehler.Server] – ohne Ursache, denn deren Meldungen könnten Bodies enthalten.
     */
    private suspend fun <T> abgesichert(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ServerFehler) {
        throw e
    } catch (e: IOException) {
        throw ServerFehler.Offline(e)
    } catch (_: Exception) {
        throw ServerFehler.Server(0)
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

    /** Führt die Anfrage aus und liefert den Body (höchstens [MAX_ANTWORT_BYTES]) als Text; Fehlerstatus → [ServerFehler]. */
    private suspend fun anfragen(
        methode: HttpMethod,
        pfad: String,
        mitToken: Boolean = true,
        body: HttpRequestBuilder.() -> Unit = {},
    ): String =
        // prepareRequest/execute statt request: request würde den ganzen Body ungezählt in den Speicher lesen.
        client.prepareRequest(basis + pfad) {
            method = methode
            kopf(mitToken)
            body()
        }.execute { antwort ->
            val status = antwort.status.value
            if (status !in 200..299) throw fehlerFuer(status, fehlerCode(antwort.fehlerText()))
            antwort.begrenztLesen(MAX_ANTWORT_BYTES).decodeToString()
        }

    /** Fehler-Body für den Fehlercode: höchstens [MAX_FEHLER_BYTES], der Rest wird verworfen. */
    private suspend fun HttpResponse.fehlerText(): String = begrenztLesen(MAX_FEHLER_BYTES, abschneiden = true).decodeToString()

    /**
     * Gezähltes Lesen: nie mehr als [max] Bytes puffern, auch wenn die Länge fehlt oder nicht stimmt. Mehr als [max]
     * ist [ServerFehler.ZuGross]; mit [abschneiden] wird stattdessen nach [max] Bytes aufgehört.
     */
    private suspend fun HttpResponse.begrenztLesen(max: Long, abschneiden: Boolean = false): ByteArray {
        val angegeben = contentLength()
        if (!abschneiden && angegeben != null && angegeben > max) throw ServerFehler.ZuGross()
        val kanal = bodyAsChannel()
        val ausgabe = ByteArrayOutputStream()
        val puffer = ByteArray(8 * 1024)
        while (true) {
            // Höchstens ein Byte über die Grenze anfordern, damit „zu viel“ erkennbar ist.
            val wunsch = minOf(puffer.size.toLong(), max - ausgabe.size() + 1).toInt()
            val n = kanal.readAvailable(puffer, 0, wunsch)
            if (n < 0) break
            if (ausgabe.size() + n > max) {
                if (!abschneiden) throw ServerFehler.ZuGross()
                ausgabe.write(puffer, 0, (max - ausgabe.size()).toInt())
                break
            }
            ausgabe.write(puffer, 0, n)
        }
        return ausgabe.toByteArray()
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

    companion object {
        /**
         * Größte gelesene Antwort außer Fotos. Das Protokoll begrenzt nur Anfragen ([Protocol.MAX_PUSH_BYTES]); eine
         * Pull-Seite mit [Protocol.MAX_PULL_LIMIT] großen Rezepten bleibt weit darunter.
         */
        const val MAX_ANTWORT_BYTES = 32L * 1024 * 1024

        /** Von Fehler-Bodies wird nur so viel gelesen; der Fehlercode steht am Anfang. */
        const val MAX_FEHLER_BYTES = 4L * 1024
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
