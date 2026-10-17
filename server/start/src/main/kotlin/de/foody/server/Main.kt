package de.foody.server

import de.foody.server.admin.AdminCli
import de.foody.server.db.Database
import de.foody.server.sync.Compactor
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val config = ServerConfig.fromEnv()
    when (args.firstOrNull()) {
        null -> startServer(config)
        "admin" -> exitProcess(runAdmin(config, args.drop(1)))
        "healthcheck" -> exitProcess(if (healthcheck(config.port)) 0 else 1)
        else -> {
            System.err.println("Unbekannter Befehl: ${args.first()}")
            exitProcess(2)
        }
    }
}

private fun startServer(config: ServerConfig) {
    val deps = ServerDeps.create(config, Database("jdbc:sqlite:${config.dbPath}"))
    if (!bootstrapAdmin(config, deps)) exitProcess(1)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        foodyModule(deps)
        launchCompaction(deps)
    }.start(wait = true)
}

/** Führt einen Admin-Befehl direkt gegen die Datenbankdatei aus (ohne Netty); läuft auch parallel zum Server (WAL). */
private fun runAdmin(config: ServerConfig, args: List<String>): Int =
    Database("jdbc:sqlite:${config.dbPath}").use { db -> AdminCli(ServerDeps.create(config, db), System.out).run(args) }

/** Kompaktiert Löschmarkierungen beim Start und danach alle 24 Stunden; Fehler werden protokolliert, nie geworfen. */
private fun Application.launchCompaction(deps: ServerDeps) {
    val log = LoggerFactory.getLogger("de.foody.server.compaction")
    val compactor = Compactor(deps.db, deps.clock, deps.photos)
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                log.info("Kompaktierung: {} Löschmarkierungen entfernt", compactor.run())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Kompaktierung fehlgeschlagen", e)
            }
            delay(COMPACTION_INTERVAL_MS)
        }
    }
}

private const val COMPACTION_INTERVAL_MS = 24L * 60 * 60 * 1000

/** Legt beim ersten Start den Admin aus den Umgebungsvariablen an (nie wird das Passwort protokolliert). */
private fun bootstrapAdmin(config: ServerConfig, deps: ServerDeps): Boolean {
    val log = LoggerFactory.getLogger("de.foody.server")
    val created = try {
        deps.accounts.bootstrapAdmin(config.adminUser, config.adminPassword)
    } catch (_: ApiException) {
        // Ungültige Vorgabe: eine klare Zeile ohne Wert statt Stacktrace (sonst Crash-Schleife unter der Restart-Policy).
        log.error("FOODY_ADMIN_USER muss 3–32 Zeichen (A-Z a-z 0-9 . _ -) und FOODY_ADMIN_PASSWORD 10–200 Zeichen haben; Start abgebrochen")
        return false
    }
    if (created) {
        log.info("Admin-Benutzer '{}' angelegt", config.adminUser)
    } else if (config.adminUser.isNullOrBlank() || config.adminPassword.isNullOrEmpty()) {
        if (!deps.accounts.hasUsers()) {
            log.warn("Keine Benutzer vorhanden und FOODY_ADMIN_USER/FOODY_ADMIN_PASSWORD nicht gesetzt: Anmeldung unmöglich")
        }
    }
    return true
}

/** Fragt `/health` des lokal laufenden Servers ab (für den Docker-Healthcheck). */
private fun healthcheck(port: Int): Boolean = try {
    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health"))
        .timeout(Duration.ofSeconds(3)).GET().build()
    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
} catch (e: Exception) {
    false
}
