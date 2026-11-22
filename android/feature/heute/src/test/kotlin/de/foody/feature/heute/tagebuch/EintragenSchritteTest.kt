package de.foody.feature.heute.tagebuch

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.bls.BlsNachschlag
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ProduktSuche
import de.foody.core.scan.StrichcodeStatus
import de.foody.core.scan.ZutatTreffer
import de.foody.core.testing.ViewModelHalter
import de.foody.core.testing.nachProzessende
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.feature.heute.bestand
import de.foody.feature.heute.haferflocken
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.planen
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Eintragen in zwei Schritten: Erfassen (Suche, Foto, Scan, Plan, Frei) und Bestätigen, mit Zurück und Prozessende. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EintragenSchritteTest {
    private val halter = ViewModelHalter()

    private val tag = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank

    private val erkannt = MahlzeitErkenner {
        ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18.5"), BigDecimal("40"), null), 20_000)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    /** Wie die offene Seite: Zustand und Speichern-Knopf werden gesammelt (mit [zusehen] = false noch nicht). */
    private fun vm(
        h: SavedStateHandle = SavedStateHandle(),
        scan: PackungScan = testScan(),
        erkenner: MahlzeitErkenner = keinErkenner,
        bls: BlsNachschlag = testBls(),
        zusehen: Boolean = true,
    ) = halter.behalten(
        EintragenViewModel(h, HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, scan, erkenner, testErkennung(), testGedaechtnis(), bls, ZutatenRepository(db) { 1L }),
    ).also { if (zusehen) halter.zusehen(it.zustand, it.speichernMoeglich) }

    /** Der Zustand, sobald er mit den Eingaben des Tags rechnet (vorher steht der Anfangswert da). */
    private suspend fun EintragenViewModel.stand() = zustand.first { it.eingaben.datum == tag }

    private suspend fun EintragenViewModel.e() = stand().eingaben

    /** Linsen mit 400 kcal je 100 g und „Linsensuppe“ aus 100 g Linsen für eine Portion: 400 kcal je Portion. */
    private suspend fun linsensuppe() {
        db.zutatDao().upsert(
            ZutatEntity(id = "z-linsen", canonicalName = "Linsen", nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1673.6"), createdAt = 1, updatedAt = 1),
        )
        db.rezeptDao().upsert(
            RezeptEntity(
                id = "r-linsen", name = "Linsensuppe", defaultServings = 1, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l-linsen", "r-linsen", "z-linsen", BigDecimal("100"), MeasureUnit.GRAM, 0, null, false))
    }

    /** Haferflocken mit 370 kcal je 100 g. */
    private suspend fun haferImKatalog() = db.zutatDao().upsert(
        ZutatEntity(id = "z-hafer", canonicalName = "Haferflocken", nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1548.08"), createdAt = 1, updatedAt = 1),
    )

    private val gelesen4001 = testScan(lesen = { StrichcodeStatus.Gelesen("4001") })

    /** Liest „4001“; Open Food Facts antwortet erst mit [antwort]. */
    private fun haengendeSuche(antwort: CompletableDeferred<ProduktSuche.Antwort>) =
        testScan(lesen = { StrichcodeStatus.Gelesen("4001") }, suchen = { antwort.await() })

    private val haferflocken4001 = ProduktSuche.Antwort.Gefunden(haferflocken().copy(strichcode = "4001"))

    @Test
    fun startetBeimErfassen() = runTest {
        val vm = vm()
        assertFalse(vm.zustand.value.eingaben.geoeffnet, "Startwert: Die Seite nimmt die Angaben des Ziels")
        vm.oeffnen(tag, null, LocalTime.of(12, 30))
        val e = vm.e()
        assertTrue(e.geoeffnet)
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
        assertEquals(Mahlzeit.MITTAGESSEN, e.mahlzeit)
        assertNull(e.sofort)
    }

    @Test
    fun mahlzeitAusDerZeile() = runTest {
        val vm = vm()
        vm.oeffnen(tag, Mahlzeit.SNACK, LocalTime.of(8, 0))
        assertEquals(Mahlzeit.SNACK, vm.e().mahlzeit)
    }

    @Test
    fun rezeptFuehrtZuBestaetigenMitKcal() = runTest {
        linsensuppe()
        val vm = vm()
        vm.oeffnen(tag, Mahlzeit.MITTAGESSEN)
        vm.rezeptWaehlen("r-linsen")
        val z = vm.zustand.first { it.kcalDazu != null }
        assertEquals(EintragenSchritt.BESTAETIGEN, z.eingaben.schritt)
        assertEquals("Linsensuppe", z.auswahlName)
        assertEquals(400, z.kcalDazu)

        vm.portionenSchritt(true)
        assertEquals(600, vm.zustand.first { it.eingaben.portionen == "1,5" }.kcalDazu)

        val eintrag = assertNotNull(db.tagebuchDao().nachId(assertNotNull(vm.speichern())))
        assertEquals(0, BigDecimal("1.5").compareTo(eintrag.portionen))
        assertEquals("Linsensuppe", eintrag.name)
    }

    @Test
    fun zutatMitMengeZeigtKcal() = runTest {
        haferImKatalog()
        val vm = vm()
        vm.oeffnen(tag, null)
        // Ohne Suchtext ist die Trefferliste leer: kcal und Name kommen aus dem ganzen Katalog
        vm.zutatWaehlen("z-hafer")
        vm.menge("50")
        val z = vm.zustand.first { it.kcalDazu != null }
        assertEquals(EintragenSchritt.BESTAETIGEN, z.eingaben.schritt)
        assertEquals(MeasureUnit.GRAM, z.eingaben.einheit)
        assertEquals("Haferflocken", z.auswahlName)
        assertEquals(185, z.kcalDazu)
    }

    @Test
    fun gescannteMilchInMilliliter() = runTest {
        db.zutatDao().upsert(
            ZutatEntity(id = "z-milch", canonicalName = "Milch", nutrientBasis = NutrientBasis.PER_100_ML, energyKj = BigDecimal("268"), barcode = "4002", createdAt = 1, updatedAt = 1),
        )
        val vm = vm()
        vm.oeffnen(tag, null)
        vm.zutatWaehlen("z-milch")
        assertEquals(MeasureUnit.MILLILITER, vm.e().einheit)
    }

    @Test
    fun geplantNimmtDieMahlzeitDesPlans() = runTest {
        db.bestand()
        db.planen("p1", tag, "MITTAGESSEN")
        val vm = vm()
        vm.oeffnen(tag, Mahlzeit.ABENDESSEN)
        val geplant = vm.zustand.first { it.plan.isNotEmpty() }.plan.single()

        vm.planWaehlen("p1")
        val z = vm.stand()
        assertEquals(EintragenSchritt.BESTAETIGEN, z.eingaben.schritt)
        assertEquals(EintragenWeg.PLAN, z.eingaben.weg)
        assertEquals(Mahlzeit.MITTAGESSEN, z.mahlzeit)
        assertEquals("Reispfanne", z.auswahlName)
        assertEquals(assertNotNull(geplant.kcalJePortion), z.kcalDazu)

        assertTrue(vm.zurueck())
        assertEquals(Mahlzeit.ABENDESSEN, vm.e().mahlzeit)
    }

    @Test
    fun freiEintragenLeer() = runTest {
        val vm = vm()
        vm.oeffnen(tag, null)
        vm.freiEintragen()
        val e = vm.e()
        assertEquals(EintragenSchritt.BESTAETIGEN, e.schritt)
        assertEquals(EintragenWeg.FREI, e.weg)
        assertEquals("", e.freiName)
        assertEquals("", e.freiKcal)
        assertNull(e.packung)
        assertNull(e.foto)
        assertFalse(vm.speichernMoeglich.value)

        vm.freiName("Apfel")
        vm.freiKcal("52")
        assertTrue(vm.speichernMoeglich.value)
    }

    @Test
    fun fotoGemachtBehaeltDasBildBisZurueck() = runTest {
        val datei = File(File(RuntimeEnvironment.getApplication().cacheDir, "mahlzeit").apply { mkdirs() }, "foto.jpg").apply { writeText("jpg") }
        val uri = Uri.fromFile(datei).toString()
        val vm = vm(erkenner = erkannt)
        vm.oeffnen(tag, Mahlzeit.MITTAGESSEN)

        vm.fotoGemacht(uri, datei.path)
        val e = vm.zustand.first { it.eingaben.freiName.isNotEmpty() }.eingaben
        assertEquals(EintragenSchritt.BESTAETIGEN, e.schritt)
        assertEquals(EintragenWeg.FREI, e.weg)
        assertEquals(uri, e.fotoBild)
        assertEquals("Leberkässemmel", e.freiName)
        assertEquals("480", e.freiKcal)
        assertTrue(datei.exists(), "das Vorschaubild bleibt nach der Erkennung")

        assertTrue(vm.zurueck())
        assertFalse(datei.exists())
        assertNull(vm.e().fotoBild)
    }

    @Test
    fun fotoDateiWegBeimSchliessen() = runTest {
        val datei = File(File(RuntimeEnvironment.getApplication().cacheDir, "mahlzeit").apply { mkdirs() }, "zu.jpg").apply { writeText("jpg") }
        val vm = vm()
        vm.oeffnen(tag, null)
        vm.fotoGemacht(Uri.fromFile(datei).toString(), datei.path)
        halter.schliessen()
        assertFalse(datei.exists())
    }

    @Test
    fun scanGelesenZeigtBestaetigenMitLeererPackung() = runTest {
        val antwort = CompletableDeferred<ProduktSuche.Antwort>()
        val vm = vm(scan = haengendeSuche(antwort))
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()

        // Open Food Facts antwortet noch nicht: Bestätigen ist schon offen, mit der leeren Packung
        val e = vm.e()
        assertEquals(EintragenSchritt.BESTAETIGEN, e.schritt)
        assertEquals(EintragenWeg.FREI, e.weg)
        assertEquals("4001", e.packung?.strichcode)
        assertTrue(assertNotNull(e.packung).werte.isEmpty())
        assertTrue(vm.scanLauf.zustand.laeuft)

        antwort.complete(haferflocken4001)
        val danach = vm.zustand.first { it.eingaben.pName == "Haferflocken" }.eingaben
        assertEquals("359", danach.pKcal)
        assertEquals("13,5", danach.pEiweiss)
        assertEquals(EintragenSchritt.BESTAETIGEN, danach.schritt)
    }

    @Test
    fun getippteMengeBleibtBeiSpaeterAntwort() = runTest {
        val antwort = CompletableDeferred<ProduktSuche.Antwort>()
        val vm = vm(scan = haengendeSuche(antwort))
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        // Bestätigen ist offen, während Open Food Facts noch sucht: Die Menge ist schon getippt
        vm.pMenge("250")

        antwort.complete(haferflocken4001)
        val e = vm.zustand.first { it.eingaben.pName == "Haferflocken" }.eingaben
        assertEquals("250", e.pMenge)
        assertEquals("359", e.pKcal)
        assertEquals("58,7", e.pKh)
    }

    @Test
    fun zurueckBrichtPackungssucheAb() = runTest {
        val antwort = CompletableDeferred<ProduktSuche.Antwort>()
        val vm = vm(scan = haengendeSuche(antwort))
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        assertEquals(EintragenSchritt.BESTAETIGEN, vm.e().schritt)

        assertTrue(vm.zurueck())
        assertFalse(vm.scanLauf.zustand.laeuft)
        // Die späte Antwort holt den Nutzer nicht wieder nach Bestätigen
        antwort.complete(haferflocken4001)
        val e = vm.e()
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
        assertNull(e.packung)
    }

    @Test
    fun scanOhneWerteBehaeltDenStrichcode() = runTest {
        val vm = vm(scan = gelesen4001)
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        val e = vm.e()
        assertEquals(EintragenSchritt.BESTAETIGEN, e.schritt)
        assertEquals(Packungswerte(null, null, emptyMap(), "4001", PackungScan.QUELLE_HAND), e.packung)
        assertTrue(vm.scanLauf.zustand.fotoAnbieten)

        vm.pName("Müsli")
        vm.pMenge("50")
        vm.pKcal("380")
        vm.alsZutat(true)
        assertNotNull(vm.speichern())
        val zutat = db.zutatDao().alle().single()
        assertEquals("Müsli", zutat.canonicalName)
        assertEquals("4001", zutat.barcode)
    }

    @Test
    fun tabellenfotoBehaeltDenGetipptenNamen() = runTest {
        val vm = vm(scan = gelesen4001)
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        vm.pName("Müsli")

        vm.packungUebernehmen(Packungswerte(null, NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(1590)), "4001", PackungScan.QUELLE_FOTO))
        val e = vm.e()
        assertEquals("Müsli", e.pName)
        assertEquals("380", e.pKcal)
    }

    @Test
    fun tabellenfotoBehaeltDenStrichcode() = runTest {
        val vm = vm(scan = gelesen4001)
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        // Der Knopf hat den Code vergessen (etwa nach einem neuen Versuch); die leere Packung trägt ihn noch
        vm.scanLauf.zustand.menueVersuch()
        assertNull(vm.scanLauf.zustand.strichcode)

        vm.packungUebernehmen(Packungswerte(null, NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(1590)), null, PackungScan.QUELLE_FOTO))
        assertEquals("4001", vm.e().packung?.strichcode)

        vm.pName("Müsli")
        vm.pMenge("50")
        vm.alsZutat(true)
        assertNotNull(vm.speichern())
        assertEquals("4001", db.zutatDao().alle().single().barcode)
    }

    @Test
    fun getippteKcalUeberstehenNichtGefunden() = runTest {
        val antwort = CompletableDeferred<ProduktSuche.Antwort>()
        val vm = vm(scan = haengendeSuche(antwort))
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        vm.pKcal("380")

        antwort.complete(ProduktSuche.Antwort.Unbekannt)
        assertTrue(vm.scanLauf.zustand.fotoAnbieten)
        val e = vm.e()
        assertEquals("380", e.pKcal)
        assertEquals("4001", e.packung?.strichcode)
    }

    @Test
    fun zurueckVergisstDieScanMeldung() = runTest {
        val vm = vm(scan = gelesen4001)
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        assertNotNull(vm.scanLauf.zustand.meldung)

        assertTrue(vm.zurueck())
        assertNull(vm.scanLauf.zustand.meldung)
        assertFalse(vm.scanLauf.zustand.fotoAnbieten)
    }

    @Test
    fun neuerAuftragBrichtPackungssucheAb() = runTest {
        val antwort = CompletableDeferred<ProduktSuche.Antwort>()
        val vm = vm(scan = haengendeSuche(antwort))
        vm.oeffnen(tag, null, auftrag = "a1")
        vm.scanLauf.strichcode()
        assertTrue(vm.scanLauf.zustand.laeuft)

        vm.oeffnen(tag, null, auftrag = "a2")
        assertFalse(vm.scanLauf.zustand.laeuft)
        // Die späte Antwort gehört zur alten Seite
        antwort.complete(haferflocken4001)
        val e = vm.e()
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
        assertNull(e.packung)
    }

    @Test
    fun imKatalogFuehrtZurZutat() = runTest {
        db.bestand()
        val vm = vm(scan = testScan(imKatalog = ZutatTreffer("z-reis", "Reis")))
        vm.oeffnen(tag, null)
        vm.scanLauf.strichcode()
        val e = vm.e()
        assertEquals(EintragenSchritt.BESTAETIGEN, e.schritt)
        assertEquals(EintragenWeg.ZUTAT, e.weg)
        assertEquals("z-reis", e.zutatId)
        assertNull(e.packung)
    }

    @Test
    fun abbruchBeimSofortstartSchliesst() = runTest {
        val vm = vm()
        var gesendet = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.schliessen.collect { gesendet++ } }

        vm.oeffnen(tag, null, sofort = SofortStart.FOTO)
        vm.aufnahmeAbgebrochen()
        assertEquals(1, gesendet)
        assertNull(vm.e().sofort)
        vm.aufnahmeAbgebrochen()
        assertEquals(1, gesendet, "nur einmal")

        // Ohne Sofort-Start bleibt Erfassen offen
        vm.oeffnen(tag, null)
        vm.aufnahmeAbgebrochen()
        assertEquals(1, gesendet)
        assertEquals(EintragenSchritt.ERFASSEN, vm.e().schritt)
    }

    @Test
    fun scannerAbbruchBeimSofortstartSchliesst() = runTest {
        val vm = vm(scan = testScan(lesen = { StrichcodeStatus.Abgebrochen }))
        vm.oeffnen(tag, null, sofort = SofortStart.SCANNEN)
        withTimeout(1_000) { vm.schliessen.first() }
    }

    /** Ein normales Öffnen verwirft eine liegengebliebene Kamera-Anfrage. */
    @Test
    fun normalesOeffnenVerwirftKameraAnfrage() = runTest {
        val vm = vm()
        vm.oeffnen(tag, null, auftrag = "a1", sofort = SofortStart.FOTO)
        assertTrue(vm.fotoLauf.zustand.value.kameraAngefragt)
        vm.oeffnen(tag, Mahlzeit.SNACK, auftrag = "a2")
        assertFalse(vm.fotoLauf.zustand.value.kameraAngefragt)
    }

    @Test
    fun zurueckVonBestaetigenNachErfassen() = runTest {
        linsensuppe()
        val vm = vm()
        vm.oeffnen(tag, null)
        vm.suche("Lin")
        vm.rezeptWaehlen("r-linsen")

        assertTrue(vm.zurueck())
        val e = vm.e()
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
        assertNull(e.rezeptId)
        assertEquals("Lin", e.suche)
        assertFalse(vm.zurueck())
    }

    @Test
    fun prozessendeInBeidenSchritten() = runTest {
        haferImKatalog()

        val erfassen = SavedStateHandle()
        vm(erfassen).apply {
            oeffnen(tag, null)
            suche("Lin")
        }
        val e1 = vm(erfassen.nachProzessende()).e()
        assertEquals(EintragenSchritt.ERFASSEN, e1.schritt)
        assertEquals("Lin", e1.suche)

        val zutat = SavedStateHandle()
        vm(zutat).apply {
            oeffnen(tag, null)
            zutatWaehlen("z-hafer")
            menge("50")
        }
        val z2 = vm(zutat.nachProzessende()).zustand.first { it.kcalDazu != null }
        assertEquals(EintragenSchritt.BESTAETIGEN, z2.eingaben.schritt)
        assertEquals("z-hafer", z2.eingaben.zutatId)
        assertEquals("50", z2.eingaben.menge)
        assertEquals(185, z2.kcalDazu)

        val foto = SavedStateHandle()
        vm(foto).apply {
            oeffnen(tag, null)
            fotoGemacht("content://foto", null)
        }
        val e3 = vm(foto.nachProzessende()).e()
        assertEquals(EintragenSchritt.BESTAETIGEN, e3.schritt)
        assertEquals("content://foto", e3.fotoBild)
    }

    /** Nach einem Prozessende in Bestätigen ist schon der Startwert des Zustands Bestätigen: Erfassen blitzt nicht auf. */
    @Test
    fun prozessendeBeginntInBestaetigen() = runTest {
        val h = SavedStateHandle()
        vm(h).apply {
            oeffnen(tag, null)
            freiEintragen()
            freiName("Apfel")
        }
        val start = vm(h.nachProzessende(), zusehen = false).zustand.value.eingaben
        assertEquals(EintragenSchritt.BESTAETIGEN, start.schritt)
        assertEquals("Apfel", start.freiName)
    }

    @Test
    fun prozessendeMitOffenerKamera() = runTest {
        val h = SavedStateHandle()
        val vor = vm(h)
        vor.oeffnen(tag, null, sofort = SofortStart.FOTO)
        vor.fotoLauf.kameraGestartet("/p.jpg")

        val nach = vm(h.nachProzessende())
        assertEquals("/p.jpg", nach.fotoLauf.zustand.value.fotoPfad)
        val e = nach.e()
        assertTrue(e.geoeffnet, "gesichert: kein neues Öffnen nötig")
        assertEquals(SofortStart.FOTO, e.sofort)
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
    }

    @Test
    fun prozessendeImScannerZeigtErfassen() = runTest {
        val h = SavedStateHandle()
        val vor = vm(h, scan = testScan(lesen = { awaitCancellation() }))
        vor.oeffnen(tag, null, sofort = SofortStart.SCANNEN)
        assertEquals(SofortStart.SCANNEN, vor.e().sofort)

        // Das Ergebnis des Google-Scanners kommt nach einem Prozessende nicht mehr an
        val e = vm(h.nachProzessende()).e()
        assertNull(e.sofort)
        assertEquals(EintragenSchritt.ERFASSEN, e.schritt)
    }

    @Test
    fun sucheErstMitText() = runTest {
        db.bestand()
        haferImKatalog()
        db.zutatDao().upsert(
            ZutatEntity(id = "z-drink", canonicalName = "Haferdrink", nutrientBasis = NutrientBasis.PER_100_ML, barcode = "4003", createdAt = 1, updatedAt = 1),
        )
        val vm = vm()
        vm.oeffnen(tag, null)
        val leer = vm.zustand.first { it.eingaben.datum == tag && it.rezepte.isNotEmpty() }
        assertTrue(leer.zutaten.isEmpty())
        assertTrue(leer.bls.isEmpty())

        vm.suche("Hafer")
        val z = vm.zustand.first { it.eingaben.suche == "Hafer" }
        assertEquals(listOf("Haferdrink", "Haferflocken"), z.zutaten.map { it.name })
    }
}
