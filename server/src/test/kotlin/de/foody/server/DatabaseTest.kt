package de.foody.server

import de.foody.sync.protocol.Protocol
import de.foody.server.db.Database
import de.foody.server.db.Migrations
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post as routePost
import io.ktor.server.routing.routing
import de.foody.sync.protocol.ErrorDto
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DatabaseTest {
    private fun Database.userVersion(): Int = tx { c ->
        c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
    }

    private fun Database.householdCount(): Int = tx { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM household").use { it.next(); it.getInt(1) } }
    }

    @Test
    fun migrationsRaiseUserVersionToLatest() {
        Database("jdbc:sqlite::memory:").use { db ->
            assertEquals(Migrations.all.size, db.userVersion())
            db.tx { Migrations.apply(it) }
            assertEquals(Migrations.all.size, db.userVersion())
        }
    }

    @Test
    fun txRollsBackOnException() {
        Database("jdbc:sqlite::memory:").use { db ->
            assertFailsWith<IllegalStateException> {
                db.tx { c ->
                    c.createStatement().use {
                        it.executeUpdate("INSERT INTO household(id, name, created_by, created_at) VALUES ('h', 'n', 'u', 0)")
                    }
                    error("boom")
                }
            }
            assertEquals(0, db.householdCount())
        }
    }

    @Test
    fun healthNeedsNoProtocolHeader() = testServer {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun apiWithoutHeaderIsProtocolTooOld() = testServer {
        val missing = client.get("/api/v1/households")
        assertEquals(HttpStatusCode.Conflict, missing.status)
        assertEquals("""{"code":"protocol_too_old"}""", missing.bodyAsText())

        val newer = client.get("/api/v1/households") { header("X-Foody-Protocol", (Protocol.VERSION + 1).toString()) }
        assertEquals(HttpStatusCode.Conflict, newer.status)
        assertEquals("""{"code":"server_too_old"}""", newer.bodyAsText())
    }

    @Test
    fun configDefaults() {
        val config = ServerConfig.fromEnv(emptyMap())
        assertEquals("/data/foody.db", config.dbPath)
        assertEquals(8080, config.port)
        assertNull(config.adminUser)
        assertNull(config.adminPassword)
    }

    @Test
    fun clientBodyErrorsAreNot500() = testServer(extraSetup = {
        routing {
            routePost("/test/echo") {
                call.receive<ErrorDto>()
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }) {
        val malformed = client.post("/test/echo") {
            contentType(ContentType.Application.Json)
            setBody("{kaputt")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("""{"code":"invalid_input"}""", malformed.bodyAsText())

        val wrongType = client.post("/test/echo") {
            contentType(ContentType.Text.Plain)
            setBody("x")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, wrongType.status)
        assertEquals("""{"code":"invalid_input"}""", wrongType.bodyAsText())
    }
}
