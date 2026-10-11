package de.foody.sync.protocol

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Anfrage `POST /photos/missing`: höchstens [Protocol.MAX_PUSH_RECORDS] gültige Foto-Hashes. */
@Serializable
data class PhotosMissingRequest(val hashes: List<String>)

/** Antwort: die Hashes, die der Server für den Haushalt noch nicht hat. */
@Serializable
data class PhotosMissingResponse(val missing: List<String>)

/** Foto-Hash: SHA-256 des JPEG-Inhalts als 64 Zeichen Kleinbuchstaben-Hex. */
object PhotoHash {
    val REGEX: Regex = Regex("^[0-9a-f]{64}$")

    fun isValid(h: String): Boolean = REGEX.matches(h)

    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
