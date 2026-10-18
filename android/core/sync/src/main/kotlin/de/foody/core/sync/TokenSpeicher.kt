package de.foody.core.sync

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Ablage des AES-Schlüssels, mit dem das Token verschlüsselt wird. */
interface Schluesselablage {
    /** Der vorhandene Schlüssel oder `null`, wenn es keinen gibt. Vorübergehende Störungen werfen. */
    fun holen(): SecretKey?

    /** Legt einen neuen Schlüssel an (ersetzt einen vorhandenen). */
    fun erzeugen(): SecretKey

    /** Entfernt den Schlüssel; Fehler werden geschluckt. */
    fun loeschen()
}

/** Schlüssel nicht auslesbar im `AndroidKeyStore` unter [TokenSpeicher.SCHLUESSEL_ALIAS] (AES-256, GCM) wie in der v1. */
class AndroidKeystoreAblage : Schluesselablage {
    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun holen(): SecretKey? = keyStore().getKey(TokenSpeicher.SCHLUESSEL_ALIAS, null) as? SecretKey

    override fun erzeugen(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                TokenSpeicher.SCHLUESSEL_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun loeschen() {
        try {
            keyStore().deleteEntry(TokenSpeicher.SCHLUESSEL_ALIAS)
        } catch (_: Exception) {
            // Nichts weiter zu tun; der folgende Versuch meldet den Fehler.
        }
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
    }
}

/** Der Schlüssel zum Entschlüsseln fehlt (endgültig verloren, z. B. nach Wiederherstellung auf einem anderen Gerät). */
private class SchluesselFehlt : Exception()

/**
 * Gerätetoken des Sync-Servers, verschlüsselt abgelegt genau wie in der v1 (sonst müsste sich der Nutzer nach dem
 * Update neu anmelden): AES-256/GCM, Schlüssel im `AndroidKeyStore` (Alias [SCHLUESSEL_ALIAS]), in den
 * SharedPreferences [PREFS_NAME] unter [EINTRAG] der Text `Base64(iv):Base64(chiffrat)` (Base64 ohne Umbruch).
 *
 * Ist der Eintrag nicht entschlüsselbar, liefert [laden] `null` (gilt als abgemeldet). Verworfen wird er nur, wenn er
 * endgültig unbrauchbar ist (falsches Format, Prüfsumme falsch, Schlüssel weg), nicht bei vorübergehenden
 * Keystore-Störungen. Das Token wird nie geloggt. Das zuletzt entschlüsselte Paar bleibt im Speicher, damit nicht jede
 * Anfrage den Keystore bemüht.
 */
// commit() statt apply() und ohne KTX-`edit {}`: Das Ergebnis wird gebraucht – ein Token darf nach dem Anmelden
// nicht still verloren gehen.
@SuppressLint("UseKtx", "ApplySharedPref")
@Singleton
class TokenSpeicher internal constructor(context: Context, private val ablage: Schluesselablage) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, AndroidKeystoreAblage())

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** (gespeicherter Text, Token) der letzten Entschlüsselung. */
    private var zwischenstand: Pair<String, String>? = null

    @Synchronized
    fun laden(): String? {
        val gespeichert = prefs.getString(EINTRAG, null) ?: return null
        zwischenstand?.takeIf { it.first == gespeichert }?.let { return it.second }
        return try {
            entschluesseln(gespeichert).also { zwischenstand = gespeichert to it }
        } catch (e: Exception) {
            if (endgueltig(e)) {
                zwischenstand = null
                prefs.edit().remove(EINTRAG).commit()
            }
            null
        }
    }

    @Synchronized
    fun speichern(token: String) {
        val verschluesselt = try {
            verschluesseln(token)
        } catch (_: Exception) {
            // Schlüssel unbrauchbar (z. B. ungültig geworden): neu anlegen und einmal wiederholen.
            ablage.loeschen()
            // Ohne Schlüssel ist auch der gemerkte Eintrag nicht mehr lesbar: Zwischenstand verwerfen.
            zwischenstand = null
            verschluesseln(token)
        }
        check(prefs.edit().putString(EINTRAG, verschluesselt).commit()) { "Token konnte nicht gespeichert werden" }
        zwischenstand = verschluesselt to token
    }

    @Synchronized
    fun loeschen() {
        zwischenstand = null
        prefs.edit().remove(EINTRAG).commit()
    }

    private fun endgueltig(e: Exception): Boolean =
        e is AEADBadTagException || e is IllegalArgumentException || e is InvalidAlgorithmParameterException ||
            e is UnrecoverableKeyException || e is KeyPermanentlyInvalidatedException || e is SchluesselFehlt

    private fun verschluesseln(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, ablage.holen() ?: ablage.erzeugen())
        val daten = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(daten)
    }

    private fun entschluesseln(gespeichert: String): String {
        val teile = gespeichert.split(':')
        require(teile.size == 2) { "Format" }
        val iv = Base64.decode(teile[0], Base64.NO_WRAP)
        val daten = Base64.decode(teile[1], Base64.NO_WRAP)
        val schluessel = ablage.holen() ?: throw SchluesselFehlt()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, schluessel, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(daten), Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        /** SharedPreferences der v1 und V3. */
        const val PREFS_NAME = "foody_sync"

        /** Eintrag mit `iv:chiffrat`. */
        const val EINTRAG = "token"

        /** Alias des Schlüssels im AndroidKeyStore. */
        const val SCHLUESSEL_ALIAS = "foody_sync_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
