package de.foody.feature.heute.tagebuch

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.testing.ViewModelHalter
import de.foody.core.testing.nachProzessende
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** „Frei“ mit dem Foto-Knopf: Vorschlag füllt die Felder, Markierung, Prozessende, Abbrechen (Fake-Erkenner). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EintragenFotoTest {
    private val halter = ViewModelHalter()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank

    private val vorschlag = FotoVorschlag("Leberkässemmel", 480, BigDecimal("18.5"), BigDecimal("40"), null)
    private val erkannt = MahlzeitErkenner { ErkennungStatus.Erkannt(vorschlag, 20_000) }

    /** Wie das offene Blatt: Im Test ist die App zu, der Zustand rechnet nur mit Zuseher. */
    private val zuseher = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        zuseher.cancel()
        halter.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(h: SavedStateHandle = SavedStateHandle(), erkenner: MahlzeitErkenner = erkannt, modell: Boolean = true) =
        halter.behalten(
            EintragenViewModel(h, HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), erkenner, testErkennung(modell)),
        ).also { vm ->
            zuseher.launch { vm.zustand.collect {} }
            zuseher.launch { vm.speichernMoeglich.collect {} }
            zuseher.launch { vm.fotoKnopf.collect {} }
        }

    private val EintragenViewModel.e get() = zustand.value.eingaben

    @Test
    fun knopfNurMitModell() {
        assertFalse(vm(modell = false).fotoKnopf.value, "ohne Modell (Tablet) kein Knopf")
        assertTrue(vm().fotoKnopf.value)
    }

    @Test
    fun vorschlagErsetztDieFreienFelderUndLaesstSichEintragen() = runTest {
        val vm = vm()
        vm.oeffnen(heute, Mahlzeit.MITTAGESSEN)
        vm.weg(EintragenWeg.FREI)
        vm.freiName("Brot")
        vm.freiFett("3")

        vm.fotoLauf.erkennen("content://foto")

        assertEquals("Leberkässemmel", vm.e.freiName)
        assertEquals("480", vm.e.freiKcal)
        assertEquals("18,5", vm.e.freiEiweiss)
        assertEquals("40", vm.e.freiKh)
        assertEquals("", vm.e.freiFett, "ohne Fett im Vorschlag bleibt das Feld leer")
        assertTrue(fotoHinweis(vm.e))
        assertTrue(vm.speichernMoeglich.value)

        val eintrag = assertNotNull(db.tagebuchDao().nachId(assertNotNull(vm.speichern())))
        assertEquals(TagebuchArt.FREI, eintrag.art)
        assertEquals("Leberkässemmel", eintrag.name)
        assertEquals(Mahlzeit.MITTAGESSEN.name, eintrag.mahlzeit)
    }

    @Test
    fun markiertBisZurAenderung() {
        val vm = vm()
        vm.oeffnen(heute, Mahlzeit.SNACK)
        vm.weg(EintragenWeg.FREI)
        vm.fotoLauf.erkennen("content://foto")
        val foto = assertNotNull(vm.e.foto)
        assertTrue(wieImFoto(vm.e.freiKcal, foto.kcal))
        assertFalse(wieImFoto(vm.e.freiFett, foto.fett), "leerer Wert wird nicht markiert")

        vm.freiKcal("520")
        assertFalse(wieImFoto(vm.e.freiKcal, foto.kcal))
        assertTrue(fotoHinweis(vm.e), "Name und Makros gleichen noch dem Foto")

        vm.freiName("Semmel")
        vm.freiEiweiss("")
        vm.freiKh("41")
        assertFalse(fotoHinweis(vm.e), "alles geändert: kein Hinweis mehr")
    }

    @Test
    fun ohneVorschlagBleibenDieFelder() {
        val vm = vm(erkenner = { ErkennungStatus.Unlesbar })
        vm.oeffnen(heute, Mahlzeit.SNACK)
        vm.weg(EintragenWeg.FREI)
        vm.freiName("Apfel")
        vm.fotoLauf.erkennen("content://foto")
        assertEquals("Apfel", vm.e.freiName)
        assertNull(vm.e.foto)
        assertNotNull(vm.fotoLauf.zustand.value.meldung)
    }

    @Test
    fun vorschlagUndMarkierungUeberstehenProzessende() {
        val h = SavedStateHandle()
        val vor = vm(h)
        vor.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        vor.weg(EintragenWeg.FREI)
        vor.fotoLauf.erkennen("content://foto")

        val nach = vm(h.nachProzessende(), erkenner = { error("nicht neu gestartet") })
        nach.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        assertEquals(vor.e.foto, nach.e.foto)
        assertEquals("Leberkässemmel", nach.e.freiName)
        assertTrue(fotoHinweis(nach.e))
    }

    @Test
    fun neuesBlattBrichtDieErkennungAb() {
        val vm = vm(erkenner = { awaitCancellation() })
        vm.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        vm.weg(EintragenWeg.FREI)
        vm.fotoLauf.erkennen("content://foto")
        assertTrue(vm.fotoLauf.zustand.value.laeuft)

        // Neuaufbau mit demselben Auftrag (Drehen) lässt sie laufen, ein neues Blatt nicht
        vm.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        assertTrue(vm.fotoLauf.zustand.value.laeuft)
        vm.oeffnen(heute, Mahlzeit.ABENDESSEN, auftrag = "blatt-2")
        assertFalse(vm.fotoLauf.zustand.value.laeuft)
        assertNull(vm.e.foto)
    }
}
