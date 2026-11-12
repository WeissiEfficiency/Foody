package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.FoodyBildvergleich
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.tagebuch.EintragenViewModel
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import de.foody.feature.heute.tagebuch.TagebuchTagViewModel
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Eintragen-Blatt der Stile R–V in Immersiv: Treffer als Zeilen des Stils (Auswahl für TalkBack), bei 200 % Schrift
 * wird nichts abgeschnitten; Bilder `stil_*_eintragen_handy`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class EintragenStileTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var tagebuch: TagebuchRepository

    @Before
    fun setUp() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        tagebuch = TagebuchRepository(db) { 1L }
        runBlocking {
            tagebuch.frei(heute, Mahlzeit.SNACK, "Apfel", 500, null, null, null)
            for ((id, name) in listOf("z-hafer" to "Haferflocken", "z-milch" to "Hafermilch")) {
                db.zutatDao().upsert(ZutatEntity(id = id, canonicalName = name, energyKj = BigDecimal("1550"), createdAt = 1, updatedAt = 1))
            }
        }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    /** Öffnet das Blatt über „+“ und wechselt zu „Zutat“. */
    private fun oeffnen(stil: FoodyStil, schrift200: Boolean = false) {
        val kontext = RuntimeEnvironment.getApplication()
        val vm = halter.behalten(TagebuchTagViewModel(tagebuch, TageszielSpeicher(kontext)))
        val eintragen = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(), keinErkenner, testErkennung(),
                testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                val inhalt = @Composable { TagebuchTagRoute(heute, onDatum = {}, vm = vm, eintragenVm = eintragen) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Apfel")
        compose.onAllNodes(hasContentDescription("Eintragen") and !isHeading()).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Eintragen") and isHeading()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Zutat").onFirst().performClick()
        compose.warteAufText("Haferflocken")
    }

    private fun waehlen(stil: FoodyStil) {
        oeffnen(stil)
        compose.onNodeWithText("Haferflocken").performScrollTo().performClick()
        compose.onNodeWithText("Haferflocken").assertIsSelected()
    }

    private fun gross(stil: FoodyStil) {
        oeffnen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun anime() = waehlen(FoodyStil.ANIME)

    @Test fun edo() = waehlen(FoodyStil.EDO)

    @Test fun bayrisch() = waehlen(FoodyStil.BAYRISCH)

    @Test fun grauen() = waehlen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = waehlen(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    /** Das Blatt mit gewählter Zutat. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun bild(stil: FoodyStil) {
        waehlen(stil)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/stil_${stil.name.lowercase()}_eintragen_handy.png", roborazziOptions = FoodyBildvergleich)
    }

    @Test fun animeBild() = bild(FoodyStil.ANIME)

    @Test fun edoBild() = bild(FoodyStil.EDO)

    @Test fun bayrischBild() = bild(FoodyStil.BAYRISCH)

    @Test fun grauenBild() = bild(FoodyStil.GRAUEN)

    @Test fun kreuzerBild() = bild(FoodyStil.KREUZER)
}
