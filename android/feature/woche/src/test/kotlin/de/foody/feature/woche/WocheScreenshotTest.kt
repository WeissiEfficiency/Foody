package de.foody.feature.woche

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.testing.FoodyBildvergleich
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Referenzbilder des Wochenplans mit erfundenen Rezepten: die Woche (hell/dunkel × Handy/Tablet), dazu Mahlzeit- und
 * Füllen-Blatt auf dem Handy (eigene Fenster, darum als ganzer Bildschirm, je Bild ein Test).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class WocheScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        RuntimeEnvironment.getApplication().getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
            .edit().clear().putInt(Einstellungsschluessel.TAGESZIEL, 2000).commit()
        db = testDatenbank()
        u = WocheTestUmgebung(db)
        runBlocking {
            db.bestand()
            db.hauptspeisen()
            db.planen("p1", MONTAG, "MITTAGESSEN")
            db.planen("p2", MONTAG, "ABENDESSEN")
            db.planen("p3", MITTWOCH, "ABENDESSEN")
            db.planEintragDao().upsert(db.planEintragDao().nachId("p1")!!.copy(cookedAt = 5))
        }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private val vergleich = FoodyBildvergleich

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = woche("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = woche("tablet")

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun mahlzeitHellHandy() = blatt("mahlzeit_hell_handy", dunkel = false, warteAuf = "Rezept ansehen") { compose.onAllNodesWithText("Reispfanne")[0].performClick() }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun mahlzeitDunkelHandy() = blatt("mahlzeit_dunkel_handy", dunkel = true, warteAuf = "Rezept ansehen") { compose.onAllNodesWithText("Reispfanne")[0].performClick() }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun fuellenHellHandy() = blatt("fuellen_hell_handy", dunkel = false, warteAuf = "Neu mischen") { compose.onNodeWithText("Leere Tage füllen").performScrollTo().performClick() }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun fuellenDunkelHandy() = blatt("fuellen_dunkel_handy", dunkel = true, warteAuf = "Neu mischen") { compose.onNodeWithText("Leere Tage füllen").performScrollTo().performClick() }

    private fun woche(geraet: String) {
        for (dunkel in listOf(false, true)) {
            val vm = u.vm()
            bild("woche_${if (dunkel) "dunkel" else "hell"}_$geraet", dunkel) { WocheRoute({ _, _ -> }, {}, vm = vm) }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun blatt(name: String, dunkel: Boolean, warteAuf: String, stil: FoodyStil = FoodyStil.MILCHGLAS, oeffnen: () -> Unit) {
        val vm = u.vm()
        compose.setContent { FoodyTheme(dunkel = dunkel, stil = stil, unschaerfe = false) { FoodyHintergrund { WocheRoute({ _, _ -> }, {}, vm = vm) } } }
        if (stil == FoodyStil.SAGE) compose.warteAufText("KARTE DER WOCHE") else compose.warteAufText("KW 44 · 26.10.–1.11.")
        oeffnen()
        compose.warteAufText(warteAuf)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich)
    }

    /** Düstere Sage, Immersiv: die Karte der Woche. */
    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun sage() {
        val vm = u.vm()
        bild("sage_woche_handy", dunkel = false, stil = FoodyStil.SAGE) { WocheRoute({ _, _ -> }, {}, vm = vm) }
    }

    /** Stile R–V, Immersiv: die Szenen der Woche (heute Mittwoch, Reispfanne am Mittwoch). */
    @Test
    @Config(qualifiers = "w411dp-h1100dp")
    fun stileHandy() {
        for (stil in listOf(FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            val vm = u.vm()
            bild("stil_${stil.name.lowercase()}_woche_handy", dunkel = false, stil = stil) { WocheRoute({ _, _ -> }, {}, vm = vm) }
        }
    }

    private fun bild(name: String, dunkel: Boolean, stil: FoodyStil = FoodyStil.MILCHGLAS, inhalt: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
            FoodyTheme(dunkel = dunkel, stil = stil, unschaerfe = false) { FoodyHintergrund { inhalt() } }
        }
    }

    /** Düstere Sage, Immersiv: „Leere Tage bestücken“. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun sageFuellenHandy() = blatt("sage_fuellen_handy", dunkel = false, warteAuf = "Neu mischen", stil = FoodyStil.SAGE) {
        compose.onNodeWithContentDescription("Leere Tage füllen").performScrollTo().performClick()
    }
}
