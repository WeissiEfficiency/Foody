package de.foody.feature.heute.tagebuch

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.sync.SyncLage
import de.foody.core.sync.SyncLageQuelle
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HeuteRoute
import de.foody.feature.heute.HeuteViewModel
import de.foody.feature.heute.Zeitgeber
import de.foody.feature.heute.bestand
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

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
                HeuteRepository(db), tagebuch, SyncLageQuelle { flowOf(SyncLage(false, false, null)) },
                TageszielSpeicher(RuntimeEnvironment.getApplication()),
                Zeitgeber { MutableStateFlow(LocalDateTime.of(heute, LocalTime.of(8, 0))) },
            ),
        )
    }
    private val eintragen by lazy { halter.behalten(EintragenViewModel(HeuteRepository(db), tagebuch, db, testScan())) }

    private val heuteInhalt = @androidx.compose.runtime.Composable {
        FoodyTheme { HeuteRoute({ _, _ -> }, {}, {}, {}, {}, vm = vm, eintragenVm = eintragen) }
    }

    /** „Eintragen“ in der Zeile „Noch kein Snack“ (dritte Mahlzeit) öffnet das Blatt. */
    private fun snackZeileOeffnen() {
        compose.warteAufText("Noch kein Snack")
        compose.onAllNodesWithText("Eintragen")[2].performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aus dem Plan").fetchSemanticsNodes().isNotEmpty() }
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
