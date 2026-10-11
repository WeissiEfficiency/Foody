package de.foody.server

import de.foody.server.db.Database
import de.foody.sync.protocol.Protocol
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.server.application.Application
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
class TestEnv(val deps: ServerDeps, val clock: MutableClock, val photoDir: java.nio.file.Path)

/** Startet das Server-Modul mit `:memory:`-Datenbank und führt [block] aus. */
fun testServer(
    clock: MutableClock = MutableClock(),
    extraSetup: Application.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(TestEnv) -> Unit,
) {
    // Eigener Wurzelordner, damit Tests auch Dateien neben dem Foto-Ordner erkennen können.
    val tempRoot = java.nio.file.Files.createTempDirectory("foody-test")
    val photoDir = tempRoot.resolve("photos")
    val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null, photoDir = photoDir.toString())
    try {
        Database("jdbc:sqlite::memory:").use { db ->
            val deps = ServerDeps.create(config, db, clock)
            testApplication {
                application {
                    foodyModule(deps)
                    extraSetup()
                }
                block(TestEnv(deps, clock, photoDir))
            }
        }
    } finally {
        tempRoot.toFile().deleteRecursively()
    }
}

private val userCounter = java.util.concurrent.atomic.AtomicInteger()

/** Legt Benutzer, Gerät und Haushalt an; liefert (Gerätetoken, Haushalts-ID). */
fun TestEnv.setupHousehold(name: String = "Zuhause"): Pair<String, String> {
    val userId = deps.accounts.createUser("tester${userCounter.incrementAndGet()}", "geheimgeheim")
    val token = deps.accounts.createDevice(userId, null, "Testgerät")
    val deviceId = deps.accounts.deviceForToken(token)!!.deviceId
    return token to deps.accounts.createHousehold(userId, deviceId, name).id
}

/** Setzt den Protokoll-Header der aktuellen Version. */
fun HttpRequestBuilder.protocol(version: Int = Protocol.VERSION) {
    header(Protocol.HEADER, version.toString())
}
