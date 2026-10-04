package de.foody.server

import de.foody.server.db.Database
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
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
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { foodyModule(deps) }.start(wait = true)
}

/** Fragt `/health` des lokal laufenden Servers ab (für den Docker-Healthcheck). */
private fun healthcheck(port: Int): Boolean = try {
    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health"))
        .timeout(Duration.ofSeconds(3)).GET().build()
    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
} catch (e: Exception) {
    false
}
