package de.foody.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Gerätetoken: 32 Zufallsbytes als Base64url; gespeichert wird nur der SHA-256-Hash. */
object Tokens {
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
