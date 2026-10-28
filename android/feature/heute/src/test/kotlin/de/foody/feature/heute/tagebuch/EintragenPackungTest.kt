package de.foody.feature.heute.tagebuch

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.scan.ScanAusgang
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.bestand
import de.foody.feature.heute.haferflocken
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** „Frei“ mit gescannter Packung: Übernahme, Häkchen-Vorgabe, Prüfregeln und Speichern über `ausPackung`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EintragenPackungTest {
    private val halter = ViewModelHalter()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var vm: EintragenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        vm = halter.behalten(EintragenViewModel(SavedStateHandle(), HeuteRepository(db), TagebuchRepository(db) { 1L }, db, testScan()))
        vm.oeffnen(heute, Mahlzeit.FRUEHSTUECK)
        vm.weg(EintragenWeg.FREI)
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun katalogMit(name: String) = db.zutatDao().upsert(
        ZutatEntity(id = "z-$name", canonicalName = name, energyKj = BigDecimal("1000"), protein = BigDecimal("9"), createdAt = 1, updatedAt = 1),
    )

    private val e get() = vm.zustand.value.eingaben

    /** Wie ein Bundle beim Prozessende: Jeder Wert muss die Java-Serialisierung überstehen. */
    private fun nachProzessende(h: SavedStateHandle) = SavedStateHandle(
        h.keys().associateWith { k ->
            val bytes = ByteArrayOutputStream().also { aus -> ObjectOutputStream(aus).use { it.writeObject(h.get<Any?>(k)) } }.toByteArray()
            ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        },
    )

    @Test
    fun ueberstehtProzessende() {
        // Foto aus dem Tagebuch: Während die Kamera offen ist, beendet Android die App (R-W7/D1)
        val h = SavedStateHandle()
        val vor = halter.behalten(EintragenViewModel(h, HeuteRepository(db), TagebuchRepository(db) { 1L }, db, testScan()))
        vor.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        vor.weg(EintragenWeg.FREI)
        vor.packungUebernehmen(haferflocken())
        vor.pMenge("60")

        val nach = halter.behalten(EintragenViewModel(nachProzessende(h), HeuteRepository(db), TagebuchRepository(db) { 1L }, db, testScan()))
        // Das Blatt baut sich mit demselben Auftrag wieder auf: Die Eingaben bleiben
        nach.oeffnen(heute, Mahlzeit.SNACK, auftrag = "blatt-1")
        val wieder = nach.zustand.value.eingaben
        assertEquals(EintragenWeg.FREI, wieder.weg)
        assertEquals(haferflocken(), wieder.packung)
        assertEquals("60", wieder.pMenge)
        assertEquals("Haferflocken", wieder.pName)
        assertTrue(nach.speichernMoeglich.value)
    }

    @Test
    fun uebernimmtPackung() {
        val p = haferflocken(name = "  Haferflocken   zart ")
        vm.packungUebernehmen(p)
        assertEquals(p, e.packung)
        assertEquals("Haferflocken zart", e.pName)
        assertEquals("", e.pMenge)
        assertEquals(NutrientBasis.PER_100_G, e.pBasis)
        assertEquals("359", e.pKcal)
        assertEquals("13,5", e.pEiweiss)
        assertEquals("58,7", e.pKh)
        assertEquals("7", e.pFett)
    }

    @Test
    fun ohneNameUndBasis() {
        vm.packungUebernehmen(haferflocken(name = null, basis = null))
        assertEquals("", e.pName)
        // Foto ohne Basis: Vorgabe g
        assertEquals(NutrientBasis.PER_100_G, e.pBasis)
        vm.pBasis(NutrientBasis.PER_100_ML)
        assertEquals(NutrientBasis.PER_100_ML, e.pBasis)
    }

    @Test
    fun nameGekuerzt() {
        vm.packungUebernehmen(haferflocken(name = "a".repeat(250)))
        assertEquals(200, e.pName.length)
    }

    @Test
    fun haekchenNeuAn() = runTest {
        db.bestand()
        vm.packungUebernehmen(haferflocken())
        assertTrue(e.alsZutat)
        assertNull(vm.zustand.first().vorhandeneZutat)
        assertEquals("Als Zutat speichern", haekchenText(null))
    }

    @Test
    fun haekchenVorhandenAus() = runTest {
        katalogMit("Haferflocken")
        vm.packungUebernehmen(haferflocken(name = "haferflocken"))
        assertFalse(e.alsZutat)
        val vorhanden = vm.zustand.first().vorhandeneZutat
        assertEquals("Haferflocken", vorhanden)
        assertEquals("Zutat „Haferflocken“ aktualisieren", haekchenText(vorhanden))
    }

    @Test
    fun haekchenFolgtNameBisNutzer() = runTest {
        katalogMit("Haferflocken")
        vm.packungUebernehmen(haferflocken(name = "Hafer"))
        assertTrue(e.alsZutat)
        vm.pName("Haferflocken")
        assertFalse(e.alsZutat)
        vm.pName("Haferflocken fein")
        assertTrue(e.alsZutat)
        // Eigene Wahl geht vor
        vm.alsZutat(false)
        assertTrue(e.alsZutatVomNutzer)
        vm.pName("Ganz neu")
        assertFalse(e.alsZutat)
    }

    @Test
    fun regeln() = runTest {
        vm.packungUebernehmen(haferflocken())
        vm.pMenge("100")
        assertTrue(vm.speichernMoeglich.first())
        vm.pKcal("")
        assertFalse(vm.speichernMoeglich.first(), "kcal fehlt")
        vm.pKcal("1001")
        assertFalse(vm.speichernMoeglich.first(), "kcal über 1.000")
        vm.pKcal("1000")
        assertTrue(vm.speichernMoeglich.first())
        vm.pEiweiss("101")
        assertFalse(vm.speichernMoeglich.first(), "Makro über 100")
        vm.pEiweiss("")
        assertTrue(vm.speichernMoeglich.first(), "Makro leer ist erlaubt")
        vm.pFett("-1")
        assertFalse(vm.speichernMoeglich.first(), "Makro negativ")
        vm.pFett("7")
        vm.pMenge("0")
        assertFalse(vm.speichernMoeglich.first(), "Menge 0")
        vm.pMenge("50")
        vm.pName("  ")
        assertFalse(vm.speichernMoeglich.first(), "Name fehlt")
        vm.pName("x".repeat(201))
        assertFalse(vm.speichernMoeglich.first(), "Name zu lang")
    }

    @Test
    fun speichernFreiOhneHaekchen() = runTest {
        vm.packungUebernehmen(haferflocken())
        vm.alsZutat(false)
        vm.pMenge("200")
        val eintrag = assertNotNull(db.tagebuchDao().nachId(assertNotNull(vm.speichern())))
        assertEquals(TagebuchArt.FREI, eintrag.art)
        assertEquals("Haferflocken", eintrag.name)
        assertEquals(Mahlzeit.FRUEHSTUECK.name, eintrag.mahlzeit)
        // kcal unverändert → genauer kJ-Wert der Packung × 2
        assertEquals(0, BigDecimal("3004").compareTo(eintrag.energieKj))
        assertEquals(0, BigDecimal("27").compareTo(eintrag.eiweiss))
        assertTrue(db.zutatDao().alle().isEmpty())
    }

    @Test
    fun speichernMitHaekchen() = runTest {
        vm.packungUebernehmen(haferflocken())
        vm.pMenge("50")
        vm.pKcal("360")
        vm.pEiweiss("14")
        val eintrag = assertNotNull(db.tagebuchDao().nachId(assertNotNull(vm.speichern())))
        val zutat = db.zutatDao().alle().single()
        assertEquals("Haferflocken", zutat.canonicalName)
        assertEquals(TagebuchArt.ZUTAT, eintrag.art)
        assertEquals(zutat.id, eintrag.zutatId)
        assertEquals(MeasureUnit.GRAM, eintrag.einheit)
        // kcal geändert → umgerechnet (360 × 4,184); Eiweiß aus dem Feld, Zucker unverändert aus der Packung
        assertEquals(0, BigDecimal("1506.24").compareTo(zutat.energyKj))
        assertEquals(0, BigDecimal("14").compareTo(zutat.protein))
        assertEquals(0, BigDecimal("1.2").compareTo(zutat.sugar))
        assertEquals("4000000000000", zutat.barcode)
        assertEquals("Open Food Facts", zutat.nutrientSource)
    }

    @Test
    fun speichernMlBeiGewaehlterBasis() = runTest {
        vm.packungUebernehmen(haferflocken(name = "Milch", basis = null))
        vm.pBasis(NutrientBasis.PER_100_ML)
        vm.pMenge("200")
        val eintrag = assertNotNull(db.tagebuchDao().nachId(assertNotNull(vm.speichern())))
        assertEquals(MeasureUnit.MILLILITER, eintrag.einheit)
        assertEquals(NutrientBasis.PER_100_ML, db.zutatDao().alle().single().nutrientBasis)
    }

    @Test
    fun imKatalogWechseltZuZutat() = runTest {
        db.bestand()
        vm.imKatalog(ScanAusgang.ImKatalog("z-reis", "Reis", "4000000000000"))
        val z = vm.zustand.first()
        assertEquals(EintragenWeg.ZUTAT, z.eingaben.weg)
        assertEquals("z-reis", z.eingaben.zutatId)
        assertEquals("Reis", z.eingaben.suche)
        assertEquals(listOf("Reis"), z.zutaten.map { it.name })
        // Der Fokus soll auf die Menge
        withTimeout(1_000) { vm.mengeFokus.first() }
    }

    @Test
    fun zurueckZuFrei() {
        vm.packungUebernehmen(haferflocken())
        vm.packungVerwerfen()
        assertNull(e.packung)
        assertEquals(EintragenWeg.FREI, e.weg)
    }

    @Test
    fun doppeltipp() = runTest {
        vm.packungUebernehmen(haferflocken())
        vm.pMenge("100")
        val ids = kotlinx.coroutines.coroutineScope {
            listOf(async { vm.speichern() }, async { vm.speichern() }).map { it.await() }
        }
        assertEquals(1, ids.filterNotNull().size)
        assertEquals(1, db.tagebuchDao().alle().size)
        assertEquals(1, db.zutatDao().alle().size)
    }
}
