package de.foody.server.admin

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.sync.Compactor
import de.foody.sync.protocol.ErrorCode
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
        "konto-anlegen" -> if (args.size == 2) kontoAnlegen(args[1]) else usage()
        "backup" -> if (args.size == 2) backup(args[1]) else usage()
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
        } catch (e: ApiException) {
            // Das Zufallspasswort ist immer gültig: Scheitern liegt am Namen.
            return fail(if (e.code == ErrorCode.USERNAME_TAKEN) "Konto existiert bereits: $name" else "Ungültiger Name: $name (3–32 Zeichen A-Z a-z 0-9 . _ -)")
        }
        out.println("Konto $name angelegt, Passwort: $password")
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
              konto-anlegen <name>       Neues Konto mit Zufallspasswort anlegen (Passwort wird einmal ausgegeben)
              backup <zieldatei>         Konsistente Kopie der Datenbank schreiben (Ziel darf nicht existieren)
              compact                    Alte Löschmarkierungen sofort kompaktieren
            """.trimIndent(),
        )
        return 2
    }

    private companion object {
        const val PASSWORD_LENGTH = 20
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}
