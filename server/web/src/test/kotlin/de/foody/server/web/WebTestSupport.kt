package de.foody.server.web

import de.foody.server.ServerConfig
import de.foody.server.ServerDeps
import de.foody.server.db.Database
import de.foody.server.foodyModule
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

internal class FesteUhr(var jetzt: Instant = Instant.parse("2026-10-11T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = jetzt
}

internal class WebUmgebung(val deps: ServerDeps, val uhr: FesteUhr) {
    private val zaehler = AtomicInteger()

    /** Benutzer (Passwort „geheimgeheim“), App-Gerät und Haushalt; liefert (Benutzername, App-Token, Haushalts-ID). */
    fun haushalt(name: String = "Zuhause"): Triple<String, String, String> {
        val benutzer = "web${zaehler.incrementAndGet()}"
        val userId = deps.accounts.createUser(benutzer, "geheimgeheim")
        val token = deps.accounts.createDevice(userId, null, "Testhandy")
        val deviceId = deps.accounts.deviceForToken(token)!!.deviceId
        return Triple(benutzer, token, deps.accounts.createHousehold(userId, deviceId, name).id)
    }

    fun push(household: String, vararg records: SyncRecord) = deps.sync.push(household, records.toList())
}

/** Server mit Web-Oberfläche und `:memory:`-Datenbank, wie `TestServer.kt` im Sync-Modul. */
internal fun webServer(block: suspend ApplicationTestBuilder.(WebUmgebung) -> Unit) {
    val fotos = java.nio.file.Files.createTempDirectory("foody-web-test")
    val config = ServerConfig(dbPath = ":memory:", port = 0, adminUser = null, adminPassword = null, photoDir = fotos.toString())
    try {
        Database("jdbc:sqlite::memory:").use { db ->
            val uhr = FesteUhr()
            val deps = ServerDeps.create(config, db, uhr)
            testApplication {
                application { foodyModule(deps, WebOberflaeche.routen(deps)) }
                block(WebUmgebung(deps, uhr))
            }
        }
    } finally {
        fotos.toFile().deleteRecursively()
    }
}

internal inline fun <reified T> datensatz(id: String, type: RecordType, payload: T, updatedAt: Long = 1) =
    SyncRecord(id = id, type = type, updatedAt = updatedAt, payload = Protocol.json.encodeToJsonElement(payload) as JsonObject)

/** Reis 1464 kJ je 100 g – wie in den App-Tests (200 g für 2 Portionen ≈ 350 kcal je Portion). */
internal fun reis() = datensatz("reis", RecordType.INGREDIENT, IngredientPayload(name = "Reis", basis = "PER_100_G", energyKj = "1464"))

internal fun rezept(id: String, name: String, reisGramm: String = "200", portionen: Int = 2, tags: String = "", archiviert: Boolean = false) =
    datensatz(id, RecordType.RECIPE, RecipePayload(
        name = name, servings = portionen, tags = tags, favorite = false, archivedAt = if (archiviert) 1L else null,
        lines = listOf(RecipePayload.Line("$id-l1", "reis", reisGramm, "GRAM", 0, null, false)),
        steps = listOf(RecipePayload.Step("$id-s1", 0, "Reis kochen")),
    ))
