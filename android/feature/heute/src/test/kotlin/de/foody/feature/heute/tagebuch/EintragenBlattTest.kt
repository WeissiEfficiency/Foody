package de.foody.feature.heute.tagebuch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.NichtGegessenSpeicher
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.sync.SyncLage
import de.foody.core.sync.SyncLageQuelle
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HeuteRoute
import de.foody.feature.heute.HeuteViewModel
import de.foody.feature.heute.Schnellstart
import de.foody.feature.heute.Zeitgeber
import de.foody.feature.heute.bestand
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Das Eintragen-Blatt aus „Heute“ und der Ändern-Dialog in „Alle Einträge“. Ohne Breiten-Qualifier: Unter Robolectric
 * läuft ein Textfeld in einem eigenen Fenster (Blatt, Dialog) mit `w…dp` endlos (Testumgebung, nicht die App).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EintragenBlattTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var tagebuch: TagebuchRepository

    @Before
    fun setUp() {
        db = testDatenbank()
        tagebuch = TagebuchRepository(db) { 1L }
        runBlocking { db.bestand() }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    // ViewModels außerhalb der Komposition: Sie überstehen den Neuaufbau wie in der App (Hilt-ViewModel)
    private val vm by lazy {
        halter.behalten(
            HeuteViewModel(
                HeuteRepository(db, EmptyCoroutineContext), tagebuch, SyncLageQuelle { flowOf(SyncLage(false, false, null)) },
                TageszielSpeicher(RuntimeEnvironment.getApplication()),
                Zeitgeber { MutableStateFlow(LocalDateTime.of(heute, LocalTime.of(8, 0))) },
                NichtGegessenSpeicher(RuntimeEnvironment.getApplication()),
            ),
        )
    }
    private val eintragen by lazy { halter.behalten(EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(), keinErkenner, testErkennung(), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L })) }

    private val heuteInhalt = @androidx.compose.runtime.Composable {
        FoodyTheme { HeuteRoute({ _, _ -> }, {}, {}, {}, {}, vm = vm, eintragenVm = eintragen) }
    }

    /** „Eintragen“ in der Zeile „Noch kein Snack“ (dritte Mahlzeit) öffnet das Blatt. */
    private fun snackZeileOeffnen() {
        compose.warteAufText("Noch kein Snack")
        compose.onAllNodesWithText("Eintragen")[2].performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aus dem Plan").fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Widget „Scannen“: „Heute“ nimmt die Anfrage an und öffnet das Blatt in „Frei“ mit laufendem Strichcode-Scan.
     * Nach einem Neuaufbau startet der Scan nicht noch einmal.
     */
    @Test
    fun scanAnfrageVomWidget() {
        var gelesen = 0
        val eintragen = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(beimLesen = { gelesen++ }), keinErkenner,
                testErkennung(), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        var anfrage by mutableStateOf<Schnellstart?>(Schnellstart(Schnellstart.Art.SCANNEN, "widget-1"))
        val wiederherstellen = StateRestorationTester(compose)
        wiederherstellen.setContent {
            FoodyTheme {
                HeuteRoute({ _, _ -> }, {}, {}, {}, {}, vm = vm, eintragenVm = eintragen, schnellstart = anfrage, onSchnellstartAngenommen = { anfrage = null })
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Frei") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            assertNull(anfrage)
            assertEquals(1, gelesen)
        }
        wiederherstellen.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Frei") and isSelected()).assertExists()
        compose.runOnIdle { assertEquals(1, gelesen) }
    }

    /**
     * Widget „KI-Foto“: „Heute“ öffnet das Blatt in „Frei“, der Foto-Knopf gleich die Kamera (das Kamerafoto ist
     * gemerkt). Nach einem Neuaufbau geht sie nicht noch einmal auf (dasselbe Foto, keine neue Anfrage).
     */
    @Test
    fun fotoAnfrageVomWidget() {
        // FileProvider vergleicht Pfade mit „/“: Unter Windows findet er den Cache-Ordner nie (läuft in der CI, Linux)
        assumeFalse("FileProvider unter Windows", File.separatorChar == '\\')
        // Er merkt sich seine Ordner statisch (aus dem ersten Test dieser JVM, mit anderem Robolectric-Ordner)
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
        val eintragen = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(), keinErkenner,
                testErkennung(modell = true), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        var anfrage by mutableStateOf<Schnellstart?>(Schnellstart(Schnellstart.Art.FOTO, "widget-2"))
        val wiederherstellen = StateRestorationTester(compose)
        wiederherstellen.setContent {
            FoodyTheme {
                HeuteRoute({ _, _ -> }, {}, {}, {}, {}, vm = vm, eintragenVm = eintragen, schnellstart = anfrage, onSchnellstartAngenommen = { anfrage = null })
            }
        }
        compose.waitUntil(5_000) { eintragen.fotoLauf.zustand.value.fotoPfad != null }
        val foto = eintragen.fotoLauf.zustand.value.fotoPfad
        compose.onNode(hasText("Frei") and isSelected()).assertExists()
        compose.runOnIdle {
            assertNull(anfrage)
            assertFalse(eintragen.fotoLauf.zustand.value.kameraAngefragt)
        }
        wiederherstellen.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Frei") and isSelected()).assertExists()
        compose.runOnIdle { assertEquals(foto, eintragen.fotoLauf.zustand.value.fotoPfad) }
    }

    /** Widget „Schnell eintragen“: das Blatt wie über „+“ – nicht in „Frei“, kein Scan, keine Kamera. */
    @Test
    fun eintragenAnfrageVomWidget() {
        var gelesen = 0
        val eintragen = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(beimLesen = { gelesen++ }), keinErkenner,
                testErkennung(modell = true), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        var anfrage by mutableStateOf<Schnellstart?>(Schnellstart(Schnellstart.Art.EINTRAGEN, "widget-3"))
        compose.setContent {
            FoodyTheme {
                HeuteRoute({ _, _ -> }, {}, {}, {}, {}, vm = vm, eintragenVm = eintragen, schnellstart = anfrage, onSchnellstartAngenommen = { anfrage = null })
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Rezept") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Frei") and isSelected()).assertDoesNotExist()
        compose.runOnIdle {
            assertNull(anfrage)
            assertEquals(0, gelesen)
            assertFalse(eintragen.fotoLauf.zustand.value.kameraAngefragt)
        }
    }

    /** Ein normales Öffnen verwirft eine liegengebliebene Kamera-Anfrage (etwa ohne Modell, wo der Knopf fehlt). */
    @Test
    fun normalesOeffnenVerwirftKameraAnfrage() {
        eintragen.oeffnen(heute, null, auftrag = "a1", start = Schnellstart.Art.FOTO)
        assertTrue(eintragen.fotoLauf.zustand.value.kameraAngefragt)
        eintragen.oeffnen(heute, Mahlzeit.SNACK, auftrag = "a2")
        assertFalse(eintragen.fotoLauf.zustand.value.kameraAngefragt)
    }

    @Test
    fun blattAusZeileVorbelegtFreiErstMitKcal() {
        compose.setContent { heuteInhalt() }
        snackZeileOeffnen()
        compose.onNode(hasText("Snack") and isSelected()).assertExists()
        compose.onNodeWithText("Frei").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Apfel")
        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("kcal")).performTextInput("52")
        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).assertIsEnabled().performClick()
        compose.waitUntil(5_000) { runBlocking { db.tagebuchDao().alle().size == 1 } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aus dem Plan").fetchSemanticsNodes().isEmpty() }
        assertEquals(Mahlzeit.SNACK.name, runBlocking { db.tagebuchDao().alle().single().mahlzeit })
    }

    @Test
    fun blattUeberstehtNeuaufbau() {
        val wiederherstellen = StateRestorationTester(compose)
        wiederherstellen.setContent { heuteInhalt() }
        snackZeileOeffnen()
        compose.onNode(hasSetTextAction() and hasText("Rezept suchen")).performTextInput("Rei")
        wiederherstellen.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(EINTRAGEN_SPEICHERN).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Rei")).assertExists()
    }

    @Test
    fun aendernUeberstehtNeuaufbau() {
        runBlocking { tagebuch.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null) }
        val vm = halter.behalten(TagebuchTagViewModel(tagebuch, TageszielSpeicher(RuntimeEnvironment.getApplication())))
        val wiederherstellen = StateRestorationTester(compose)
        wiederherstellen.setContent { FoodyTheme { TagebuchTagRoute(heute, {}, vm = vm) } }
        compose.warteAufText("Apfel")
        compose.onNodeWithContentDescription("Apfel ändern").performClick()
        compose.warteAufText("Speichern")
        wiederherstellen.emulateSavedInstanceStateRestore()
        compose.warteAufText("Speichern")
    }
}
