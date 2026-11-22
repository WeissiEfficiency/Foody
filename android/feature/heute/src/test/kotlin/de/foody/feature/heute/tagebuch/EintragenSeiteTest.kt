package de.foody.feature.heute.tagebuch

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.KameraDatei
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.erkennung.FotoGedaechtnis
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ScanTexte
import de.foody.core.scan.ZutatTreffer
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.FotoPortion
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.MitSchrift
import de.foody.feature.heute.bestand
import de.foody.feature.heute.haferflocken
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Seite Eintragen mit Erfassen und Bestätigen: Foto und Scannen starten sofort, Abbrechen lässt den Nutzer, wo er
 * war (beim Sofort-Start geht die Seite zu), Speichern und Zurück kehren zur Herkunft zurück, und der Aufbau ist in allen
 * Stilen derselbe. Kamera und Galerie liefern über [FesteErgebnisse] feste Ergebnisse.
 */
@OptIn(DelicateCoilApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w360dp-h1600dp")
class EintragenSeiteTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tag = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private val ergebnisse = FesteErgebnisse()

    /** Wie oft die Seite fertig gemeldet hat (in der App: zurück zur Herkunft). */
    private var fertig = 0

    /** Stil und Variante des Themes; ein Wechsel setzt den Inhalt nicht neu. */
    private var auswahl by mutableStateOf(FoodyStil.MILCHGLAS to StilVariante.IMMERSIV)

    private val erkannt = MahlzeitErkenner {
        ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18.5"), BigDecimal("40"), null), 20_000)
    }

    /** Stellt den FileProvider nach dem Test wieder her. */
    private lateinit var kameraZurueck: () -> Unit

    @Before
    fun setUp() {
        db = testDatenbank()
        runBlocking { db.bestand() }
        val app = RuntimeEnvironment.getApplication()
        kameraZurueck = kameraUrisOhneFileProvider(app)
        // Fotos ohne Dekodieren (Robolectric kann unter Windows keine Bilder dekodieren)
        val bilder = FakeImageLoaderEngine.Builder().default(ColorImage(android.graphics.Color.rgb(200, 90, 40))).build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).components { add(bilder) }.build())
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
        SingletonImageLoader.reset()
        kameraZurueck()
    }

    private fun vm(
        scan: PackungScan = testScan(),
        erkenner: MahlzeitErkenner = keinErkenner,
        modell: Boolean = true,
        gedaechtnis: FotoGedaechtnis = testGedaechtnis(),
    ) = halter.behalten(
        EintragenViewModel(
            SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, scan, erkenner,
            testErkennung(modell), gedaechtnis, testBls(), ZutatenRepository(db) { 1L },
        ),
    )

    private fun zeigen(vm: EintragenViewModel, sofort: SofortStart? = null, schrift: Float = 1f) {
        val start = EintragenStart(tag, Mahlzeit.MITTAGESSEN, sofort)
        compose.setContent {
            val (stil, variante) = auswahl
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides ergebnisse.besitzer) {
                FoodyTheme(stil = stil, variante = variante, unschaerfe = false) {
                    MitSchrift(schrift) { EintragenRoute(start, "auftrag-1", { fertig++ }, vm) }
                }
            }
        }
    }

    /** Erfassen mit dem Foto-Knopf (er erscheint, sobald die Einstellung gelesen ist). */
    private fun erfassenMitFoto() = compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_FOTO).fetchSemanticsNodes().isNotEmpty() }

    /** Foto mit der Kamera (sie liefert gleich): weiter nach Bestätigen, die Erkennung läuft. */
    private fun fotoMachen() {
        erfassenMitFoto()
        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
    }

    /** Strichcode scannen (der Fake liest gleich): weiter nach Bestätigen. */
    private fun scannen() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_SCANNEN).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ERFASSEN_SCANNEN).performClick()
    }

    private fun zurueck() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun rezeptWaehlen() {
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Reispfanne").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(EINTRAGEN_SPEICHERN).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun anzahl(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Knoten, deren Text [text] enthält (auch mitten im Satz). */
    private fun teilText(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    @Test
    fun fotoKnopfStartetDieKameraSofort() {
        ergebnisse.halten = true
        zeigen(vm())
        erfassenMitFoto()
        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
        compose.runOnIdle { assertIs<ActivityResultContracts.TakePicture>(ergebnisse.gestartet.single()) }
        assertEquals(0, anzahl(ErkennungTexte.KAMERA), "kein Menü")
    }

    /** Zwei schnelle Tipps öffnen die Kamera nur einmal; solange sie offen ist, startet auch die Galerie nicht. */
    @Test
    fun doppeltippStartetNurEineKamera() {
        ergebnisse.halten = true
        zeigen(vm())
        erfassenMitFoto()
        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
        compose.onNodeWithText("Aus der Galerie").performClick()
        compose.runOnIdle { assertEquals(1, ergebnisse.gestartet.size, "${ergebnisse.gestartet}") }
        assertIs<ActivityResultContracts.TakePicture>(ergebnisse.gestartet.single())
    }

    /**
     * Beim Öffnen springt kein Knopf: Bis die Einstellung gelesen ist, steht neben „Scannen“ ein leerer Platz (nicht
     * „Frei eintragen“), die Nebenwege und „Nicht dabei?“ kommen erst danach.
     */
    @Test
    fun fotoKnopfSpringtNicht() {
        compose.mainClock.autoAdvance = false
        zeigen(vm())
        val scannen = compose.onNodeWithTag(ERFASSEN_SCANNEN).getBoundsInRoot()
        assertEquals(0, anzahl("Frei eintragen"), "erstes Bild")
        assertEquals(0, anzahl("Aus der Galerie"))
        assertEquals(0, anzahl("Nicht dabei? Frei eintragen"))
        compose.onNodeWithTag(ERFASSEN_FOTO).assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        erfassenMitFoto()
        assertEquals(0, anzahl("Frei eintragen"))
        assertEquals(1, anzahl("Nicht dabei? Frei eintragen"))
        assertEquals(scannen, compose.onNodeWithTag(ERFASSEN_SCANNEN).getBoundsInRoot(), "„Scannen“ bleibt, wo es war")
        assertEquals(scannen.top, compose.onNodeWithTag(ERFASSEN_FOTO).getBoundsInRoot().top)
    }

    /** Das Aufräumen beim Aufbau lässt das Foto der offenen Kamera und das Vorschaubild liegen, nur Reste gehen weg. */
    @Test
    fun aufraeumenBehaeltOffenesFoto() {
        val ordner = KameraDatei.ordner(RuntimeEnvironment.getApplication(), KameraDatei.MAHLZEIT)
        val offen = File(ordner, "offen.jpg").apply { writeText("jpg") }
        val vorschau = File(ordner, "vorschau.jpg").apply { writeText("jpg") }
        val rest = File(ordner, "rest.jpg").apply { writeText("jpg") }
        val vm = vm()
        // Wie nach einem Prozessende: dieselbe Seite (Auftrag) mit Kamerafoto in Bestätigen und gemerkter Kamera
        vm.oeffnen(tag, Mahlzeit.MITTAGESSEN, auftrag = "auftrag-1")
        vm.fotoGemacht(Uri.fromFile(vorschau).toString(), vorschau.path)
        vm.fotoLauf.kameraGestartet(offen.path)
        zeigen(vm)
        compose.waitUntil(5_000) { !rest.exists() }
        assertTrue(offen.exists(), "Foto der offenen Kamera")
        assertTrue(vorschau.exists(), "Vorschaubild")
    }

    @Test
    fun kameraAbbruchBleibtBeimErfassen() {
        ergebnisse.foto = false
        zeigen(vm())
        erfassenMitFoto()
        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ERFASSEN_SCANNEN).assertExists()
        compose.runOnIdle { assertEquals(0, fertig) }
    }

    @Test
    fun sofortFotoAbbruchSchliesst() {
        ergebnisse.foto = false
        zeigen(vm(), SofortStart.FOTO)
        compose.waitUntil(5_000) { fertig > 0 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, fertig) }
        assertIs<ActivityResultContracts.TakePicture>(ergebnisse.gestartet.single())
        assertEquals(0, anzahl("Scannen"))
    }

    @Test
    fun sofortFotoZeigtBestaetigenMitFortschritt() {
        zeigen(vm(erkenner = { awaitCancellation() }), SofortStart.FOTO)
        compose.warteAufText(ErkennungTexte.LAEUFT)
        compose.onNodeWithText(ErkennungTexte.ABBRECHEN).assertExists()
        compose.onNodeWithContentDescription(VORSCHAU).assertExists()
    }

    @Test
    fun fotoKommtAuchBeiLeererSeiteAn() {
        ergebnisse.halten = true
        val vm = vm(erkenner = { awaitCancellation() })
        zeigen(vm, SofortStart.FOTO)
        compose.waitUntil(5_000) { ergebnisse.gestartet.isNotEmpty() }
        // Die Kamera ist offen, die Seite leer
        compose.onNodeWithTag(ERFASSEN_SCANNEN).assertDoesNotExist()

        // Wie nach einem Prozessende: Der Pfad des Fotos kommt aus dem gesicherten Zustand
        val foto = File(File(RuntimeEnvironment.getApplication().cacheDir, "mahlzeit").apply { mkdirs() }, "vorher.jpg").apply { writeText("jpg") }
        compose.runOnIdle {
            vm.fotoLauf.kameraGestartet(foto.path)
            ergebnisse.liefern()
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(VORSCHAU).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(Uri.fromFile(foto).toString(), vm.zustand.value.eingaben.fotoBild) }
        compose.runOnIdle { assertEquals(0, fertig) }
    }

    @Test
    fun speichernKehrtZurueck() {
        zeigen(vm())
        rezeptWaehlen()
        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).performClick()
        compose.waitUntil(5_000) { fertig > 0 }
        compose.runOnIdle { assertEquals(1, fertig) }
        assertEquals(1, runBlocking { db.tagebuchDao().alle().size })
    }

    @Test
    fun zurueckFuehrtZuErfassenDannHinaus() {
        zeigen(vm())
        rezeptWaehlen()
        zurueck()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_SCANNEN).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, fertig) }

        zurueck()
        compose.runOnIdle { assertEquals(1, fertig) }
    }

    @Test
    fun mahlzeitUeberMenue() {
        zeigen(vm())
        rezeptWaehlen()
        compose.onNodeWithContentDescription("Mahlzeit: Mittagessen, ändern").performScrollTo().performClick()
        compose.onNodeWithText("Snack").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Mahlzeit: Snack, ändern").assertExists()

        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).performClick()
        compose.waitUntil(5_000) { fertig > 0 }
        assertEquals(Mahlzeit.SNACK.name, runBlocking { db.tagebuchDao().alle().single().mahlzeit })
    }

    /** Texte der Knöpfe in Bildschirmreihenfolge (oben nach unten, links nach rechts). */
    private fun knopfTexte(): List<String> =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .fetchSemanticsNodes()
            .filter { SemanticsProperties.Text in it.config }
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
            .map { n -> n.config[SemanticsProperties.Text].joinToString(" ") { it.text } }

    @Test
    fun gleicherAufbauInAllenStilen() {
        val vm = vm()
        zeigen(vm)
        erfassenMitFoto()
        val erwartet = listOf("Foto", "Scannen", "Aus der Galerie", "Nährwerttabelle fotografieren", "Nicht dabei? Frei eintragen")
        val verboten = listOf("Siegel", "Deuten", "Ins Bordbuch", "Eintragen! ✓", "In die Chronik", "Ins Logbuch")
        val fehler = mutableListOf<String>()
        for (stil in FoodyStil.entries) {
            for (variante in StilVariante.entries) {
                auswahl = stil to variante
                compose.waitForIdle()
                val knoepfe = knopfTexte()
                if (knoepfe != erwartet) fehler += "$stil/$variante Erfassen: $knoepfe"
                verboten.filter { teilText(it) > 0 }.forEach { fehler += "$stil/$variante Erfassen zeigt „$it“" }

                rezeptWaehlen()
                if (anzahl("Mahlzeit") != 1) fehler += "$stil/$variante Bestätigen ohne Mahlzeit"
                if (compose.onAllNodes(hasTestTag(EINTRAGEN_SPEICHERN) and hasText("Eintragen")).fetchSemanticsNodes().size != 1) {
                    fehler += "$stil/$variante Bestätigen ohne „Eintragen“"
                }
                verboten.filter { teilText(it) > 0 }.forEach { fehler += "$stil/$variante Bestätigen zeigt „$it“" }
                zurueck()
                compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_SCANNEN).fetchSemanticsNodes().isNotEmpty() }
            }
        }
        assertTrue(fehler.isEmpty(), fehler.joinToString("\n"))
    }

    @Test
    fun ohneModellFreiStattFoto() {
        zeigen(vm(modell = false))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_SCANNEN).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ERFASSEN_FOTO).assertDoesNotExist()
        assertEquals(0, anzahl("Aus der Galerie"))
        assertEquals(0, anzahl("Nicht dabei? Frei eintragen"))
        // „Frei eintragen“ steht oben neben „Scannen“
        val frei = compose.onNodeWithText("Frei eintragen").getBoundsInRoot()
        val scannen = compose.onNodeWithTag(ERFASSEN_SCANNEN).getBoundsInRoot()
        assertEquals(scannen.top, frei.top)
    }

    @Test
    fun scanOhneWerteZeigtTabellenKnoepfe() {
        zeigen(vm(scan = testScan()))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_SCANNEN).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ERFASSEN_SCANNEN).performClick()
        compose.warteAufText(ScanTexte.NICHT_GEFUNDEN)
        compose.onNode(hasSetTextAction() and hasText("Name"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(ScanTexte.FOTO).assertExists()
        compose.onNodeWithText(ScanTexte.GALERIE).assertExists()
        // Eine leere Packung ist noch nichts zum Prüfen
        assertEquals(0, compose.onAllNodes(hasText("bitte prüfen", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun vorschlagInDenFeldern() {
        zeigen(vm(erkenner = erkannt))
        fotoMachen()
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("480")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("18,5")).assertExists()

        // Alles von Hand geändert: kein Hinweis mehr
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel")).performTextReplacement("Semmel")
        compose.onNode(hasSetTextAction() and hasText("480")).performTextReplacement("300")
        compose.onNode(hasSetTextAction() and hasText("18,5")).performTextReplacement("10")
        compose.onNode(hasSetTextAction() and hasText("40")).performTextReplacement("41")
        compose.waitForIdle()
        assertEquals(0, anzahl(ErkennungTexte.BITTE_PRUEFEN))
    }

    @Test
    fun portionsChipsRechnenUm() {
        zeigen(vm(erkenner = erkannt))
        fotoMachen()
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        compose.onNode(hasText("groß") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("672")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("25,9")).assertExists()
    }

    @Test
    fun wieBeimLetztenMal() {
        val gedaechtnis = testGedaechtnis()
        val gespeichert = FotoVorschlag("Leberkässemmel mit Senf", 520, null, null, null)
        runBlocking { gedaechtnis.merken(FotoVorschlag("Leberkässemmel", 480, null, null, null), false, FotoPortion.NORMAL, gespeichert) }
        zeigen(vm(erkenner = erkannt, gedaechtnis = gedaechtnis))
        fotoMachen()
        compose.warteAufText(ErkennungTexte.WIE_LETZTES_MAL)
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel mit Senf")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("520")).assertExists()
    }

    @Test
    fun meldungOhneVorschlag() {
        zeigen(vm(erkenner = { ErkennungStatus.Unlesbar }))
        fotoMachen()
        compose.warteAufText(ErkennungTexte.UNLESBAR)
        compose.onNodeWithContentDescription(VORSCHAU).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Name"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun chipsOhneBasis() {
        zeigen(vm(scan = testScan(packung = haferflocken(basis = null))))
        scannen()
        compose.warteAufText("Werte aus Open Food Facts – bitte prüfen")
        compose.onNode(hasText("g") and isSelected()).assertExists()
        compose.onNode(hasText("ml") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("ml") and isSelected()).assertExists()
        compose.onNodeWithText("je 100 ml").assertExists()
    }

    @Test
    fun imKatalogFokusAufMenge() {
        zeigen(vm(scan = testScan(imKatalog = ZutatTreffer("z-reis", "Reis"))))
        scannen()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("Menge")).fetchSemanticsNodes().isNotEmpty() }
        // Bestätigen mit der Zutat im Titel, der Fokus auf der Menge
        compose.onNodeWithText("Reis").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Menge")).assertIsFocused()
    }

    @Test
    fun schrift130OhneAbschneiden() {
        zeigen(vm(erkenner = erkannt), schrift = 1.3f)
        erfassenMitFoto()
        compose.pruefeNichtsAbgeschnitten()

        compose.onNodeWithTag(ERFASSEN_FOTO).performClick()
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        compose.pruefeNichtsAbgeschnitten()
    }

    private companion object {
        /** Beschreibung des Vorschaubilds in Bestätigen. */
        const val VORSCHAU = "Foto der Mahlzeit"
    }
}
