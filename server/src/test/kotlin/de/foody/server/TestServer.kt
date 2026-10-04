package de.foody.server

import de.foody.server.db.Database
import de.foody.sync.protocol.Protocol
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Steuerbare Uhr für Tests. */
class MutableClock(var now: Instant = Instant.parse("2026-10-04T10:00:00Z")) : Clock() {
    fun advance(d: Duration) {
        now = now.plus(d)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/** Testumgebung: Abhängigkeiten (Datenbank, Uhr) für direkte Zugriffe aus Tests. */
class TestEnv(val deps: ServerDeps, val clock: MutableClock)

/** Startet das Server-Modul mit `:memory:`-Datenbank und führt [block] aus. */
fun testServer(
    clock: MutableClock = MutableClock(),
    block: suspend ApplicationTestBuilder.(TestEnv) -> Unit,
) {
    val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null)
    Database("jdbc:sqlite::memory:").use { db ->
        val deps = ServerDeps.create(config, db, clock)
        testApplication {
            application { foodyModule(deps) }
            block(TestEnv(deps, clock))
        }
    }
}

/** Setzt den Protokoll-Header der aktuellen Version. */
fun HttpRequestBuilder.protocol(version: Int = Protocol.VERSION) {
    header(Protocol.HEADER, version.toString())
}
