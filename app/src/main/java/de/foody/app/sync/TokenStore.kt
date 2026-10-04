package de.foody.app.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert das Gerätetoken des Sync-Servers verschlüsselt: AES-256/GCM, der Schlüssel liegt nicht auslesbar im
 * `AndroidKeyStore` (Alias `foody_sync_token`), `iv:Chiffrat` (Base64) in den SharedPreferences `foody_sync`.
 * Ist der Eintrag nicht entschlüsselbar (beschädigt, Schlüssel weg), liefert [load] `null` und löscht ihn.
 * Das Token wird nie geloggt.
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun save(token: String) {
        val encoded = try {
            encrypt(token)
        } catch (_: Exception) {
            // Schlüssel unbrauchbar (z. B. ungültig geworden): neu anlegen und einmal wiederholen.
            deleteKey()
            encrypt(token)
        }
        // commit statt apply: Nach dem Anmelden darf das Token nicht verloren gehen.
        check(prefs.edit().putString(KEY_TOKEN, encoded).commit()) { "Token konnte nicht gespeichert werden" }
    }

    @Synchronized
    fun load(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            decrypt(stored)
        } catch (_: Exception) {
            prefs.edit().remove(KEY_TOKEN).commit()
            null
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).commit()
    }

    private fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(data)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(':')
        require(parts.size == 2) { "Format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun deleteKey() {
        try {
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Nichts weiter zu tun; der folgende Versuch meldet den Fehler.
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "foody_sync_token"
        const val PREFS = "foody_sync"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
