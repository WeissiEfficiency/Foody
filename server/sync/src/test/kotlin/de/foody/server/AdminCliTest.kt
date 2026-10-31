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
import java.time.Duration
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
    fun pruefenErkenntHeileUndKaputteSicherung() = testServer { env ->
        val ordner = Files.createTempDirectory("foody-pruefen")
        try {
            env.deps.accounts.createUser("stefan", "geheimgeheim")
            val heil = ordner.resolve("heil.db")
            assertEquals(0, cli(env.deps, listOf("backup", heil.toString())).first)
            assertEquals(0 to "ok", cli(env.deps, listOf("pruefen", heil.toString())).let { it.first to it.second.trim() })

            // Seiten hinter dem Kopf mit Unsinn überschreiben: Die Datei öffnet sich noch, ist aber kaputt.
            val bytes = Files.readAllBytes(heil)
            for (i in 4096 until bytes.size) bytes[i] = 0x5A
            val kaputt = Files.write(ordner.resolve("kaputt.db"), bytes)
            val (code, output) = cliErr(env.deps, listOf("pruefen", kaputt.toString()))
            assertEquals(1, code)
            assertEquals(1, output.trim().lines().size, output)

            assertEquals(1, cliErr(env.deps, listOf("pruefen", ordner.resolve("fehlt.db").toString())).first)
        } finally {
            ordner.toFile().deleteRecursively()
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

    @Test
    fun geraeteListetAlleGeraete() = testServer { env ->
        val stefan = env.deps.accounts.createUser("stefan", "geheimgeheim")
        env.deps.accounts.createDevice(stefan, "Handy")
        env.clock.advance(Duration.ofDays(2))
        val tablet = env.deps.accounts.createDevice(stefan, "Tablet")
        env.deps.accounts.abmelden(assertNotNull(env.deps.accounts.deviceForToken(tablet)).deviceId)

        val (code, output) = cli(env.deps, listOf("geraete"))
        assertEquals(0, code)
        assertEquals(
            listOf("stefan · Handy · zuletzt 2026-10-04 · aktiv", "stefan · Tablet · zuletzt 2026-10-06 · widerrufen 2026-10-06"),
            output.trim().lines(),
        )
    }

    @Test
    fun abmeldenUngenutztWiderruftNurAlteGeraete() = testServer { env ->
        val stefan = env.deps.accounts.createUser("stefan", "geheimgeheim")
        val alt = env.deps.accounts.createDevice(stefan, "Handy alt")
        env.clock.advance(Duration.ofDays(40))
        val neu = env.deps.accounts.createDevice(stefan, "Handy neu")

        val (code, output) = cli(env.deps, listOf("abmelden-ungenutzt", "30"))
        assertEquals(0, code)
        assertEquals(listOf("Widerrufen: stefan · Handy alt · zuletzt 2026-10-04", "1 Gerät widerrufen"), output.trim().lines())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(alt) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(neu) }.status)
        // Ein zweiter Lauf findet nichts mehr
        assertEquals(listOf("0 Geräte widerrufen"), cli(env.deps, listOf("abmelden-ungenutzt", "30")).second.trim().lines())
    }

    @Test
    fun abmeldenUngenutztBrauchtTage() = testServer { env ->
        for (args in listOf(listOf("abmelden-ungenutzt"), listOf("abmelden-ungenutzt", "x"), listOf("abmelden-ungenutzt", "0"))) {
            assertEquals(2, cliErr(env.deps, args).first, args.toString())
        }
    }

    @Test
    fun kontoLoeschenLoeschtLeeresKonto() = testServer { env ->
        val ben = env.deps.accounts.createUser("ben", "geheimgeheim")
        val token = env.deps.accounts.createDevice(ben, "Handy")

        val (code, output) = cli(env.deps, listOf("konto-loeschen", "BEN"))
        assertEquals(0, code)
        assertEquals("Konto BEN gelöscht", output.trim())
        assertEquals(HttpStatusCode.Unauthorized, client.login("ben", "geheimgeheim").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(token) }.status)
        assertEquals("", cli(env.deps, listOf("geraete")).second.trim())
        // Der Name ist danach wieder frei
        assertEquals(0, cli(env.deps, listOf("konto-anlegen", "ben")).first)
    }

    @Test
    fun kontoLoeschenLaesstKontoMitDatenStehen() = testServer { env ->
        val (token, _) = env.setupKonto()
        val name = env.deps.db.tx { c ->
            c.createStatement().use { s -> s.executeQuery("SELECT username FROM user").use { it.next(); it.getString(1) } }
        }
        env.deps.db.tx { c ->
            c.createStatement().use { s ->
                s.executeUpdate(
                    "INSERT INTO sync_record (konto_id, type, id, rev, deleted, payload, updated_at) " +
                        "SELECT id, 'recipe', 'r1', 1, 1, NULL, 0 FROM user",
                )
            }
        }

        val (code, output) = cliErr(env.deps, listOf("konto-loeschen", name))
        assertEquals(1, code)
        assertEquals("Konto $name hat Daten – nicht gelöscht", output.trim())
        assertEquals(HttpStatusCode.OK, client.get("/api/v3/sync/pull") { protocol(); bearerAuth(token) }.status)
        assertEquals(1, cliErr(env.deps, listOf("konto-loeschen", "niemand")).first)
    }

    @Test
    fun kontoLoeschenLaesstKontoMitFotosStehen() = testServer { env ->
        val ben = env.deps.accounts.createUser("ben", "geheimgeheim")
        val ordner = Files.createDirectories(env.photoDir.resolve(ben))
        Files.write(ordner.resolve("${"a".repeat(64)}.jpg"), byteArrayOf(1))

        val (code, output) = cliErr(env.deps, listOf("konto-loeschen", "ben"))
        assertEquals(1, code)
        assertEquals("Konto ben hat Daten – nicht gelöscht", output.trim())
        assertEquals(HttpStatusCode.OK, client.login("ben", "geheimgeheim").status)
    }

    /** Legt eine Datenbank auf Version 3 (vor der letzten Migration) an und liefert die Konfiguration dazu. */
    private fun alteDatenbank(ordner: Path): ServerConfig {
        val config = ServerConfig(dbPath = ordner.resolve("foody.db").toString(), port = 0, adminUser = null, adminPassword = null,
            photoDir = ordner.resolve("photos").toString())
        Database("jdbc:sqlite:${config.dbPath}", migrationen = Migrations.all.take(3)).close()
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
            val config = alteDatenbank(ordner)
            for (args in listOf(listOf("compact"), listOf("konto-anlegen", "ben"))) {
                val (code, out, err) = admin(config, args)
                assertEquals(1, code, args.toString())
                assertEquals("", out)
                assertEquals("Datenbank auf Version 3 – erst den Server mit V3 starten (er sichert und wandelt um).", err.trim())
                assertEquals(3, userVersion(config.dbPath))
                assertEquals(emptyList(), ordner.listDirectoryEntries("vor-migration-*"))
            }
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    /** Nach dem Einspielen einer alten Sicherung stellt `wiederhergestellt` sie selbst um – vor dem ersten Sync. */
    @Test
    fun wiederhergestelltMigriertMitSicherung() {
        val ordner = Files.createTempDirectory("foody-admin-alt")
        try {
            val config = alteDatenbank(ordner)
            val (code, _, err) = admin(config, listOf("wiederhergestellt"))
            assertEquals(0, code, err)
            assertEquals(Migrations.all.size, userVersion(config.dbPath))
            assertEquals(1, ordner.listDirectoryEntries("vor-migration-*").size)
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun adminBackupAufAlterVersion() {
        val ordner = Files.createTempDirectory("foody-admin-alt")
        try {
            val config = alteDatenbank(ordner)
            val ziel = ordner.resolve("vor-neustart.db").toString()
            val (code, out, _) = admin(config, listOf("backup", ziel))
            assertEquals(0, code)
            assertEquals(ziel, out.trim())
            assertEquals(3, userVersion(ziel))
            assertEquals(3, userVersion(config.dbPath))
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
        for (name in listOf("reset-password", "konto-anlegen", "konto-loeschen", "geraete", "abmelden-ungenutzt", "backup", "pruefen", "compact")) {
            assertTrue(output.contains(name), name)
        }
        assertEquals(2, cli(env.deps, emptyList()).first)
        assertEquals(2, cli(env.deps, listOf("backup")).first)
    }
}
