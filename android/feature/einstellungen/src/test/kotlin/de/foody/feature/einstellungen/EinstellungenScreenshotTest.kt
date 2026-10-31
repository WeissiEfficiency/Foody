package de.foody.feature.einstellungen

import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.db.ZutatEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Referenzbilder der Einstellungsseite: nicht verbunden und verbunden (gesperrt) – hell/dunkel × Handy/Tablet –, dazu
 * die Rückfrage „Alle Daten löschen?“ als ganzer Bildschirm. Drei Zutaten im Katalog (Zeile „Zutatenkatalog 3“).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinstellungenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: EinstellungenTestUmgebung

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        u = EinstellungenTestUmgebung()
        u.ziel.setzen(2000)
        runBlocking {
            listOf("Reis", "Zwiebel", "Haferflocken").forEachIndexed { i, n ->
                u.db.zutatDao().upsert(ZutatEntity(id = "z$i", canonicalName = n, createdAt = 1, updatedAt = 1))
            }
        }
    }

    /** ViewModel mit fertigem Zustand: Die Katalog-Anzahl muss vor der Aufnahme da sein, sonst fehlt sie im Bild. */
    private fun vm(): EinstellungenViewModel = u.vm().also {
        // SolangeAppOffen startet über die Hauptschleife
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, it.zustand.value.zutaten)
    }

    @After
    fun tearDown() = u.schliessen()

    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    private val keineDateien: @Composable ((android.net.Uri) -> Unit, (android.net.Uri) -> Unit) -> Dateiwahl = { _, _ ->
        remember { Dateiwahl({}, {}) }
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = alle("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = alle("tablet")

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun loeschenRueckfrageHandy() {
        val vm = vm()
        compose.setContent {
            FoodyTheme(dunkel = false, unschaerfe = false) {
                FoodyHintergrund { EinstellungenRoute({}, {}, {}, vm = vm, dateiwahl = keineDateien) }
            }
        }
        compose.warteAufText("Alle Daten löschen")
        compose.onNodeWithText("Alle Daten löschen").performScrollTo().performClick()
        compose.warteAufText("Alle Daten löschen?")
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/loeschen_hell_handy.png", roborazziOptions = vergleich)
    }

    private fun alle(geraet: String) {
        for (verbunden in listOf(false, true)) {
            if (verbunden) u.verbinden()
            for (dunkel in listOf(false, true)) {
                val vm = vm()
                val name = "${if (verbunden) "verbunden" else "getrennt"}_${if (dunkel) "dunkel" else "hell"}_$geraet"
                captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
                    FoodyTheme(dunkel = dunkel, unschaerfe = false) {
                        FoodyHintergrund { EinstellungenRoute({}, {}, {}, vm = vm, dateiwahl = keineDateien) }
                    }
                }
            }
        }
    }
}
