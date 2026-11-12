package de.foody.feature.kochen

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Referenzbilder des Kochmodus mit einem erfundenen Rezept: Bereitlegen, Schritt mit laufendem Timer und das
 * Fertig-Blatt (eigenes Fenster, darum als ganzer Bildschirm) – hell/dunkel × Handy/Tablet.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: KochTestUmgebung

    @Before
    fun setUp() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        KochTestUmgebung.leeren()
        db = testDatenbank()
        u = KochTestUmgebung(db)
        runBlocking { db.reistopf() }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private val vergleich = FoodyBildvergleich

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = seiten("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = seiten("tablet")

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun fertigHellHandy() = fertig("fertig_hell_handy", dunkel = false)

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun fertigDunkelHandy() = fertig("fertig_dunkel_handy", dunkel = true)

    /** Düstere Sage, Immersiv: „Lege bereit“ und ein Schritt mit Sanduhr. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun sage() {
        val bereit = u.vm().apply { starten("r1", null); abhaken(0) }
        bild("sage_bereitlegen_handy", dunkel = false, stil = FoodyStil.SAGE) { KochenRoute("r1", null, { _, _ -> }, {}, vm = bereit) }
        KochTestUmgebung.leeren()
        val schritt = u.vm().apply { starten("r1", null); abhaken(0); seiteWaehlen(2) }
        bild("sage_schritt_handy", dunkel = false, stil = FoodyStil.SAGE) { KochenRoute("r1", null, { _, _ -> }, {}, vm = schritt) }
        KochTestUmgebung.leeren()
    }

    /** Die Stile R–V, Immersiv: Bereitlegen und ein Schritt mit Timer. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun stileHandy() {
        for (stil in listOf(FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            val name = stil.name.lowercase()
            val bereit = u.vm().apply { starten("r1", null); abhaken(0) }
            bild("stil_${name}_bereitlegen_handy", dunkel = false, stil = stil) { KochenRoute("r1", null, { _, _ -> }, {}, vm = bereit) }
            KochTestUmgebung.leeren()
            val schritt = u.vm().apply { starten("r1", null); abhaken(0); seiteWaehlen(2) }
            bild("stil_${name}_schritt_handy", dunkel = false, stil = stil) { KochenRoute("r1", null, { _, _ -> }, {}, vm = schritt) }
            KochTestUmgebung.leeren()
        }
    }

    private fun seiten(geraet: String) {
        for (dunkel in listOf(false, true)) {
            val modus = if (dunkel) "dunkel" else "hell"
            val bereit = u.vm().apply { starten("r1", null); abhaken(0) }
            bild("bereitlegen_${modus}_$geraet", dunkel) { KochenRoute("r1", null, { _, _ -> }, {}, vm = bereit) }
            KochTestUmgebung.leeren()
            val schritt = u.vm().apply { starten("r1", null); abhaken(0); seiteWaehlen(2) }
            val timer = u.timer.starten("r1", 2, "Reistopf", "20 Minuten", Duration.ofMinutes(20))
            bild("schritt_${modus}_$geraet", dunkel) { KochenRoute("r1", null, { _, _ -> }, {}, vm = schritt) }
            u.timer.entfernen(timer)
            KochTestUmgebung.leeren()
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun fertig(name: String, dunkel: Boolean) {
        val vm = u.vm().apply { starten("r1", null) }
        runBlocking { vm.fertig() }
        compose.setContent { FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { KochenRoute("r1", null, { _, _ -> }, {}, vm = vm) } } }
        compose.warteAufText("Guten Appetit!")
        captureScreenRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich)
    }

    private fun bild(name: String, dunkel: Boolean, stil: FoodyStil = FoodyStil.MILCHGLAS, inhalt: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
            FoodyTheme(dunkel = dunkel, stil = stil, unschaerfe = false) { FoodyHintergrund { inhalt() } }
        }
    }
}
