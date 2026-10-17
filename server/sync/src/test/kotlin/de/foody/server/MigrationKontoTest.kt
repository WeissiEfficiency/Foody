package de.foody.server

import de.foody.server.auth.AccountStore
import de.foody.server.auth.DevicePrincipal
import de.foody.server.auth.PasswordHasher
import de.foody.server.auth.Tokens
import de.foody.server.db.Database
import de.foody.server.db.MigrationAbgebrochen
import de.foody.server.db.Migrations
import de.foody.server.sync.RecordStore
import de.foody.server.sync.SyncService
import de.foody.api.protocol.ErrorCode
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Migration 1 → 2: Haushalte werden zu Konten (Daten, Zähler, Geräte, Vorprüfung). */
class MigrationKontoTest {
    private val pw = "geheimgeheim"
    private val hasher = PasswordHasher()
    private val clock = Clock.systemUTC()
    private val ordner: Path = Files.createTempDirectory("foody-konto")
    private val url = "jdbc:sqlite:${ordner.resolve("foody.db")}"

    private val stefan = "u-stefan"
    private val admin = "u-admin"
    private val geraetA = "geraet-a"

    @AfterTest
    fun aufraeumen() {
        ordner.toFile().deleteRecursively()
    }

    /**
     * Datenbank auf Version 1 wie im Bestand: Konto `stefan` mit Haushalt `h1`, Konto `admin` ohne Haushalt,
     * zwei aktive und ein widerrufenes Gerät, vier Datensätze plus ein Vorratseintrag, zwei Einladungen.
     */
    private fun v1Datenbank(compactedBefore: Long = 3, extra: (Connection) -> Unit = {}) {
        Database(url, migrationen = listOf(Migrations.all[0])).use { db ->
            db.tx { c ->
                fun exec(sql: String, vararg werte: Any?) = c.prepareStatement(sql).use { st ->
                    werte.forEachIndexed { i, w -> st.setObject(i + 1, w) }
                    st.executeUpdate()
                }
                val user = "INSERT INTO user (id, username, username_lower, password_hash, display_name, is_admin, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0)"
                exec(user, stefan, "Stefan", "stefan", hasher.hash(pw), "Stefan", 0)
                exec(user, admin, "admin", "admin", hasher.hash(pw), "admin", 1)
                exec(
                    "INSERT INTO household (id, name, created_by, created_at, last_rev, compacted_before_rev) VALUES ('h1', 'Zuhause', ?, 0, 7, ?)",
                    stefan, compactedBefore,
                )
                exec("INSERT INTO membership (user_id, household_id, role, created_at) VALUES (?, 'h1', 'owner', 0)", stefan)
                val device = "INSERT INTO device (id, user_id, household_id, name, token_hash, created_at, last_seen_at, revoked_at) " +
                    "VALUES (?, ?, ?, ?, ?, 0, 0, ?)"
                exec(device, geraetA, stefan, "h1", "Handy", Tokens.sha256Hex("token-a"), null)
                exec(device, "geraet-b", stefan, "h1", "Tablet", Tokens.sha256Hex("token-b"), null)
                exec(device, "geraet-w", stefan, "h1", "Alt", Tokens.sha256Hex("token-widerrufen"), 5)
                val record = "INSERT INTO sync_record (household_id, type, id, rev, deleted, payload, updated_at, deleted_at, canonical_name) " +
                    "VALUES ('h1', ?, ?, ?, ?, ?, 1, ?, ?)"
                exec(record, "ingredient", "i1", 1, 0, """{"name":"Mehl"}""", null, "mehl")
                exec(record, "ingredient", "i2", 2, 0, """{"name":"Salz"}""", null, "salz")
                exec(record, "recipe", "r1", 3, 0, """{"name":"Brot"}""", null, null)
                exec(record, "ingredient", "i3", 4, 1, null, 10, null)
                exec(record, "pantry_item", "p1", 5, 0, """{"name":"Vorrat"}""", null, null)
                val invite = "INSERT INTO invite (id, code_hash, household_id, created_by, expires_at) VALUES (?, ?, ?, ?, 0)"
                exec(invite, "inv1", Tokens.sha256Hex("code1"), "h1", stefan)
                exec(invite, "inv2", Tokens.sha256Hex("code2"), null, "admin-cli")
                extra(c)
            }
        }
    }

    private fun <T> roh(block: (Connection) -> T): T = DriverManager.getConnection(url).use(block)

    private fun Connection.zahl(sql: String): Long = createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } }

    private fun Connection.tabelleDa(name: String): Boolean =
        prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use { st ->
            st.setString(1, name)
            st.executeQuery().use { it.next() }
        }

    private fun accounts(db: Database) = AccountStore(db, clock, hasher)

    @Test
    fun datenGehenAufsKonto() {
        v1Datenbank()
        Database(url).close()
        roh { c ->
            val zeilen = c.createStatement().use { s ->
                s.executeQuery("SELECT konto_id, type, id, rev FROM sync_record ORDER BY rev").use { rs ->
                    buildList { while (rs.next()) add(listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4).toString())) }
                }
            }
            assertEquals(
                listOf(
                    listOf(stefan, "ingredient", "i1", "1"),
                    listOf(stefan, "ingredient", "i2", "2"),
                    listOf(stefan, "recipe", "r1", "3"),
                    listOf(stefan, "ingredient", "i3", "4"),
                ),
                zeilen,
            )
            assertEquals(0, c.zahl("SELECT COUNT(*) FROM sync_record WHERE type = 'pantry_item'"))
            assertEquals(2, Migrations.aktuell(c))
        }
    }

    @Test
    fun zaehlerUebernommen() {
        v1Datenbank()
        Database(url).close()
        roh { c ->
            assertEquals(7, c.zahl("SELECT last_rev FROM user WHERE id = '$stefan'"))
            assertEquals(3, c.zahl("SELECT compacted_before_rev FROM user WHERE id = '$stefan'"))
            assertEquals(0, c.zahl("SELECT last_rev FROM user WHERE id = '$admin'"))
            assertEquals(0, c.zahl("SELECT compacted_before_rev FROM user WHERE id = '$admin'"))
        }
    }

    @Test
    fun alteTabellenWeg() {
        v1Datenbank()
        Database(url).close()
        roh { c ->
            for (name in listOf("household", "membership", "invite")) assertTrue(!c.tabelleDa(name), name)
            val umzug = c.createStatement().use { s ->
                s.executeQuery("SELECT household_id, konto_id FROM haushalt_umzug").use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
                }
            }
            assertEquals(listOf("h1" to stefan), umzug)
            val spalten = c.createStatement().use { s ->
                s.executeQuery("PRAGMA table_info(device)").use { rs -> buildList { while (rs.next()) add(rs.getString("name")) } }
            }
            assertTrue("household_id" !in spalten, spalten.toString())
            val userSpalten = c.createStatement().use { s ->
                s.executeQuery("PRAGMA table_info(user)").use { rs -> buildList { while (rs.next()) add(rs.getString("name")) } }
            }
            assertTrue("is_admin" !in userSpalten, userSpalten.toString())
        }
    }

    @Test
    fun tokensBleibenGueltig() {
        v1Datenbank()
        Database(url).use { db ->
            assertEquals(DevicePrincipal(geraetA, stefan), accounts(db).deviceForToken("token-a"))
            assertEquals(DevicePrincipal("geraet-b", stefan), accounts(db).deviceForToken("token-b"))
        }
    }

    @Test
    fun widerrufeneBleibenWiderrufen() {
        v1Datenbank()
        Database(url).use { db ->
            assertNull(accounts(db).deviceForToken("token-widerrufen"))
        }
    }

    @Test
    fun kontoOhneHaushaltBleibt() {
        v1Datenbank()
        Database(url).use { db ->
            assertEquals(admin, assertNotNull(accounts(db).authenticate("admin", pw)))
            assertEquals(stefan, accounts(db).authenticate("stefan", pw))
            val sync = SyncService(db, RecordStore(), clock)
            assertEquals(0, sync.pull(admin, 0, 100).records.size)
        }
    }

    private fun zaehleV1(): List<Long> = roh { c ->
        listOf(
            Migrations.aktuell(c).toLong(),
            c.zahl("SELECT COUNT(*) FROM household"),
            c.zahl("SELECT COUNT(*) FROM membership"),
            c.zahl("SELECT COUNT(*) FROM sync_record"),
            c.zahl("SELECT COUNT(*) FROM device"),
            c.zahl("SELECT COUNT(*) FROM invite"),
        )
    }

    @Test
    fun vorpruefungZweiMitglieder() {
        v1Datenbank { c ->
            c.createStatement().use { s ->
                s.executeUpdate(
                    "INSERT INTO user (id, username, username_lower, password_hash, display_name, created_at) " +
                        "VALUES ('u-anna', 'anna', 'anna', 'x', 'anna', 0)",
                )
                s.executeUpdate("INSERT INTO membership (user_id, household_id, role, created_at) VALUES ('u-anna', 'h1', 'member', 0)")
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertTrue(e.message!!.contains("h1"), e.message)
        assertTrue(e.message!!.contains("2 Mitglieder"), e.message)
        assertEquals(listOf(1L, 1L, 2L, 5L, 3L, 2L), zaehleV1())
    }

    @Test
    fun vorpruefungOhneMitglied() {
        v1Datenbank { c ->
            c.createStatement().use { s ->
                s.executeUpdate("INSERT INTO household (id, name, created_by, created_at) VALUES ('h2', 'Leer', 'x', 0)")
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertTrue(e.message!!.contains("h2"), e.message)
        assertTrue(e.message!!.contains("0 Mitglieder"), e.message)
        assertEquals(listOf(1L, 2L, 1L, 5L, 3L, 2L), zaehleV1())
    }

    @Test
    fun vorpruefungKontoInZweiHaushalten() {
        v1Datenbank { c ->
            c.createStatement().use { s ->
                s.executeUpdate("INSERT INTO household (id, name, created_by, created_at) VALUES ('h2', 'Ferien', '$stefan', 0)")
                s.executeUpdate("INSERT INTO membership (user_id, household_id, role, created_at) VALUES ('$stefan', 'h2', 'owner', 0)")
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertTrue(e.message!!.contains(stefan), e.message)
        assertTrue(e.message!!.contains("2 Haushalten"), e.message)
        assertEquals(listOf(1L, 2L, 2L, 5L, 3L, 2L), zaehleV1())
    }

    @Test
    fun vorpruefungDatensatzOhneHaushalt() {
        v1Datenbank { c ->
            c.createStatement().use { s ->
                s.executeUpdate(
                    "INSERT INTO sync_record (household_id, type, id, rev, deleted, payload, updated_at) " +
                        "VALUES ('h-weg', 'ingredient', 'x1', 1, 0, '{}', 1), ('h-weg', 'ingredient', 'x2', 2, 0, '{}', 1)",
                )
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertTrue(e.message!!.contains("h-weg"), e.message)
        assertTrue(e.message!!.contains("2 Datensätze"), e.message)
        assertEquals(listOf(1L, 1L, 1L, 7L, 3L, 2L), zaehleV1())
    }

    @Test
    fun vorpruefungMitgliedOhneKonto() {
        v1Datenbank { c ->
            c.createStatement().use { s ->
                s.executeUpdate("INSERT INTO household (id, name, created_by, created_at) VALUES ('h2', 'Geist', 'u-geist', 0)")
                s.executeUpdate("INSERT INTO membership (user_id, household_id, role, created_at) VALUES ('u-geist', 'h2', 'owner', 0)")
            }
        }
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url) }
        assertTrue(e.message!!.contains("h2"), e.message)
        assertTrue(e.message!!.contains("ohne Konto"), e.message)
        assertEquals(listOf(1L, 2L, 2L, 5L, 3L, 2L), zaehleV1())
    }

    @Test
    fun gegencheckBrichtBeiDatenverlustAb() {
        // Ersatz für Eintrag 1, der Datensätze verliert: der Gegencheck muss das vor dem Commit erkennen.
        v1Datenbank()
        val verlust = listOf(Migrations.all[0], "DELETE FROM sync_record WHERE id IN ('i1', 'p1')")
        val e = assertFailsWith<MigrationAbgebrochen> { Database(url, migrationen = verlust) }
        assertTrue(e.message!!.contains("4 Datensätze vorher, 3 nachher"), e.message)
        assertEquals(listOf(1L, 1L, 1L, 5L, 3L, 2L), zaehleV1())
    }

    @Test
    fun pullNachMigrationNahtlos() {
        // Kompaktierungsgrenze 2, damit der Cursor 2 (wie vor der Migration) gültig bleibt.
        v1Datenbank(compactedBefore = 2)
        Database(url).use { db ->
            val sync = SyncService(db, RecordStore(), clock)
            val page = sync.pull(stefan, since = 2, limit = 100)
            assertEquals(listOf("r1" to 3L, "i3" to 4L), page.records.map { it.id to it.rev })
            assertEquals(4L, page.nextCursor)
            val abgelaufen = assertFailsWith<ApiException> { sync.pull(stefan, since = 1, limit = 100) }
            assertEquals(ErrorCode.CURSOR_EXPIRED, abgelaufen.code)
        }
    }

    @Test
    fun weiterZaehlenNachMigration() {
        v1Datenbank()
        Database(url).use { db ->
            val sync = SyncService(db, RecordStore(), clock)
            val rev = sync.push(stefan, listOf(ingredient("i9", "Zucker"))).results.single().rev
            assertEquals(8L, rev)
        }
    }
}
