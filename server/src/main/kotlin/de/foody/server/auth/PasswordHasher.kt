package de.foody.server.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Passwort-Hashing mit Argon2id (Bouncy Castle).
 * Format: `$argon2id$v=19$m=19456,t=2,p=1$<salt b64>$<hash b64>` (Base64 ohne Padding).
 */
class PasswordHasher(private val random: SecureRandom = SecureRandom()) {
    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$" +
            "${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    /** Prüft [password] gegen [encoded]; ein unlesbares Format gilt als falsches Passwort. */
    fun verify(password: String, encoded: String): Boolean {
        // Führendes "" vor dem ersten '$': ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19") return false
        return try {
            val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
            val salt = decoder.decode(parts[4])
            val expected = decoder.decode(parts[5])
            val actual = derive(
                password, salt,
                params.getValue("m"), params.getValue("t"), params.getValue("p"),
                expected.size,
            )
            MessageDigest.isEqual(expected, actual)
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        length: Int,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val out = ByteArray(length)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private companion object {
        const val MEMORY_KIB = 19456
        const val ITERATIONS = 2
        const val PARALLELISM = 1
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
    }
}
