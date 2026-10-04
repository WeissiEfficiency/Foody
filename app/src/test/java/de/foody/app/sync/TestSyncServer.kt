package de.foody.app.sync

import de.foody.server.ServerConfig
import de.foody.server.ServerDeps
import de.foody.server.db.Database
import de.foody.server.foodyModule
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Steuerbare Uhr für den Test-Server (Kompaktierung braucht „später“). */
class TestClock(var now: Instant = Instant.parse("2026-10-04T10:00:00Z")) : Clock() {
    fun advance(d: Duration) {
        now = now.plus(d)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

/** Zugangsdaten des beim Start angelegten Admins. */
const val TEST_ADMIN_USER = "admin"
const val TEST_ADMIN_PASSWORD = "geheimgeheim"

/**
 * Startet den echten Server (`foodyModule` aus `:server`) in-process mit `:memory:`-Datenbank und einem Admin
 * ([TEST_ADMIN_USER]/[TEST_ADMIN_PASSWORD]). Der `client` des [ApplicationTestBuilder] spricht ohne Netzwerk mit ihm.
 */
fun syncServerTest(
    clock: TestClock = TestClock(),
    block: suspend ApplicationTestBuilder.(ServerDeps, TestClock) -> Unit,
) {
    // Der Treiber muss im ClassLoader dieses Tests registriert sein: Läuft zuvor ein Robolectric-Test (eigener
    // ClassLoader) in derselben JVM, hat nur dessen Kopie sich bei DriverManager angemeldet.
    Class.forName("org.sqlite.JDBC")
    val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null)
    Database("jdbc:sqlite::memory:").use { db ->
        val deps = ServerDeps.create(config, db, clock)
        check(deps.accounts.bootstrapAdmin(TEST_ADMIN_USER, TEST_ADMIN_PASSWORD)) { "Admin-Bootstrap fehlgeschlagen" }
        testApplication {
            application { foodyModule(deps) }
            block(deps, clock)
        }
    }
}
