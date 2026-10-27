package de.foody.app3

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Das zusammengeführte Manifest der App enthält beide FileProvider (Rezeptfotos aus :feature:rezepte, Packungsfotos aus
 * :core:scan) mit eigener Authority; der Scan nutzt eine eigene Unterklasse, sonst stießen sich die Einträge.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ManifestTest {
    private val kontext = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun anbieter(endung: String) =
        assertNotNull(kontext.packageManager.resolveContentProvider("${kontext.packageName}.$endung", 0), "Provider .$endung fehlt")

    @Test
    fun scanProvider() {
        val p = anbieter("scan")
        assertEquals("de.foody.core.scan.ScanFotoProvider", p.name)
        assertEquals(false, p.exported)
        assertEquals(true, p.grantUriPermissions)
    }

    @Test
    fun rezeptfotoProvider() {
        val p = anbieter("rezeptfotos")
        assertEquals("androidx.core.content.FileProvider", p.name)
        assertEquals(false, p.exported)
        assertEquals(true, p.grantUriPermissions)
    }
}
