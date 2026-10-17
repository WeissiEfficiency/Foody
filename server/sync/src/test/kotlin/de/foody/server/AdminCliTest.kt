package de.foody.server

import de.foody.server.admin.AdminCli
import de.foody.server.db.Database
import de.foody.server.db.Migrations
import de.foody.api.protocol.AuthResponse
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.Protocol
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
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminCliTest {
    private fun cli(deps: ServerDeps, args: List<String>): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val code = PrintStream(buffer, true, Charsets.UTF_8).use { AdminCli(deps, it).run(args) }
        return code to buffer.toString(Charsets.UTF_8)
    }

    /** Wie [cli], liefert aber die Fehlerausgabe (stderr) statt der normalen Ausgabe; stdout muss dabei leer bleiben. */
    private fun cliErr(deps: ServerDeps, args: List<String>): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = PrintStream(out, true, Charsets.UTF_8).use { o ->
            PrintStream(err, true, Charsets.UTF_8).use { e -> AdminCli(deps, o, e).run(args) }
        }
        assertEquals("", out.toString(Charsets.UTF_8))
        return code to err.toString(Charsets.UTF_8)
    }

    private suspend fun HttpClient.login(user: String, password: String): HttpResponse =
        post("/api/v3/auth/login") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(LoginRequest.serializer(), LoginRequest(user, password, "Handy")))
        }

    @Test
    fun resetPasswordPrintsWorkingPasswordAndRevokesDevices() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", "altaltaltalt")
        val oldToken = env.deps.accounts.createDevice(kontoId, "Handy")
        assertEquals(HttpStatusCode.OK, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(oldToken) }.status)

        val (code, output) = cli(env.deps, listOf("reset-password", "STEFAN"))
        assertEquals(0, code)
        val password = output.trim()
        assertEquals(20, password.length)
        assertTrue(password.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' })

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(oldToken) }.status)
        assertEquals(HttpStatusCode.OK, client.login("stefan", password).status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", "altaltaltalt").status)
    }

    @Test
    fun resetPasswordUnknownUserFails() = testServer { env ->
        val (code, output) = cliErr(env.deps, listOf("reset-password", "niemand"))
        assertEquals(1, code)
        assertEquals(1, output.trim().lines().size)
    }

    @Test
    fun kontoAnlegenGibtPasswortAus() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("konto-anlegen", "ben"))
        assertEquals(0, code)
        val match = assertNotNull(Regex("^Konto ben angelegt, Passwort: ([A-Za-z0-9]{20})$").matchEntire(output.trim()), output)
        val password = match.groupValues[1]
        val response = client.login("ben", password)
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = Protocol.json.decodeFromString(AuthResponse.serializer(), response.bodyAsText())
        assertEquals(env.deps.accounts.authenticate("ben", password), auth.kontoId)
    }

    @Test
    fun kontoAnlegenMitVergebenemNamenScheitert() = testServer { env ->
        env.deps.accounts.createUser("ben", "geheimgeheim")
        val (code, output) = cliErr(env.deps, listOf("konto-anlegen", "BEN"))
        assertEquals(1, code)
        assertEquals(1, output.trim().lines().size)
        assertEquals(2, cliErr(env.deps, listOf("konto-anlegen")).first)
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
                val (code2, _) = cliErr(deps, listOf("backup", target.toString()))
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
        val (code, output) = cliErr(env.deps, listOf("backup", target.toString()))
        assertEquals(1, code)
        assertEquals(1, output.trim().lines().size)
    }

    @Test
    fun compactPrintsCount() = testServer { env ->
        val (code, output) = cli(env.deps, listOf("compact"))
        assertEquals(0, code)
        assertEquals("0", output.trim())
    }

    /** Legt eine Datenbank auf Version 1 (vor dem Umstieg auf Konten) an und liefert die Konfiguration dazu. */
    private fun v1Datenbank(ordner: Path): ServerConfig {
        val config = ServerConfig(dbPath = ordner.resolve("foody.db").toString(), port = 0, adminUser = null, adminPassword = null,
            photoDir = ordner.resolve("photos").toString())
        Database("jdbc:sqlite:${config.dbPath}", migrationen = listOf(Migrations.all[0])).close()
        return config
    }

    private fun userVersion(datei: String): Int =
        DriverManager.getConnection("jdbc:sqlite:$datei").use { c ->
            c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
        }

    /** Wie [cliErr], aber über [AdminCli.ausfuehren] gegen die Datei aus [config]. */
    private fun admin(config: ServerConfig, args: List<String>): Triple<Int, String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = PrintStream(out, true, Charsets.UTF_8).use { o ->
            PrintStream(err, true, Charsets.UTF_8).use { e -> AdminCli.ausfuehren(config, args, o, e) }
        }
        return Triple(code, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    @Test
    fun adminMigriertNicht() {
        val ordner = Files.createTempDirectory("foody-admin-alt")
        try {
            val config = v1Datenbank(ordner)
            for (args in listOf(listOf("compact"), listOf("konto-anlegen", "ben"))) {
                val (code, out, err) = admin(config, args)
                assertEquals(1, code, args.toString())
                assertEquals("", out)
                assertEquals("Datenbank auf Version 1 – erst den Server mit V3 starten (er sichert und wandelt um).", err.trim())
                assertEquals(1, userVersion(config.dbPath))
                assertEquals(emptyList(), ordner.listDirectoryEntries("vor-migration-*"))
            }
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun adminBackupAufAlterVersion() {
        val ordner = Files.createTempDirectory("foody-admin-alt")
        try {
            val config = v1Datenbank(ordner)
            val ziel = ordner.resolve("vor-neustart.db").toString()
            val (code, out, _) = admin(config, listOf("backup", ziel))
            assertEquals(0, code)
            assertEquals(ziel, out.trim())
            assertEquals(1, userVersion(ziel))
            assertEquals(1, userVersion(config.dbPath))
            assertEquals(emptyList(), ordner.listDirectoryEntries("vor-migration-*"))
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun adminAufAktuellerVersion() {
        val ordner = Files.createTempDirectory("foody-admin-neu")
        try {
            val config = ServerConfig(dbPath = ordner.resolve("foody.db").toString(), port = 0, adminUser = null, adminPassword = null,
                photoDir = ordner.resolve("photos").toString())
            Database("jdbc:sqlite:${config.dbPath}").close()
            val (code, out, _) = admin(config, listOf("compact"))
            assertEquals(0, code)
            assertEquals("0", out.trim())
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun unknownCommandShowsHelp() = testServer { env ->
        val (code, output) = cliErr(env.deps, listOf("unsinn"))
        assertEquals(2, code)
        for (name in listOf("reset-password", "konto-anlegen", "backup", "compact")) assertTrue(output.contains(name), name)
        assertEquals(2, cli(env.deps, emptyList()).first)
        assertEquals(2, cli(env.deps, listOf("backup")).first)
    }
}
