package de.foody.core.sync

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
import java.util.concurrent.atomic.AtomicInteger

/** Steuerbare Uhr für den Test-Server (Kompaktierung braucht „später“). */
class TestUhr(var jetzt: Instant = Instant.parse("2026-10-18T10:00:00Z")) : Clock() {
    fun vorstellen(dauer: Duration) {
        jetzt = jetzt.plus(dauer)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = jetzt
}

/** Laufender Test-Server: Abhängigkeiten für direkte Zugriffe und die Uhr. */
class TestServer(val deps: ServerDeps, val uhr: TestUhr) {
    /** Legt ein Konto an; liefert (Benutzername, Konto-ID). Passwort: [TEST_PASSWORT]. */
    fun kontoAnlegen(): Pair<String, String> {
        val name = "tester${zaehler.incrementAndGet()}"
        return name to deps.accounts.createUser(name, TEST_PASSWORT)
    }

    private companion object {
        val zaehler = AtomicInteger()
    }
}

const val TEST_PASSWORT = "geheimgeheim"

/** Basis-Adresse, unter der der `client` einer `testApplication` den Server erreicht. */
const val TEST_BASIS = "http://localhost"

/**
 * Startet den echten Sync-Server (`foodyModule` aus `:server-sync`) in-process mit `:memory:`-Datenbank.
 * Der `client` des [ApplicationTestBuilder] spricht ohne Netzwerk mit ihm.
 */
fun syncServerTest(
    uhr: TestUhr = TestUhr(),
    block: suspend ApplicationTestBuilder.(TestServer) -> Unit,
) {
    // Der Treiber muss im ClassLoader dieses Tests registriert sein: Läuft zuvor ein Robolectric-Test (eigener
    // ClassLoader) in derselben JVM, hat nur dessen Kopie sich bei DriverManager angemeldet.
    Class.forName("org.sqlite.JDBC")
    val fotoOrdner = java.nio.file.Files.createTempDirectory("foody-fotos")
    val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null, photoDir = fotoOrdner.toString())
    try {
        Database("jdbc:sqlite::memory:").use { db ->
            val deps = ServerDeps.create(config, db, uhr)
            testApplication {
                application { foodyModule(deps) }
                block(TestServer(deps, uhr))
            }
        }
    } finally {
        fotoOrdner.toFile().deleteRecursively()
    }
}
