package de.foody.server.admin

import de.foody.server.ServerDeps
import de.foody.server.sync.Compactor
import java.io.File
import java.io.PrintStream
import java.security.SecureRandom

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
        "invite" -> if (args.size == 1) invite() else usage()
        "backup" -> if (args.size == 2) backup(args[1]) else usage()
        "compact" -> if (args.size == 1) compact() else usage()
        else -> usage()
    }

    private fun resetPassword(username: String): Int {
        val password = buildString { repeat(PASSWORD_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        if (!deps.accounts.resetPassword(username, password)) return fail("Unbekannter Benutzer: $username")
        out.println(password)
        return 0
    }

    private fun invite(): Int {
        out.println(deps.accounts.createInvite(CREATOR, null).code)
        return 0
    }

    private fun backup(target: String): Int {
        if (File(target).exists()) return fail("Ziel existiert bereits: $target")
        deps.db.outsideTx { c ->
            c.prepareStatement("VACUUM INTO ?").use { st ->
                st.setString(1, target)
                st.execute()
            }
        }
        out.println(target)
        return 0
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
              invite                     Einladungscode für ein neues Konto (ohne Haushalt) erzeugen
              backup <zieldatei>         Konsistente Kopie der Datenbank schreiben (Ziel darf nicht existieren)
              compact                    Alte Löschmarkierungen sofort kompaktieren
            """.trimIndent(),
        )
        return 2
    }

    private companion object {
        const val PASSWORD_LENGTH = 20
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /** `invite.created_by` ist NOT NULL; Haushalts-lose Einladungen prüfen den Ersteller beim Einlösen nicht. */
        const val CREATOR = "admin-cli"
    }
}
