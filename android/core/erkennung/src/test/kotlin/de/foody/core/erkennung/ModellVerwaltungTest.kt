package de.foody.core.erkennung

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Modell installieren und entfernen, Schalter „Fotoerkennung“ (Phase 4 ohne Oberfläche). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ModellVerwaltungTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dateien = mapOf("content://bild" to ggufKopf("clip"), "content://sprache" to ggufKopf("qwen3vl"))

    private fun verwaltung() = ModellVerwaltung(
        ModellDateien(tmp.newFolder()),
        { uri -> dateien[uri]?.inputStream() ?: throw FileNotFoundException(uri) },
        Dispatchers.Unconfined,
    )

    @Test fun importierenUndEntfernenAendernDenStand() = runTest {
        val v = verwaltung()
        assertEquals(ModellDateien.Stand.Fehlt, v.stand.value)
        assertEquals(ModellDateien.Import.Fertig(bekannt = false), v.importieren(listOf("content://sprache", "content://bild")))
        assertIs<ModellDateien.Stand.Bereit>(v.stand.value)
        v.entfernen()
        assertEquals(ModellDateien.Stand.Fehlt, v.stand.value)
    }

    @Test fun nichtLesbareDateiIstEinFehler() = runTest {
        val v = verwaltung()
        assertEquals(ModellDateien.Import.Fehler, v.importieren(listOf("content://bild", "content://weg")))
        assertEquals(ModellDateien.Stand.Fehlt, v.stand.value)
    }

    @Test fun schalterStandardAnUndSichtbarNurMitModell() = runTest {
        val kontext: Context = ApplicationProvider.getApplicationContext()
        val prefs = kontext.getSharedPreferences("fotoerkennung", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val v = verwaltung()
        val e = ErkennungEinstellung(prefs, v)
        assertTrue(e.an.value)
        assertFalse(e.sichtbar.first(), "ohne Modell kein Knopf")
        v.importieren(listOf("content://bild", "content://sprache"))
        assertTrue(e.sichtbar.first())
        e.setzen(false)
        assertFalse(e.sichtbar.first())
        assertFalse(ErkennungEinstellung(prefs, v).an.value, "Wahl bleibt gespeichert")
    }
}
