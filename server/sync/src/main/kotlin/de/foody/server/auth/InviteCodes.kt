package de.foody.server.auth

import java.security.SecureRandom

/** Einladungscodes `FOODY-XXXX-XXXX` in Crockford-Base32 (8 Zeichen, 40 Bit Zufall). */
object InviteCodes {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val PREFIX = "FOODY"
    private const val LENGTH = 8

    /** Erzeugt einen neuen Code im Anzeigeformat. */
    fun generate(random: SecureRandom): String {
        val body = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return "$PREFIX-${String(body, 0, 4)}-${String(body, 4, 4)}"
    }

    /**
     * Normalisiert eine Eingabe auf die 8 Code-Zeichen: Großbuchstaben, Leerzeichen und Bindestriche weg,
     * Präfix `FOODY` weg, `O→0`, `I`/`L→1`. `null`, wenn das Ergebnis kein gültiger Code ist.
     */
    fun normalize(input: String): String? {
        var s = input.uppercase().filter { it != ' ' && it != '-' }
        if (s.length > LENGTH && s.startsWith(PREFIX)) s = s.removePrefix(PREFIX)
        s = s.map { c ->
            when (c) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> c
            }
        }.joinToString("")
        return if (s.length == LENGTH && s.all { it in ALPHABET }) s else null
    }
}
