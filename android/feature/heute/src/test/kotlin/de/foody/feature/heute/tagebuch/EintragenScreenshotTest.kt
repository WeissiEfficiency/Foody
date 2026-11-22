package de.foody.feature.heute.tagebuch

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.testing.FoodyBildvergleich
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.bestand
import de.foody.feature.heute.planen
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
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
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Referenzbilder der Seite Eintragen auf einem kleinen Handy (360 × 800 dp), in jedem Stil (immersiv, hell): Erfassen
 * mit Foto, Scannen, Suche und Geplantem, Bestätigen nach einem Foto (Fake-Erkenner, Hinweis „bitte prüfen“) und
 * Bestätigen mit einem Rezept. Bilder `eintragen_<stil>_<zustand>.png`.
 */
@OptIn(ExperimentalRoborazziApi::class, DelicateCoilApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
class EintragenScreenshotTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val tag = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank

    /** Der Stil des Themes; ein Wechsel setzt den Inhalt nicht neu. */
    private var stil by mutableStateOf(FoodyStil.MILCHGLAS)

    private val erkenner = MahlzeitErkenner {
        ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18"), BigDecimal("40"), BigDecimal("27")), 20_000)
    }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        runBlocking {
            db.bestand()
            db.planen("p1", tag, "MITTAGESSEN")
        }
        // Fotos ohne Dekodieren (Robolectric kann unter Windows keine Bilder dekodieren)
        val bilder = FakeImageLoaderEngine.Builder().default(ColorImage(android.graphics.Color.rgb(200, 90, 40))).build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).components { add(bilder) }.build())
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
        SingletonImageLoader.reset()
    }

    private fun zeigen(): EintragenViewModel {
        val vm = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), erkenner,
                testErkennung(modell = true), testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        val start = EintragenStart(tag, Mahlzeit.MITTAGESSEN)
        compose.setContent {
            FoodyTheme(dunkel = false, stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                FoodyHintergrund { EintragenRoute(start, "auftrag-1", {}, vm) }
            }
        }
        // Erfassen steht, sobald die Einstellung gelesen ist („Foto“ statt des leeren Platzes)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(ERFASSEN_FOTO).fetchSemanticsNodes().isNotEmpty() }
        return vm
    }

    /** Ein Bild je Stil vom jetzigen Zustand. */
    private fun jeStil(zustand: String) {
        FoodyStil.entries.forEach {
            stil = it
            compose.waitForIdle()
            captureScreenRoboImage("src/test/screenshots/eintragen_${it.name.lowercase()}_$zustand.png", roborazziOptions = FoodyBildvergleich)
        }
    }

    @Test
    fun erfassen() {
        zeigen()
        compose.warteAufText("Geplant")
        jeStil("erfassen")
    }

    @Test
    fun bestaetigenFoto() {
        val vm = zeigen()
        compose.runOnIdle { vm.fotoGemacht("content://foto", null) }
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        jeStil("bestaetigen_foto")
    }

    @Test
    fun bestaetigenRezept() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Reispfanne").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(EINTRAGEN_SPEICHERN).fetchSemanticsNodes().isNotEmpty() }
        jeStil("bestaetigen_rezept")
    }
}
