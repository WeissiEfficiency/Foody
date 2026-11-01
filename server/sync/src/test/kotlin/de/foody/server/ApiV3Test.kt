package de.foody.server

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// API v3 auf dem Draht: Pfade, Protokoll-Header, Login-Antwort, Abmelden, zwei Geräte eines Kontos.
class ApiV3Test {
    private fun feld(text: String, name: String): String =
        Json.parseToJsonElement(text).jsonObject.getValue(name).jsonPrimitive.content

    private suspend fun HttpResponse.drahtCode(): String = feld(bodyAsText(), "code")

    private suspend fun HttpClient.login(username: String, pfad: String = "/api/v3/auth/login", version: Int = 3) =
        post(pfad) {
            protocol(version)
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"geheimgeheim","deviceName":"Testgerät"}""")
        }

    @Test
    fun loginLiefertKontoId() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("anna", "geheimgeheim")
        val response = client.login("anna")
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertEquals(kontoId, feld(text, "kontoId"))
        assertTrue(feld(text, "token").isNotBlank())
    }

    @Test
    fun alterPfadGibtEsNichtMehr() = testServer {
        // Seit dem Schließen des Rückwegs (C4) antwortet unter /api/v1 nichts mehr.
        assertEquals(HttpStatusCode.NotFound, client.login("niemand", pfad = "/api/v1/auth/login", version = 2).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/sync/pull?since=0") { protocol(2); bearerAuth("falsch") }.status)
    }

    @Test
    fun alterHeaderZuAlt() = testServer { env ->
        val (token, _) = env.setupKonto()
        val response = client.get("/api/v3/sync/pull?since=0") { protocol(2); bearerAuth(token) }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("protocol_too_old", response.drahtCode())
    }

    @Test
    fun neuererHeaderServerZuAlt() = testServer { env ->
        val (token, _) = env.setupKonto()
        val response = client.get("/api/v3/sync/pull?since=0") { protocol(4); bearerAuth(token) }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("server_too_old", response.drahtCode())
    }

    @Test
    fun abmeldenWiderruftToken() = testServer { env ->
        val (token, _) = env.setupKonto()
        val abmelden = client.post("/api/v3/auth/abmelden") { protocol(3); bearerAuth(token) }
        assertEquals(HttpStatusCode.NoContent, abmelden.status)
        val pull = client.get("/api/v3/sync/pull?since=0") { protocol(3); bearerAuth(token) }
        assertEquals(HttpStatusCode.Unauthorized, pull.status)
        assertEquals("unauthorized", pull.drahtCode())
    }

    @Test
    fun zweiGeraeteSehenEinander() = testServer { env ->
        env.deps.accounts.createUser("bert", "geheimgeheim")
        val a = feld(client.login("bert").bodyAsText(), "token")
        val b = feld(client.login("bert").bodyAsText(), "token")

        client.pushOk(a, listOf(ingredient("i1", "Mehl")))
        val beiB = client.pull(b, since = 0)
        assertTrue(beiB.records.any { it.id == "i1" })

        val standA = client.pull(a, since = 0).nextCursor
        client.pushOk(b, listOf(ingredient("i2", "Zucker")))
        val beiA = client.pull(a, since = standA)
        assertEquals(listOf("i2"), beiA.records.map { it.id })
    }

    @Test
    fun vorratWirdAbgelehnt() = testServer { env ->
        val (token, _) = env.setupKonto()
        val response = client.post("/api/v3/sync/push") {
            protocol(3)
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"records":[{"id":"p1","type":"pantry_item","updatedAt":1,"deleted":false,""" +
                    """"payload":{"ingredientId":"i1","amount":"1","unit":"PIECE"}}]}""",
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_input", response.drahtCode())
    }
}
