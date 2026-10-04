package de.foody.server

import de.foody.server.admin.AdminCli
import de.foody.server.db.Database
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminCliTest {
    private fun cli(deps: ServerDeps, args: List<String>): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val code = PrintStream(buffer, true, Charsets.UTF_8).use { AdminCli(deps, it).run(args) }
        return code to buffer.toString(Charsets.UTF_8)
    }

    private suspend fun HttpClient.login(user: String, password: String): HttpResponse =
        post("/api/v1/auth/login") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(LoginRequest.serializer(), LoginRequest(user, password, "Handy")))
        }

    @Test
    fun resetPasswordPrintsWorkingPasswordAndRevokesDevices() = testServer { env ->
        val userId = env.deps.accounts.createUser("stefan", "altaltaltalt")
        val oldToken = env.deps.accounts.createDevice(userId, null, "Handy")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/devices") { protocol(); bearerAuth(oldToken) }.status)

        val (code, output) = cli(env.deps, listOf("reset-password", "STEFAN"))
        assertEquals(0, code)
        val password = output.trim()
        assertEquals(20, password.length)
        assertTrue(password.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' })

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/devices") { protocol(); bearerAuth(oldToken) }.status)
        assertEquals(HttpStatusCode.OK, client.login("stefan", password).status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", "altaltaltalt").status)
    }

    @Test
    fun resetPasswordUnknownUserFails() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("reset-password", "niemand"))
        assertEquals(1, code)
        assertEquals(1, output.trim().lines().size)
    }

    @Test
    fun inviteCreatesAccountInvite() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("invite"))
        assertEquals(0, code)
        val response = client.post("/api/v1/auth/register") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(RegisterRequest.serializer(), RegisterRequest(output.trim(), "ben", "geheimgeheim", "Handy")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(Protocol.json.decodeFromString(AuthResponse.serializer(), response.bodyAsText()).householdId)
    }

    @Test
    fun backupWritesConsistentCopy() {
        val dir = Files.createTempDirectory("foody-backup")
        try {
            val config = ServerConfig(dbPath = dir.resolve("foody.db").toString(), port = 0, adminUser = null, adminPassword = null)
            Database("jdbc:sqlite:${config.dbPath}").use { db ->
                val deps = ServerDeps.create(config, db)
                deps.accounts.createUser("stefan", "geheimgeheim")
                deps.accounts.createUser("anna", "geheimgeheim")
                val target = dir.resolve("copy.db")
                val (code, output) = cli(deps, listOf("backup", target.toString()))
                assertEquals(0, code)
                assertTrue(output.contains(target.toString()))
                Database("jdbc:sqlite:$target").use { copy ->
                    val count = copy.tx { c -> c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM user").use { it.next(); it.getInt(1) } } }
                    assertEquals(2, count)
                }

                val before = Files.readAllBytes(target)
                val (code2, _) = cli(deps, listOf("backup", target.toString()))
                assertEquals(1, code2)
                assertTrue(before.contentEquals(Files.readAllBytes(target)))
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun backupIntoMissingDirectoryReportsOneLineError() = testServer { env ->
        val target = Files.createTempDirectory("foody-missing").resolve("nope").resolve("x.db")
        val (code, output) = cli(env.deps, listOf("backup", target.toString()))
        assertEquals(1, code)
        assertEquals(1, output.trim().lines().size)
    }

    @Test
    fun compactPrintsCount() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("compact"))
        assertEquals(0, code)
        assertEquals("0", output.trim())
    }

    @Test
    fun unknownCommandShowsHelp() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("unsinn"))
        assertEquals(2, code)
        for (name in listOf("reset-password", "invite", "backup", "compact")) assertTrue(output.contains(name), name)
        assertEquals(2, cli(env.deps, emptyList()).first)
        assertEquals(2, cli(env.deps, listOf("backup")).first)
    }
}
