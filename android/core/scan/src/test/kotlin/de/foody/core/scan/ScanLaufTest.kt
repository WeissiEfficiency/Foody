package de.foody.core.scan

import androidx.lifecycle.SavedStateHandle
import de.foody.core.data.zutaten.Packungswerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanLaufTest {
    private val code = "4006040002031"
    private val packung = Packungswerte(null, NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(276)), code, PackungScan.QUELLE_FOTO)
    private val packungen = mutableListOf<Packungswerte>()
    private var fotoCode: String? = "nie gefragt"

    private fun lauf(scope: CoroutineScope, strichcode: ScanAusgang, foto: suspend () -> ScanAusgang) = ScanLauf(
        verfuegbar = true, vorladen = {}, strichcodeLesen = { strichcode },
        fotoLesen = { _, c -> fotoCode = c; foto() },
        scope = scope, gespeichert = SavedStateHandle(), onPackung = { packungen += it }, onImKatalog = {},
    )

    @Test
    fun fotoNachNichtGefundenTraegtDenStrichcode() = runTest {
        val l = lauf(this, ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT)) { ScanAusgang.Gefunden(packung) }
        l.strichcode()
        testScheduler.advanceUntilIdle()
        assertTrue(l.zustand.fotoAnbieten)
        l.foto("content://bild")
        testScheduler.advanceUntilIdle()
        assertEquals(code, fotoCode)
        assertEquals(listOf(packung), packungen)
        assertFalse(l.zustand.laeuft)
        assertNull(l.zustand.strichcode)
    }

    @Test
    fun aufraeumenAuchWennDasViewModelEndet() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val nie = CompletableDeferred<ScanAusgang>()
        val l = lauf(scope, ScanAusgang.Abgebrochen) { nie.await() }
        var geloescht = false
        l.foto("file:///scan/packung.jpg") { geloescht = true }
        testScheduler.advanceUntilIdle()
        assertTrue(l.zustand.laeuft)
        scope.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(geloescht)
        assertTrue(packungen.isEmpty())
    }
}
