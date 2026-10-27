package de.foody.feature.heute.tagebuch

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.bestand
import de.foody.feature.heute.haferflocken
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Referenzbilder des Blatts „Eintragen“ → „Frei“ mit übernommener Packung (Fake-Scan), hell und dunkel, ganzer Bildschirm:
 * Blatt aufgezogen und bis zum Häkchen „Als Zutat speichern“ gerollt.
 */
@OptIn(ExperimentalRoborazziApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class PackungScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank

    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    @Before
    fun setUp() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        runBlocking { db.bestand() }
    }

    @After
    fun tearDown() = db.close()

    private fun packung(dunkel: Boolean) {
        val vm = EintragenViewModel(HeuteRepository(db), TagebuchRepository(db) { 1L }, db, testScan(packung = haferflocken()))
        val auftrag = BlattAuftrag(Mahlzeit.FRUEHSTUECK, LocalDate.of(2026, 10, 24))
        compose.setContent {
            FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { EintragenBlatt(auftrag, {}, vm) } }
        }
        compose.warteAufText("Frei")
        compose.onNodeWithText("Frei").performClick()
        compose.onNodeWithText("Packung scannen").performScrollTo().performClick()
        compose.onNodeWithText("Strichcode").performClick()
        compose.warteAufText("Werte aus Open Food Facts – bitte prüfen")
        // Menge ohne Tastatur und Fokus (kein blinkender Cursor im Bild), damit die Vorschau „= 359 kcal“ erscheint
        vm.pMenge("100")
        compose.warteAufText("= 359 kcal")
        // Blatt ganz aufziehen und bis zum Häkchen rollen: markierte Werte, Vorschau und Häkchen im Bild
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performSemanticsAction(SemanticsActions.Expand)
        compose.waitForIdle()
        compose.onNodeWithText("Als Zutat speichern").performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/packung_${if (dunkel) "dunkel" else "hell"}_handy.png", roborazziOptions = vergleich)
    }

    @Test
    fun hell() = packung(dunkel = false)

    @Test
    fun dunkel() = packung(dunkel = true)
}
