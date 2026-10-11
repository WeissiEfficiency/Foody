package de.foody.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.sync.TokenStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** [TokenStore]: verschlüsselte Ablage des Sync-Tokens im AndroidKeyStore. */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("foody_sync", Context.MODE_PRIVATE)
    private lateinit var store: TokenStore

    @Before fun setUp() {
        store = TokenStore(context)
        store.clear()
    }

    @After fun tearDown() = store.clear()

    @Test fun roundTrip() {
        store.save("tok-ÄÖ-1234567890")
        assertEquals("tok-ÄÖ-1234567890", store.load())
        // Auch eine neue Instanz (z. B. nach Prozessneustart) liest es wieder
        assertEquals("tok-ÄÖ-1234567890", TokenStore(context).load())
    }

    @Test fun clearRemoves() {
        store.save("abc")
        store.clear()
        assertNull(store.load())
    }

    @Test fun corruptedValueReturnsNull() {
        store.save("abc")
        prefs.edit().putString("token", "AAAA:BBBB").commit()
        assertNull(store.load())
        assertFalse(prefs.contains("token"))
        prefs.edit().putString("token", "kein-format").commit()
        assertNull(store.load())
        assertFalse(prefs.contains("token"))
    }

    @Test fun storedValueIsNotPlaintext() {
        store.save("SECRET-TOKEN-4711")
        val raw = assertNotNull(prefs.getString("token", null))
        assertFalse(raw.contains("SECRET-TOKEN-4711"))
        assertFalse(prefs.all.values.any { it.toString().contains("SECRET-TOKEN-4711") })
    }

    @Test fun saveOverwrites() {
        store.save("eins")
        store.save("zwei")
        assertEquals("zwei", store.load())
    }
}
