package de.foody.server

import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.VerloreneRevisionen
import de.foody.server.admin.AdminCli
import de.foody.server.auth.AccountStore
import de.foody.server.auth.PasswordHasher
import de.foody.server.db.Database
import de.foody.server.db.Migrations
import de.foody.server.sync.RecordStore
import de.foody.server.sync.SyncService
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D4 (W5): Nach dem Einspielen einer Sicherung gleichen alle Geräte voll ab und erfahren, was dem Server fehlt. */
class WiederherstellungTest {
    private fun wiederhergestellt(deps: ServerDeps): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val code = PrintStream(buffer, true, Charsets.UTF_8).use { AdminCli(deps, it).run(listOf("wiederhergestellt")) }
        return code to buffer.toString(Charsets.UTF_8)
    }

    @Test
    fun alteCursorLaufenAbUndDerVerloreneBereichKommtMit() = testServer { env ->
        val (token, _) = env.setupKonto()
        client.pushOk(token, listOf(ingredient("i1", "Mehl"))) // Stand der Sicherung: Revision 1
        assertNull(client.pull(token, 0).verloren)

        val (code, output) = wiederhergestellt(env.deps)
        assertEquals(0, code)
        assertEquals(1, output.trim().lines().size, output)

        val sprung = 1 + SyncService.SPRUNG_NACH_WIEDERHERSTELLUNG
        // Ein Gerät, das vor dem Einspielen mehr gesehen hat, und eines auf dem Stand der Sicherung: beide neu
        for (since in listOf(5L, 1L, sprung - 1)) {
            val alt = client.pullRaw(token, "?since=$since")
            assertEquals(HttpStatusCode.Gone, alt.status, "since=$since")
            assertEquals(ErrorCode.CURSOR_EXPIRED, alt.errorCode())
        }

        val voll = client.pull(token, 0)
        assertEquals(listOf("i1"), voll.records.map { it.id })
        assertEquals(VerloreneRevisionen(nach = 1, vor = sprung), voll.verloren)
        assertEquals(sprung, voll.nextCursor)

        // Neue Änderungen bekommen Revisionen hinter dem Sprung
        val neu = client.pushOk(token, listOf(ingredient("i2", "Salz"))).results.single()
        assertEquals(sprung + 1, neu.rev)
        assertEquals(listOf("i2"), client.pull(token, voll.nextCursor).records.map { it.id })
    }

    @Test
    fun zweiteWiederherstellungBehaeltDenAnfangDesBereichs() = testServer { env ->
        val (token, _) = env.setupKonto()
        client.pushOk(token, listOf(ingredient("i1", "Mehl")))
        assertEquals(0, wiederhergestellt(env.deps).first)
        assertEquals(0, wiederhergestellt(env.deps).first)

        val sprung = SyncService.SPRUNG_NACH_WIEDERHERSTELLUNG
        assertEquals(VerloreneRevisionen(nach = 1, vor = 1 + 2 * sprung), client.pull(token, 0).verloren)
    }

    /** Migration 2 → 3: Bestehende Konten behalten Daten und Stand, ohne verlorenen Bereich. */
    @Test
    fun datenbankAufVersionZweiWirdUmgestellt() {
        val ordner = Files.createTempDirectory("foody-migration-3")
        try {
            val url = "jdbc:sqlite:${ordner.resolve("foody.db")}"
            val clock = MutableClock()
            val konto = Database(url, migrationen = Migrations.all.take(2)).use { db ->
                val id = AccountStore(db, clock, PasswordHasher()).createUser("stefan", "geheimgeheim")
                SyncService(db, RecordStore(), clock).push(id, listOf(ingredient("i1", "Mehl")))
                id
            }
            Database(url, clock = clock).use { db ->
                val voll = SyncService(db, RecordStore(), clock).pull(konto, since = 0, limit = 100)
                assertEquals(listOf("i1" to 1L), voll.records.map { it.id to it.rev })
                assertEquals(1L, voll.nextCursor)
                assertNull(voll.verloren)
            }
        } finally {
            ordner.toFile().deleteRecursively()
        }
    }

    @Test
    fun jedesKontoBehaeltSeinenStand() = testServer { env ->
        val (a, _) = env.setupKonto()
        val (b, _) = env.setupKonto()
        client.pushOk(a, listOf(ingredient("i1", "Mehl"), ingredient("i2", "Salz")))
        val (code, output) = wiederhergestellt(env.deps)
        assertEquals(0, code)
        assertEquals(2, output.trim().lines().size, output)

        val sprung = SyncService.SPRUNG_NACH_WIEDERHERSTELLUNG
        assertEquals(VerloreneRevisionen(nach = 2, vor = 2 + sprung), client.pull(a, 0).verloren)
        assertEquals(VerloreneRevisionen(nach = 0, vor = sprung), client.pull(b, 0).verloren)
    }
}
