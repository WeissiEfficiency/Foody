package de.foody.server

import de.foody.server.db.Database
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** Zusätzliche Routen (z. B. die Web-Oberfläche) docken an, ohne dass der Sync-Server sie kennt. */
class ErweiterungTest {
    private fun mitServer(erweiterungen: List<Route.() -> Unit>?, block: suspend ApplicationTestBuilder.() -> Unit) {
        val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null,
            photoDir = java.nio.file.Files.createTempDirectory("foody-erw").toString())
        Database("jdbc:sqlite::memory:").use { db ->
            val deps = ServerDeps.create(config, db, MutableClock())
            testApplication {
                application { if (erweiterungen == null) foodyModule(deps) else foodyModule(deps, erweiterungen) }
                block()
            }
        }
    }

    @Test fun erweiterungWirdEingehaengt() = mitServer(listOf({ get("/probe") { call.respondText("ok") } })) {
        val antwort = client.get("/probe")
        assertEquals(HttpStatusCode.OK, antwort.status)
        assertEquals("ok", antwort.bodyAsText())
    }

    @Test fun ohneErweiterungBleibtAllesWieHeute() = mitServer(null) {
        assertEquals(HttpStatusCode.NotFound, client.get("/probe").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
}
