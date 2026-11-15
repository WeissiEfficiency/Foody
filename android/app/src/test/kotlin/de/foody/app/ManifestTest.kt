package de.foody.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.sync.TokenSpeicher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Das zusammengeführte Manifest der App enthält genau einen FileProvider für Kamerafotos (aus :core:data, für
 * Rezeptfotos und Packungsscan); die früheren Einträge aus :feature:rezepte und :core:scan sind weg.
 * Dazu die Regeln für Cloud-Backup und Gerätewechsel (`data_extraction_rules.xml`).
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

    /** Regeln aus `data_extraction_rules.xml` je Abschnitt als „domain:path“. */
    private fun ausnahmen(): Map<String, List<String>> {
        val ergebnis = mutableMapOf<String, MutableList<String>>()
        val xml = kontext.resources.getXml(R.xml.data_extraction_rules)
        var abschnitt = ""
        while (xml.next() != XmlPullParser.END_DOCUMENT) {
            if (xml.eventType != XmlPullParser.START_TAG) continue
            when (xml.name) {
                "cloud-backup", "device-transfer" -> abschnitt = xml.name
                "exclude" -> ergebnis.getOrPut(abschnitt) { mutableListOf() } +=
                    "${xml.getAttributeValue(null, "domain")}:${xml.getAttributeValue(null, "path")}"
            }
        }
        return ergebnis
    }

    /** Gerätewechsel nimmt alles mit außer dem Sync-Token (Schlüssel im Keystore); Cloud-Backup nimmt nichts. */
    @Test
    fun geraetewechselOhneTokenKeinCloudBackup() {
        val a = ausnahmen()
        assertEquals(listOf("sharedpref:${TokenSpeicher.PREFS_NAME}.xml"), a["device-transfer"])
        val cloud = a.getValue("cloud-backup")
        listOf("root", "file", "database", "sharedpref", "external").forEach { assertTrue("$it:." in cloud, "Cloud-Backup schließt $it nicht aus") }
    }
}
