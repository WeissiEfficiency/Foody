package de.foody.core.sync

import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.IngredientPayload
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.PushStatus
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.server.sync.Compactor
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class ServerZugangTest {
    private fun zutat(id: String, name: String = "Mehl") = SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 1,
        payload = Protocol.json.encodeToJsonElement(IngredientPayload(name = name)) as JsonObject,
    )

    private fun loeschung(id: String) = SyncRecord(id, RecordType.INGREDIENT, deleted = true, updatedAt = 2)

    private fun ApplicationTestBuilder.zugang(token: () -> String? = { null }) = KtorServerZugang(TEST_BASIS, client, token)

    /** Ersatz-Server mit festen Antworten (für Zustände, die der echte Server dem Client nicht liefert). */
    private fun ersatzServer(routen: Route.() -> Unit, block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            routing(routen)
            block()
        }

    @Test
    fun anmeldenLiefertKontoId() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        val antwort = zugang().anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät"))
        assertEquals(kontoId, antwort.kontoId)
        assertTrue(antwort.token.isNotBlank())
    }

    @Test
    fun headerDreiWirdGesendet() {
        var protokoll: String? = null
        var anmeldung: String? = null
        var loginAnmeldung: String? = "nicht aufgerufen"
        ersatzServer({
            get("/api/v3/sync/pull") {
                protokoll = call.request.headers[Protocol.HEADER]
                anmeldung = call.request.headers[HttpHeaders.Authorization]
                assertEquals("7", call.request.queryParameters["since"])
                assertEquals("50", call.request.queryParameters["limit"])
                call.respondText(
                    Protocol.json.encodeToString(PullResponse.serializer(), PullResponse(emptyList(), 7, false)),
                    ContentType.Application.Json,
                )
            }
            post("/api/v3/auth/login") {
                loginAnmeldung = call.request.headers[HttpHeaders.Authorization]
                call.respondText("""{"token":"t","kontoId":"k"}""", ContentType.Application.Json)
            }
        }) {
            val zugang = zugang { "mein-token" }
            assertEquals(7, zugang.pull(since = 7, limit = 50).nextCursor)
            assertEquals(Protocol.VERSION.toString(), protokoll)
            assertEquals("3", protokoll)
            assertEquals("Bearer mein-token", anmeldung)
            zugang.anmelden(LoginRequest("a", "b", "c"))
            // Die Anmeldung schickt nie ein (altes) Token mit.
            assertNull(loginAnmeldung)
        }
    }

    @Test
    fun datenUndFotosLaufenUeberDenServer() = syncServerTest { server ->
        val (name, _) = server.kontoAnlegen()
        var token: String? = null
        val zugang = zugang { token }
        token = zugang.anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät")).token

        assertEquals(PushStatus.ACCEPTED, zugang.push(listOf(zutat("z1"))).results.single().status)
        val geholt = zugang.pull(since = 0, limit = 500)
        assertEquals(listOf("z1"), geholt.records.map { it.id })
        assertFalse(geholt.hasMore)

        val foto = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
        val hash = PhotoHash.of(foto)
        assertEquals(listOf(hash), zugang.fotosFehlend(listOf(hash)))
        zugang.fotoHochladen(hash, foto)
        assertEquals(emptyList(), zugang.fotosFehlend(listOf(hash)))
        assertContentEquals(foto, zugang.fotoLaden(hash))
        // Unbekanntes Foto: kein Fehler, sondern null
        assertNull(zugang.fotoLaden("0".repeat(64)))

        // Abmelden widerruft das Token: danach 401
        zugang.abmelden()
        assertFailsWith<ServerFehler.AnmeldungAbgelaufen> { zugang.pull(0, 10) }
    }

    @Test
    fun fehlerWerdenZugeordnet() {
        syncServerTest { server ->
            // 401: ungültiges Token
            val fremd = zugang { "kein-gueltiges-token" }
            assertEquals("anmeldung_abgelaufen", assertFailsWith<ServerFehler.AnmeldungAbgelaufen> { fremd.pull(0, 10) }.kennung)

            // 401 bei der Anmeldung: falsches Passwort, mit Code
            val (name, _) = server.kontoAnlegen()
            val falsch = assertFailsWith<ServerFehler.AnmeldungAbgelaufen> {
                zugang().anmelden(LoginRequest(name, "falsch-falsch", "Testgerät"))
            }
            assertEquals(ErrorCode.INVALID_CREDENTIALS, falsch.code)

            var token: String? = null
            val zugang = zugang { token }
            token = zugang.anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät")).token

            // 413: mehr als MAX_PUSH_RECORDS Datensätze
            assertFailsWith<ServerFehler.ZuGross> {
                zugang.push((0..Protocol.MAX_PUSH_RECORDS).map { zutat("z$it") })
            }

            // 400: sonstiger Client-Fehler
            val abgelehnt = assertFailsWith<ServerFehler.Abgelehnt> { zugang.pull(since = -1, limit = 10) }
            assertEquals(400, abgelehnt.status)
            assertEquals(ErrorCode.INVALID_INPUT, abgelehnt.code)

            // 410: Cursor hinter der Kompaktierung
            zugang.push(listOf(zutat("z1")))
            zugang.push(listOf(loeschung("z1")))
            server.uhr.vorstellen(Duration.ofDays(91))
            assertEquals(1, Compactor(server.deps.db, server.uhr).run())
            assertFailsWith<ServerFehler.CursorAbgelaufen> { zugang.pull(since = 1, limit = 10) }
        }

        // Zustände, die der echte Server diesem Client nicht liefert
        ersatzServer({
            get("/api/v3/sync/pull") {
                when (call.request.queryParameters["since"]) {
                    "1" -> call.respondText("""{"code":"protocol_too_old"}""", ContentType.Application.Json, HttpStatusCode.Conflict)
                    "2" -> call.respondText("""{"code":"server_too_old"}""", ContentType.Application.Json, HttpStatusCode.Conflict)
                    "3" -> call.respondText("Interner Fehler", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                    "4" -> call.respondText("<html>Proxy</html>", ContentType.Text.Html, HttpStatusCode.BadGateway)
                    "5" -> call.respondText("<html>kein JSON</html>", ContentType.Text.Html, HttpStatusCode.OK)
                    "6" -> call.respondText("", ContentType.Text.Plain, HttpStatusCode.TooManyRequests)
                }
            }
            // Alles andere unter /api/v3: 404 wie bei einem Server ohne Protokoll 3
        }) {
            val zugang = zugang { "t" }
            assertEquals("app_zu_alt", assertFailsWith<ServerFehler.AppZuAlt> { zugang.pull(1, 10) }.kennung)
            assertEquals("server_zu_alt", assertFailsWith<ServerFehler.ServerZuAlt> { zugang.pull(2, 10) }.kennung)
            assertEquals(500, assertFailsWith<ServerFehler.Server> { zugang.pull(3, 10) }.status)
            assertEquals(502, assertFailsWith<ServerFehler.Server> { zugang.pull(4, 10) }.status)
            // Antwort ohne lesbares JSON (z. B. Anmeldeseite eines WLANs) ist ein Serverfehler
            assertEquals("server_fehler", assertFailsWith<ServerFehler.Server> { zugang.pull(5, 10) }.kennung)
            assertEquals(429, assertFailsWith<ServerFehler.Abgelehnt> { zugang.pull(6, 10) }.status)
            // 404 auf /api/v3 – auch beim Foto, wenn der Server kein `not_found` liefert
            assertFailsWith<ServerFehler.ServerZuAlt> { zugang.push(listOf(zutat("z1"))) }
            assertFailsWith<ServerFehler.ServerZuAlt> { zugang.anmelden(LoginRequest("a", "b", "c")) }
            assertFailsWith<ServerFehler.ServerZuAlt> { zugang.fotoLaden("0".repeat(64)) }
        }
    }

    @Test
    fun kennungenSindNurDieFestenWerte() {
        val erlaubt = setOf("offline", "anmeldung_abgelaufen", "app_zu_alt", "server_zu_alt", "cursor_haengt", "server_fehler")
        val alle = listOf(
            ServerFehler.Offline(), ServerFehler.AnmeldungAbgelaufen(null), ServerFehler.AppZuAlt(), ServerFehler.ServerZuAlt(),
            ServerFehler.CursorAbgelaufen(), ServerFehler.ZuGross(), ServerFehler.Abgelehnt(400, null), ServerFehler.Server(500),
        )
        for (fehler in alle) assertTrue(fehler.kennung in erlaubt, "unerwartete Kennung ${fehler.kennung}")
        assertEquals("offline", ServerFehler.Offline().kennung)
        assertEquals("server_fehler", ServerFehler.Server(500).kennung)
    }

    @Test
    fun netzfehlerIstOffline() = runBlocking<Unit> {
        // Nichts lauscht auf Port 1: Verbindungsfehler
        standardHttpClient().use { client ->
            val zugang = KtorServerZugang("http://127.0.0.1:1", client) { "t" }
            assertEquals("offline", assertFailsWith<ServerFehler.Offline> { zugang.pull(0, 10) }.kennung)
            assertFailsWith<ServerFehler.Offline> { zugang.anmelden(LoginRequest("a", "b", "c")) }
            assertFailsWith<ServerFehler.Offline> { zugang.fotoLaden("0".repeat(64)) }
            assertFailsWith<ServerFehler.Offline> { zugang.fotoHochladen("0".repeat(64), byteArrayOf(1)) }
        }
    }

    /** Antwort aus [bytes] Bytes ohne Content-Length (chunked), damit nur das gezählte Lesen greift. */
    private suspend fun ApplicationCall.grosseAntwort(status: HttpStatusCode, anfang: String, bytes: Long) {
        respondBytesWriter(ContentType.Application.Json, status) {
            val kopf = anfang.toByteArray()
            writeFully(kopf)
            val block = ByteArray(64 * 1024) { ' '.code.toByte() }
            var rest = bytes - kopf.size
            while (rest > 0) {
                val n = minOf(rest, block.size.toLong()).toInt()
                writeFully(block, 0, n)
                rest -= n
            }
        }
    }

    @Test
    fun antwortenWerdenBegrenztGelesen() {
        val geheim = "GEHEIMES-TOKEN-4711"
        val grenze = KtorServerZugang.MAX_ANTWORT_BYTES
        ersatzServer({
            get("/api/v3/sync/pull") {
                when (call.request.queryParameters["since"]) {
                    // Normale Antwort über der Obergrenze, ohne und mit Content-Length
                    "1" -> call.grosseAntwort(HttpStatusCode.OK, """{"records":[],"nextCursor":0,"hasMore":false}""", grenze + 1)
                    "2" -> call.respondText(" ".repeat((grenze + 1).toInt()), ContentType.Application.Json)
                    // Genau an der Grenze: wird noch gelesen
                    "3" -> call.grosseAntwort(HttpStatusCode.OK, """{"records":[],"nextCursor":9,"hasMore":false}""", grenze)
                    // Große Fehler-Bodies: nur der Anfang zählt, das Token weiter hinten wird nie gelesen
                    "4" -> call.grosseAntwort(HttpStatusCode.Conflict, """{"code":"protocol_too_old"}""" + " ".repeat(5000) + geheim, 8L * 1024 * 1024)
                    "5" -> call.grosseAntwort(HttpStatusCode.InternalServerError, geheim, 8L * 1024 * 1024)
                }
            }
            get("/api/v3/photos/{sha}") {
                call.grosseAntwort(HttpStatusCode.OK, "", Protocol.MAX_PHOTO_BYTES + 1)
            }
        }) {
            val zugang = zugang { "t" }
            assertFailsWith<ServerFehler.ZuGross> { zugang.pull(1, 10) }
            assertFailsWith<ServerFehler.ZuGross> { zugang.pull(2, 10) }
            assertEquals(9, zugang.pull(3, 10).nextCursor)
            val fehler = listOf(
                assertFailsWith<ServerFehler.AppZuAlt> { zugang.pull(4, 10) },
                assertFailsWith<ServerFehler.Server> { zugang.pull(5, 10) },
            )
            for (f in fehler) assertFalse(f.stackTraceToString().contains(geheim))
            assertFailsWith<ServerFehler.ZuGross> { zugang.fotoLaden("0".repeat(64)) }
        }
    }

    @Test
    fun weiterleitungWirdNichtGefolgt() = runBlocking<Unit> {
        // Ziel auf eigenem Port (anderer Ursprung): zählt Aufrufe und merkt sich das mitgeschickte Token
        val zielAufrufe = AtomicInteger()
        var zielAnmeldung: String? = null
        val ziel = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            routing {
                route("{...}") {
                    handle {
                        zielAufrufe.incrementAndGet()
                        zielAnmeldung = call.request.headers[HttpHeaders.Authorization]
                        call.respondText("""{"records":[],"nextCursor":0,"hasMore":false}""", ContentType.Application.Json)
                    }
                }
            }
        }.start(wait = false)
        try {
            val zielPort = ziel.engine.resolvedConnectors().first().port
            val quelle = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing {
                    get("/api/v3/sync/pull") {
                        call.response.header(HttpHeaders.Location, "http://127.0.0.1:$zielPort/api/v3/sync/pull?since=0&limit=10")
                        call.respond(HttpStatusCode.Found)
                    }
                }
            }.start(wait = false)
            try {
                val basis = "http://127.0.0.1:${quelle.engine.resolvedConnectors().first().port}"

                // Gegenprobe: ein Client, der Weiterleitungen folgt, erreicht das Ziel (Ktor lässt dabei das
                // Authorization-Header bei fremdem Ursprung weg – darauf verlassen wir uns nicht).
                HttpClient(OkHttp).use { folgend ->
                    KtorServerZugang(basis, folgend) { "geheim" }.pull(0, 10)
                }
                assertEquals(1, zielAufrufe.get())
                zielAnmeldung = null

                // Die echte Konfiguration folgt nicht: 302 ist ein Fehler, das Ziel sieht nichts
                standardHttpClient().use { client ->
                    val fehler = assertFailsWith<ServerFehler.Abgelehnt> {
                        KtorServerZugang(basis, client) { "geheim" }.pull(0, 10)
                    }
                    assertEquals(302, fehler.status)
                }
                assertEquals(1, zielAufrufe.get())
                assertNull(zielAnmeldung)
            } finally {
                quelle.stop(0, 0)
            }
        } finally {
            ziel.stop(0, 0)
        }
    }

    @Test
    fun unerwarteteAusnahmenWerdenServerfehler() = testApplication {
        val geheim = "GEHEIMES-TOKEN-4711"
        val wirft = createClient {
            install(createClientPlugin("Wurf") { onRequest { _, _ -> throw IllegalStateException("kaputt $geheim") } })
        }
        val zugang = KtorServerZugang(TEST_BASIS, wirft) { geheim }
        val fehler = listOf(
            assertFailsWith<ServerFehler.Server> { zugang.pull(0, 10) },
            assertFailsWith<ServerFehler.Server> { zugang.anmelden(LoginRequest("a", "b", "c")) },
            assertFailsWith<ServerFehler.Server> { zugang.fotoLaden("0".repeat(64)) },
            assertFailsWith<ServerFehler.Server> { zugang.fotoHochladen("0".repeat(64), byteArrayOf(1)) },
        )
        for (f in fehler) {
            assertEquals(0, f.status)
            assertEquals("server_fehler", f.kennung)
            assertNull(f.cause)
            assertFalse(f.stackTraceToString().contains(geheim))
        }

        // Coroutine-Abbrüche laufen unverändert durch
        val abbruch = createClient {
            install(createClientPlugin("Abbruch") { onRequest { _, _ -> throw CancellationException("abgebrochen") } })
        }
        assertFailsWith<CancellationException> { KtorServerZugang(TEST_BASIS, abbruch) { null }.pull(0, 10) }
    }

    @Test
    fun keinTokenInFehlermeldung() {
        val geheim = "GEHEIMES-TOKEN-4711"
        ersatzServer({
            get("/api/v3/sync/pull") {
                when (call.request.queryParameters["since"]) {
                    "1" -> call.respondText("""{"code":"unauthorized","token":"$geheim"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    "2" -> call.respondText("Fehler mit $geheim", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                    "3" -> call.respondText("""{"records":"$geheim"}""", ContentType.Application.Json, HttpStatusCode.OK)
                    "4" -> call.respondText("""{"code":"$geheim"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
            // Anmeldung mit abgeschnittener Antwort: der Decoder schreibt Ausschnitte (das Token) in seine Meldung
            post("/api/v3/auth/login") {
                call.respondText("""{"token":"$geheim","kontoId":""", ContentType.Application.Json)
            }
        }) {
            val zugang = zugang { geheim }
            val fehler = buildList {
                for (since in 1L..4L) add(assertFailsWith<ServerFehler> { zugang.pull(since, 10) })
                add(assertFailsWith<ServerFehler> { zugang.anmelden(LoginRequest("a", "b", "c")) })
            }
            for (f in fehler) {
                var aktuell: Throwable? = f
                while (aktuell != null) {
                    assertFalse(aktuell.toString().contains(geheim), "Token in ${f::class.simpleName}: $aktuell")
                    assertFalse(aktuell.stackTraceToString().contains(geheim), "Token im Stacktrace von ${f::class.simpleName}")
                    aktuell = aktuell.cause
                }
            }
        }
    }
}
