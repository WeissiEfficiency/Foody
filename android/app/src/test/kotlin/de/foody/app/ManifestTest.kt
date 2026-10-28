package de.foody.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Das zusammengeführte Manifest der App enthält genau einen FileProvider für Kamerafotos (aus :core:data, für
 * Rezeptfotos und Packungsscan); die früheren Einträge aus :feature:rezepte und :core:scan sind weg.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ManifestTest {
    private val kontext = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun anbieter(endung: String) = kontext.packageManager.resolveContentProvider("${kontext.packageName}.$endung", 0)

    @Test
    fun kameraProvider() {
        val p = assertNotNull(anbieter("kamera"), "Provider .kamera fehlt")
        assertEquals("de.foody.core.data.fotos.KameraFotoProvider", p.name)
        assertEquals(false, p.exported)
        assertEquals(true, p.grantUriPermissions)
    }

    @Test
    fun keineAltenProvider() {
        assertNull(anbieter("scan"))
        assertNull(anbieter("rezeptfotos"))
    }
}
