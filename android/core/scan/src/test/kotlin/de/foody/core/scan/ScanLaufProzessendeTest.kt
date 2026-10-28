package de.foody.core.scan

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.testing.nachProzessende
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** R2-N1: Der Knopf-Zustand liegt über `saveable` im SavedStateHandle und übersteht ein Prozessende. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ScanLaufProzessendeTest {
    private val code = "4006040002031"

    private fun lauf(h: SavedStateHandle, scope: CoroutineScope) = ScanLauf(
        verfuegbar = true, vorladen = {}, strichcodeLesen = { ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT) },
        fotoLesen = { _, _ -> ScanAusgang.Abgebrochen }, scope = scope, gespeichert = h, onPackung = {}, onImKatalog = {},
    )

    @Test
    fun strichcodeUndKamerafotoUeberstehenProzessende() = runTest {
        val h = SavedStateHandle()
        val vor = lauf(h, this)
        vor.strichcode()
        testScheduler.advanceUntilIdle()
        vor.zustand.kameraGestartet("/cache/scan/packung.jpg")

        val nach = lauf(h.nachProzessende(), this).zustand
        assertEquals(code, nach.strichcode)
        assertEquals("/cache/scan/packung.jpg", nach.fotoPfad)
        assertTrue(nach.fotoAnbieten)
        assertEquals(vor.zustand.meldung, nach.meldung)
    }
}
