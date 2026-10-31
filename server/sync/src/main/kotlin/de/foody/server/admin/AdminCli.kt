package de.foody.server.admin

import de.foody.server.ApiException
import de.foody.server.ServerConfig
import de.foody.server.ServerDeps
import de.foody.server.auth.KontoExistiert
import de.foody.server.db.Database
import de.foody.server.db.Migrations
import de.foody.server.sync.Compactor
import java.io.File
import java.io.PrintStream
import java.security.SecureRandom
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.sqlite.SQLiteConfig

/**
 * Administrationsbefehle (`foody admin …`). Erfolgsausgaben gehen an [out], Fehlerzeilen und Hilfe bei falschem Aufruf an [err]; Exit-Codes: 0 Erfolg,
 * 1 Laufzeitfehler (Einzeiler), 2 Aufruf falsch oder unbekannter Befehl (Hilfe).
 */
class AdminCli(
    private val deps: ServerDeps,
    private val out: PrintStream,
    private val err: PrintStream = System.err,
) {
    private val random = SecureRandom()

    fun run(args: List<String>): Int = try {
        dispatch(args)
    } catch (e: Exception) {
        // Einzeilig und ohne Stacktrace; das Passwort wird erst nach erfolgreichem Update ausgegeben.
        fail("Fehler: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
    }

    private fun dispatch(args: List<String>): Int = when (args.firstOrNull()) {
        "reset-password" -> if (args.size == 2) resetPassword(args[1]) else usage()
        "konto-anlegen" -> if (args.size == 2) kontoAnlegen(args[1]) else usage()
        "konto-loeschen" -> if (args.size == 2) kontoLoeschen(args[1]) else usage()
        "geraete" -> if (args.size == 1) geraete() else usage()
        "abmelden-ungenutzt" -> args.getOrNull(1)?.toLongOrNull()?.takeIf { args.size == 2 && it > 0 }
            ?.let(::abmeldenUngenutzt) ?: usage()
        "backup" -> if (args.size == 2) backup(args[1]) else usage()
        "pruefen" -> if (args.size == 2) pruefen(args[1]) else usage()
        "compact" -> if (args.size == 1) compact() else usage()
        else -> usage()
    }

    private fun zufallsPasswort(): String =
        buildString { repeat(PASSWORD_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    private fun resetPassword(username: String): Int {
        val password = zufallsPasswort()
        if (!deps.accounts.resetPassword(username, password)) return fail("Unbekannter Benutzer: $username")
        out.println(password)
        return 0
    }

    /** Legt ein Konto mit Zufallspasswort an; das Passwort wird genau einmal ausgegeben. */
    private fun kontoAnlegen(name: String): Int {
        val password = zufallsPasswort()
        try {
            deps.accounts.createUser(name, password)
        } catch (_: KontoExistiert) {
            return fail("Konto existiert bereits: $name")
        } catch (_: ApiException) {
            // Das Zufallspasswort ist immer gültig: Scheitern liegt am Namen.
            return fail("Ungültiger Name: $name (3–32 Zeichen A-Z a-z 0-9 . _ -)")
        }
        out.println("Konto $name angelegt, Passwort: $password")
        return 0
    }

    /** Löscht ein Konto ohne Datensätze und Fotos samt Geräten (z. B. ein versehentlich angelegtes). */
    private fun kontoLoeschen(name: String): Int {
        val id = deps.accounts.kontoId(name) ?: return fail("Unbekanntes Konto: $name")
        if (deps.photos.listHashes(id).isNotEmpty() || !deps.accounts.kontoLoeschenWennLeer(id)) {
            return fail("Konto $name hat Daten – nicht gelöscht")
        }
        out.println("Konto $name gelöscht")
        return 0
    }

    private fun geraete(): Int {
        for (g in deps.accounts.geraete()) {
            val stand = g.widerrufen?.let { "widerrufen ${tag(it)}" } ?: "aktiv"
            out.println("${g.konto} · ${g.name} · zuletzt ${tag(g.zuletzt)} · $stand")
        }
        return 0
    }

    /** Widerruft Geräte, die sich seit [tage] Tagen nicht gemeldet haben. */
    private fun abmeldenUngenutzt(tage: Long): Int {
        val widerrufen = deps.accounts.abmeldenUngenutzt(Duration.ofDays(tage))
        for (g in widerrufen) out.println("Widerrufen: ${g.konto} · ${g.name} · zuletzt ${tag(g.zuletzt)}")
        out.println(if (widerrufen.size == 1) "1 Gerät widerrufen" else "${widerrufen.size} Geräte widerrufen")
        return 0
    }

    private fun tag(zeit: Instant): String = LocalDate.ofInstant(zeit, deps.clock.zone).toString()

    private fun backup(target: String): Int {
        if (File(target).exists()) return fail("Ziel existiert bereits: $target")
        deps.db.outsideTx { c ->
            c.prepareStatement("VACUUM INTO ?").use { st ->
                st.setString(1, target)
                st.execute()
            }
        }
        integritaet(target)?.let { fehler ->
            File(target).delete()
            return fail("Sicherung beschädigt, nicht behalten: $fehler")
        }
        out.println(target)
        return 0
    }

    /** Prüft eine Sicherung (oder die Datenbank) mit `PRAGMA integrity_check`, ohne sie zu ändern. */
    private fun pruefen(datei: String): Int {
        if (!File(datei).isFile) return fail("Datei fehlt: $datei")
        integritaet(datei)?.let { return fail("Beschädigt: $it") }
        out.println("ok")
        return 0
    }

    /**
     * `null`, wenn [datei] heil ist, sonst die Meldungen von `integrity_check` auf einer Zeile (gekürzt). Öffnet nur
     * lesend.
     */
    private fun integritaet(datei: String): String? {
        val nurLesen = SQLiteConfig().apply { setReadOnly(true) }.toProperties()
        return DriverManager.getConnection("jdbc:sqlite:$datei", nurLesen).use { c ->
            c.createStatement().use { s ->
                s.executeQuery("PRAGMA integrity_check").use { rs -> rs.next(); rs.getString(1) }
            }
        }.takeUnless { it == "ok" }?.lines()?.filter { it.isNotBlank() }?.joinToString(" · ")?.take(MELDUNG_MAX)
    }

    private fun compact(): Int {
        out.println(Compactor(deps.db, deps.clock, deps.photos).run())
        return 0
    }

    private fun fail(message: String): Int {
        err.println(message)
        return 1
    }

    private fun usage(): Int {
        err.println(
            """
            Verwendung: admin <Befehl>
              reset-password <benutzer>  Neues Zufallspasswort setzen und alle Geräte des Benutzers widerrufen
              konto-anlegen <name>       Neues Konto mit Zufallspasswort anlegen (Passwort wird einmal ausgegeben)
              konto-loeschen <name>      Konto ohne Daten und Fotos samt Geräten löschen
              geraete                    Alle Geräte mit letzter Anfrage und Widerruf auflisten
              abmelden-ungenutzt <tage>  Geräte widerrufen, die sich seit <tage> Tagen nicht gemeldet haben
              backup <zieldatei>         Konsistente Kopie der Datenbank schreiben und prüfen (Ziel darf nicht existieren)
              pruefen <datei>            Sicherung mit integrity_check prüfen, z. B. vor dem Einspielen
              compact                    Alte Löschmarkierungen sofort kompaktieren
            """.trimIndent(),
        )
        return 2
    }

    companion object {
        private const val PASSWORD_LENGTH = 20
        private const val MELDUNG_MAX = 200
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /**
         * Führt einen Admin-Befehl direkt gegen die Datenbankdatei aus `config` aus (für `foody-admin`, ohne Netty; läuft
         * auch parallel zum Server, WAL). Migriert nie: Steht eine Migration aus, geht nur `backup`, alles andere bricht mit
         * Exit-Code 1 ab – Sicherung und Umwandlung sind Sache des Serverstarts.
         */
        fun ausfuehren(config: ServerConfig, args: List<String>, out: PrintStream = System.out, err: PrintStream = System.err): Int =
            Database("jdbc:sqlite:${config.dbPath}", migrieren = false).use { db ->
                val version = db.outsideTx { Migrations.aktuell(it) }
                if (version < Migrations.all.size && args.firstOrNull() != "backup") {
                    err.println("Datenbank auf Version $version – erst den Server mit V3 starten (er sichert und wandelt um).")
                    1
                } else {
                    AdminCli(ServerDeps.create(config, db), out, err).run(args)
                }
            }
    }
}
