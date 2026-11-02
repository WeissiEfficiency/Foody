package de.foody.feature.heute.tagebuch

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.testing.FoodyBildvergleich
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Referenzbilder des Blatts „Eintragen“ → „Frei“ nach „Foto erkennen“ (Fake-Erkenner), hell und dunkel, ganzer
 * Bildschirm: beide Knöpfe, Hinweis „bitte prüfen“ und die markierten Felder.
 */
@OptIn(ExperimentalRoborazziApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FotoScreenshotTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank

    private val vergleich = FoodyBildvergleich

    private val erkenner = MahlzeitErkenner {
        ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18"), BigDecimal("40"), BigDecimal("27")), 20_000)
    }

    @Before
    fun setUp() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun vorschlag(dunkel: Boolean) {
        val vm = halter.behalten(
            EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), erkenner, testErkennung(modell = true), testGedaechtnis()),
        )
        val auftrag = BlattAuftrag(Mahlzeit.MITTAGESSEN, LocalDate.of(2026, 10, 24))
        compose.setContent {
            FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { EintragenBlatt(auftrag, {}, vm) } }
        }
        compose.warteAufText("Frei")
        compose.onNodeWithText("Frei").performClick()
        compose.warteAufText(ErkennungTexte.KNOPF)
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        // Blatt ganz aufziehen: Knöpfe, Hinweis und alle markierten Felder im Bild
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).performSemanticsAction(SemanticsActions.Expand)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/foto_${if (dunkel) "dunkel" else "hell"}_handy.png", roborazziOptions = vergleich)
    }

    @Test
    fun hell() = vorschlag(dunkel = false)

    @Test
    fun dunkel() = vorschlag(dunkel = true)
}
