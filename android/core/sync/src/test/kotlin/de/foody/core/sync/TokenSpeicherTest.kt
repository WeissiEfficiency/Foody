package de.foody.core.sync

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unter Robolectric gibt es keinen `AndroidKeyStore`: Die Tests geben dem [TokenSpeicher] eine Schlüsselablage
 * im Speicher (gleicher Algorithmus AES-256/GCM). Geprüft werden Ablage, Format und Fehlerverhalten; den echten
 * Keystore-Zugriff ([AndroidKeystoreAblage]) prüfen nur Läufe auf dem Gerät.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TokenSpeicherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("foody_sync", Context.MODE_PRIVATE)

    /** Schlüsselablage im Speicher; [fehler] simuliert einen vorübergehend gestörten Keystore. */
    private class SpeicherAblage : Schluesselablage {
        var schluessel: SecretKey? = null
        var fehler: Exception? = null

        override fun holen(): SecretKey? {
            fehler?.let { throw it }
            return schluessel
        }

        override fun erzeugen(): SecretKey =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { schluessel = it }

        override fun loeschen() {
            schluessel = null
        }
    }

    private val ablage = SpeicherAblage()

    private fun speicher() = TokenSpeicher(context, ablage)

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Verschlüsselt wie die v1 (`TokenStore.encrypt`): `Base64(iv):Base64(chiffrat)`, NO_WRAP, AES/GCM/NoPadding. */
    private fun wieVEinsVerschluesseln(token: String, schluessel: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, schluessel)
        return b64(cipher.iv) + ":" + b64(cipher.doFinal(token.toByteArray(Charsets.UTF_8)))
    }

    /** Entschlüsselt wie die v1 (`TokenStore.decrypt`), Tag 128 Bit. */
    private fun wieVEinsEntschluesseln(gespeichert: String, schluessel: SecretKey): String {
        val (iv, daten) = gespeichert.split(':')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, schluessel, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(daten, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    @Test
    fun tokenUeberlebtNeustart() {
        speicher().speichern("geraete-token-1")
        // Gespeichert wird nur verschlüsselt
        val roh = assertNotNull(prefs.getString("token", null))
        assertTrue("geraete-token-1" !in roh)

        // Neue Instanz (App-Neustart): gleiche Prefs, gleicher Schlüssel
        assertEquals("geraete-token-1", speicher().laden())
    }

    @Test
    fun loeschenEntferntToken() {
        val speicher = speicher()
        speicher.speichern("t")
        speicher.loeschen()
        assertNull(speicher.laden())
        assertNull(prefs.getString("token", null))
    }

    @Test
    fun unlesbaresTokenGiltAlsAbgemeldet() {
        speicher().speichern("geraete-token-1")
        val roh = assertNotNull(prefs.getString("token", null))

        // Chiffrat verfälscht (Prüfsumme stimmt nicht mehr)
        val (iv, daten) = roh.split(':')
        val bytes = Base64.decode(daten, Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        prefs.edit().putString("token", iv + ":" + b64(bytes)).commit()
        assertNull(speicher().laden())
        assertNull(prefs.getString("token", null), "unbrauchbarer Eintrag wird verworfen")

        // Falsches Format und kein Base64
        for (kaputt in listOf("kaputt", "a:b:c", "!!!:???")) {
            prefs.edit().putString("token", kaputt).commit()
            assertNull(speicher().laden(), "laden() bei '$kaputt'")
            assertNull(prefs.getString("token", null), "Eintrag '$kaputt' wird verworfen")
        }

        // Schlüssel fehlt (z. B. nach Wiederherstellung auf einem anderen Gerät)
        speicher().speichern("geraete-token-2")
        ablage.schluessel = null
        assertNull(speicher().laden())
        assertNull(prefs.getString("token", null))

        // Danach lässt sich wieder anmelden
        speicher().speichern("geraete-token-3")
        assertEquals("geraete-token-3", speicher().laden())
    }

    @Test
    fun voruebergehenderKeystoreFehlerBehaeltToken() {
        speicher().speichern("geraete-token-1")
        ablage.fehler = KeyStoreException("Keystore gerade nicht erreichbar")
        assertNull(speicher().laden())
        assertNotNull(prefs.getString("token", null), "vorübergehender Fehler verwirft das Token nicht")
        ablage.fehler = null
        assertEquals("geraete-token-1", speicher().laden())
    }

    @Test
    fun gleicheAblageWieVEins() {
        assertEquals("foody_sync", TokenSpeicher.PREFS_NAME)
        assertEquals("token", TokenSpeicher.EINTRAG)
        assertEquals("foody_sync_token", TokenSpeicher.SCHLUESSEL_ALIAS)
        assertEquals("AndroidKeyStore", AndroidKeystoreAblage.KEYSTORE)

        // Von der v1 geschriebenes Token liest die V3 ohne neue Anmeldung
        val schluessel = ablage.erzeugen()
        prefs.edit().putString("token", wieVEinsVerschluesseln("token-aus-v1", schluessel)).commit()
        assertEquals("token-aus-v1", speicher().laden())

        // Und umgekehrt: Das Format der V3 ist das der v1
        speicher().speichern("token-aus-v3")
        val roh = assertNotNull(prefs.getString("token", null))
        assertEquals("token-aus-v3", wieVEinsEntschluesseln(roh, schluessel))
        assertNotEquals(roh, wieVEinsVerschluesseln("token-aus-v3", schluessel), "frischer IV je Speichern")
        assertEquals(12, Base64.decode(roh.substringBefore(':'), Base64.NO_WRAP).size)
    }
}
