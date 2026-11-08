package de.foody.feature.heute.tagebuch

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.bestand
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.planen
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EintragenViewModelTest {
    private val halter = ViewModelHalter()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var vm: EintragenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        vm = halter.behalten(EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), keinErkenner, testErkennung(), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L }))
        vm.oeffnen(heute, Mahlzeit.ABENDESSEN)
    }

    @After
    fun tearDown() {
        halter.schliessen()
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

    @Test
    fun zahlenAusserhalbDerGrenzenGesperrt() {
        fun r(p: String) = gueltig(Eingaben(weg = EintragenWeg.REZEPT, rezeptId = "r1", portionen = p))
        assertFalse(r("1e999999999"), "Exponent")
        assertFalse(r("0,0000000000000000000001"), "mehr als 20 Nachkommastellen")
        assertFalse(r("1234567890123456789012345678901"), "mehr als 30 Stellen")
        assertTrue(r("1,5"))
        fun f(name: String) = gueltig(Eingaben(weg = EintragenWeg.FREI, freiName = name, freiKcal = "50"))
        assertFalse(f("x".repeat(201)), "Name länger als 200 Zeichen")
        assertTrue(f("Apfel"))
    }

    private fun mitBls(vararg zeilen: String) = halter.behalten(
        EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), keinErkenner, testErkennung(), testGedaechtnis(), testBls(*zeilen), ZutatenRepository(db) { 1L }),
    ).also { it.oeffnen(heute, Mahlzeit.ABENDESSEN) }

    @Test
    fun blsTrefferWirdZutatUndStueckZaehlt() = runTest {
        val vm = mitBls("B723000	Laugengebäck mit Hagelsalz	1191	9.132	50	1.4	4.113	3.746	2.81")
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("Brezn")
        val treffer = vm.zustand.first().bls.single()
        assertEquals("Breze", treffer.vorschlagName)
        vm.blsWaehlen(treffer).join()
        val z = vm.zustand.first()
        val zutat = assertNotNull(db.zutatDao().nachId(assertNotNull(z.eingaben.zutatId)))
        assertEquals("Breze", zutat.canonicalName)
        assertEquals("BLS 4.0 (MRI): Laugengebäck mit Hagelsalz", zutat.nutrientSource)
        assertEquals(0, BigDecimal("80").compareTo(zutat.pieceWeightG))
        assertEquals(MeasureUnit.PIECE, z.eingaben.einheit)
        // Jetzt im Katalog: Er zeigt sie, der BLS-Abschnitt nicht mehr
        assertEquals(listOf("Breze"), z.zutaten.map { it.name })
        assertTrue(z.bls.isEmpty())
        vm.menge("1")
        val e = gespeichert(vm.speichern())
        // 1 Stück = 80 g, 1191 kJ je 100 g
        assertEquals(0, BigDecimal("952.8").compareTo(e.energieKj))
    }

    @Test
    fun blsGetraenkRechnetInMilliliter() = runTest {
        val vm = mitBls("P162000	Export hell	174	0.485	2.238	0.172		0	0.004")
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("Export")
        vm.blsWaehlen(vm.zustand.first().bls.single()).join()
        val z = vm.zustand.first()
        val zutat = assertNotNull(db.zutatDao().nachId(assertNotNull(z.eingaben.zutatId)))
        // Dichte aus den Bestandteilen (BlsDichte), Einheit gleich ml
        assertEquals(0, BigDecimal("1.00").compareTo(zutat.densityGPerMl))
        assertEquals(MeasureUnit.MILLILITER, z.eingaben.einheit)
        vm.menge("500")
        val e = gespeichert(vm.speichern())
        // 500 ml = 500 g, 174 kJ je 100 g
        assertEquals(0, BigDecimal("870").compareTo(e.energieKj))
    }

    @Test
    fun blsTrefferMitVorhandenemNamenNimmtDieZutat() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "z-breze", canonicalName = "Breze", createdAt = 1, updatedAt = 1))
        val vm = mitBls("B723000	Laugengebäck mit Hagelsalz	1191						")
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("Laugengebäck")
        // Der Katalog hat „Breze“ schon: kein BLS-Treffer, der doppelt anlegte
        assertTrue(vm.zustand.first().bls.isEmpty())
        vm.blsWaehlen(de.foody.domain.BlsSuche.treffer(de.foody.domain.BlsLebensmittel("B723000", "Laugengebäck mit Hagelsalz", emptyMap()))).join()
        assertEquals("z-breze", vm.zustand.first().eingaben.zutatId)
        assertEquals(1, db.zutatDao().alle().size)
    }

    @Test
    fun blsNurImWegZutat() = runTest {
        val vm = mitBls("B723000	Laugengebäck mit Hagelsalz	1191						")
        vm.weg(EintragenWeg.REZEPT)
        vm.suche("Breze")
        assertTrue(vm.zustand.first().bls.isEmpty())
    }

    @Test
    fun katalogsucheKenntSynonyme() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "z-b", canonicalName = "Brötchen", createdAt = 1, updatedAt = 1))
        vm.weg(EintragenWeg.ZUTAT)
        vm.suche("Semmel")
        assertEquals(listOf("Brötchen"), vm.zustand.first().zutaten.map { it.name })
    }

    /** Schreibzugriffe scheitern (wie bei einem Datenbankfehler), Lesen geht weiter. */
    private fun nurLesen(an: Boolean) = db.openHelper.writableDatabase.query("PRAGMA query_only = ${if (an) 1 else 0}").close()

    @Test
    fun fehlerBeimSpeichernGibtSpeichernFrei() = runTest {
        // Scheitert das Schreiben, bleibt „Speichern“ nicht gesperrt (N2)
        db.bestand()
        vm.weg(EintragenWeg.REZEPT)
        vm.rezeptWaehlen("r1")
        nurLesen(true)
        assertFails { vm.speichern() }
        nurLesen(false)
        assertTrue(vm.speichernMoeglich.first())
        gespeichert(vm.speichern())
    }

    @Test
    fun doppeltippLegtNurEinenEintragAn() = runTest {
        db.bestand()
        vm.weg(EintragenWeg.REZEPT)
        vm.rezeptWaehlen("r1")
        val ids = kotlinx.coroutines.coroutineScope {
            listOf(async { vm.speichern() }, async { vm.speichern() }).map { it.await() }
        }
        assertEquals(1, ids.filterNotNull().size)
        assertEquals(1, db.tagebuchDao().alle().size)
    }
}
