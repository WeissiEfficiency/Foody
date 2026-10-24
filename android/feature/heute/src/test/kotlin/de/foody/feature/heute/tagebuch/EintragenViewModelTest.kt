package de.foody.feature.heute.tagebuch

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.bestand
import de.foody.feature.heute.planen
import de.foody.feature.heute.testDatenbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EintragenViewModelTest {
    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var vm: EintragenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        vm = EintragenViewModel(HeuteRepository(db), TagebuchRepository(db) { 1L }, db)
        vm.oeffnen(heute, Mahlzeit.ABENDESSEN)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun gespeichert(id: String?) = assertNotNull(db.tagebuchDao().nachId(assertNotNull(id)))

    @Test
    fun rezeptMitPortionen() = runTest {
        db.bestand()
        vm.weg(EintragenWeg.REZEPT)
        vm.rezeptWaehlen("r1")
        vm.portionen("1,5")
        assertTrue(vm.speichernMoeglich.first())
        val e = gespeichert(vm.speichern())
        assertEquals(TagebuchArt.REZEPT, e.art)
        // 3000 kJ je Portion × 1,5
        assertEquals(0, BigDecimal("4500").compareTo(e.energieKj))
        assertEquals(Mahlzeit.ABENDESSEN.name, e.mahlzeit)
        assertEquals(heute, e.datum)
    }

    @Test
    fun zutatMitMengeUndEinheit() = runTest {
        db.bestand()
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("rei")
        assertEquals(listOf("Reis"), vm.zustand.first().zutaten.map { it.name })
        vm.zutatWaehlen("z-reis")
        vm.menge("200")
        vm.einheit(MeasureUnit.GRAM)
        val e = gespeichert(vm.speichern())
        assertEquals(TagebuchArt.ZUTAT, e.art)
        assertEquals(0, BigDecimal("3000").compareTo(e.energieKj))
    }

    @Test
    fun ausPlan() = runTest {
        db.bestand()
        db.planen("p1", heute, "MITTAGESSEN", portionen = 4)
        vm.weg(EintragenWeg.PLAN)
        assertEquals(listOf("p1"), vm.zustand.first().plan.map { it.planEintragId })
        vm.planWaehlen("p1")
        val e = gespeichert(vm.speichern())
        assertEquals("p1", e.planEintragId)
        assertTrue(vm.zustand.first().plan.isEmpty(), "schon eingetragen")
    }

    @Test
    fun freiOhneKcalGesperrt() = runTest {
        vm.weg(EintragenWeg.FREI)
        vm.freiName("Apfel")
        assertFalse(vm.speichernMoeglich.first())
        assertNull(vm.speichern())
        vm.freiKcal("-5")
        assertFalse(vm.speichernMoeglich.first())
        vm.freiKcal("52")
        assertTrue(vm.speichernMoeglich.first())
        vm.freiName("  ")
        assertFalse(vm.speichernMoeglich.first())
    }

    @Test
    fun mengeNullGesperrt() = runTest {
        db.bestand()
        vm.weg(EintragenWeg.ZUTAT)
        vm.zutatWaehlen("z-reis")
        vm.menge("0")
        assertFalse(vm.speichernMoeglich.first())
        vm.menge("abc")
        assertFalse(vm.speichernMoeglich.first())
        vm.menge("0,5")
        assertTrue(vm.speichernMoeglich.first())
    }

    @Test
    fun portionenNullGesperrt() = runTest {
        db.bestand()
        vm.weg(EintragenWeg.REZEPT)
        vm.rezeptWaehlen("r1")
        vm.portionen("0")
        assertFalse(vm.speichernMoeglich.first())
        assertEquals(0, db.tagebuchDao().alle().size)
    }

    @Test
    fun mahlzeitVorbelegt() = runTest {
        vm.oeffnen(heute, Mahlzeit.SNACK)
        assertEquals(Mahlzeit.SNACK, vm.zustand.first().mahlzeit)
        vm.oeffnen(heute, null, uhrzeit = LocalTime.of(8, 0))
        assertEquals(Mahlzeit.FRUEHSTUECK, vm.zustand.first().mahlzeit)
    }

    @Test
    fun sucheHoechstensDreissig() = runTest {
        repeat(40) { db.zutatDao().upsert(ZutatEntity(id = "z$it", canonicalName = "Mehl $it", createdAt = 1, updatedAt = 1)) }
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("MEHL")
        assertEquals(30, vm.zustand.first().zutaten.size)
    }
}
