package de.foody.server

import de.foody.server.db.Database
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = ServerConfig.fromEnv()
    when (args.firstOrNull()) {
        null -> startServer(config)
        "healthcheck" -> exitProcess(if (healthcheck(config.port)) 0 else 1)
        else -> {
            System.err.println("Unbekannter Befehl: ${args.first()}")
            exitProcess(2)
        }
    }
}

private fun startServer(config: ServerConfig) {
    val deps = ServerDeps.create(config, Database("jdbc:sqlite:${config.dbPath}"))
    bootstrapAdmin(config, deps)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { foodyModule(deps) }.start(wait = true)
}

/** Legt beim ersten Start den Admin aus den Umgebungsvariablen an (nie wird das Passwort protokolliert). */
private fun bootstrapAdmin(config: ServerConfig, deps: ServerDeps) {
    val log = LoggerFactory.getLogger("de.foody.server")
    if (deps.accounts.bootstrapAdmin(config.adminUser, config.adminPassword)) {
        log.info("Admin-Benutzer '{}' angelegt", config.adminUser)
    } else if (config.adminUser.isNullOrBlank() || config.adminPassword.isNullOrEmpty()) {
        if (!deps.accounts.hasUsers()) {
            log.warn("Keine Benutzer vorhanden und FOODY_ADMIN_USER/FOODY_ADMIN_PASSWORD nicht gesetzt: Anmeldung unmöglich")
        }
    }
}

/** Fragt `/health` des lokal laufenden Servers ab (für den Docker-Healthcheck). */
private fun healthcheck(port: Int): Boolean = try {
    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health"))
        .timeout(Duration.ofSeconds(3)).GET().build()
    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
} catch (e: Exception) {
    false
}
