package de.foody.feature.einkauf

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Referenzbilder des Einkaufs mit erfundenen Daten: die Liste (hell/dunkel × Handy/Tablet) mit Hinweis, gekauftem Reis
 * und eigener Milch, die leere Liste, dazu Vorschau-, Abgleich- und Detail-Blatt auf dem Handy (eigene Fenster, darum als
 * ganzer Bildschirm, je Bild ein Test).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        u = EinkaufTestUmgebung(db)
        runBlocking { db.einkaufBestand() }
    }

    @After
    fun tearDown() = db.close()

    /** Liste aus Curry (Mo) und Reispfanne (Mi), Reis gekauft, Milch von Hand, danach Curry für 4 (Hinweis). */
    private fun liste() = runBlocking {
        db.planen("s1", MONTAG, "c")
        db.planen("s2", MITTWOCH, "p")
        val id = u.repo.ausPlanAnlegen(MONTAG, MONTAG.plusDays(6), emptySet())!!
        u.repo.antippen(id, "Milch", "Milch & Käse")
        u.repo.eingeben(id, "Grillkohle")
        u.repo.abhaken(db.einkaufsartikelDao().nachListe(id).single { it.name == "Reis" }.id, true)
        db.planen("s1", MONTAG, "c", portionen = 4)
    }

    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = alle("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = alle("tablet")

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun leerHandy() {
        for (dunkel in listOf(false, true)) {
            val vm = u.vm()
            bild("leer_${if (dunkel) "dunkel" else "hell"}_handy", dunkel) { EinkaufRoute(null, {}, {}, vm = vm) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun vorschauHellHandy() = blatt("vorschau_hell_handy", dunkel = false, warteAuf = "Liste anlegen (2)", mitListe = false) {
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun vorschauDunkelHandy() = blatt("vorschau_dunkel_handy", dunkel = true, warteAuf = "Liste anlegen (2)", mitListe = false) {
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun abgleichHellHandy() = blatt("abgleich_hell_handy", dunkel = false, warteAuf = "Übernehmen (2)") {
        compose.onNodeWithText("Plan geändert – abgleichen").performClick()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun abgleichDunkelHandy() = blatt("abgleich_dunkel_handy", dunkel = true, warteAuf = "Übernehmen (2)") {
        compose.onNodeWithText("Plan geändert – abgleichen").performClick()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun detailHellHandy() = blatt("detail_hell_handy", dunkel = false, warteAuf = "Speichern") {
        compose.onNodeWithText("Zwiebeln").performTouchInput { longClick() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun detailDunkelHandy() = blatt("detail_dunkel_handy", dunkel = true, warteAuf = "Speichern") {
        compose.onNodeWithText("Zwiebeln").performTouchInput { longClick() }
    }

    private fun alle(geraet: String) {
        liste()
        for (dunkel in listOf(false, true)) {
            val vm = u.vm()
            bild("liste_${if (dunkel) "dunkel" else "hell"}_$geraet", dunkel) { EinkaufRoute(null, {}, {}, vm = vm) }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun blatt(name: String, dunkel: Boolean, warteAuf: String, mitListe: Boolean = true, oeffnen: () -> Unit) {
        if (mitListe) liste() else runBlocking { db.planen("s2", MITTWOCH, "p"); db.planen("s3", MITTWOCH.plusDays(2), "c") }
        val vm = u.vm()
        compose.setContent { FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { EinkaufRoute(null, {}, {}, vm = vm) } } }
        compose.warteAufText(if (mitListe) "Zwiebeln" else "Noch nichts auf der Liste")
        oeffnen()
        compose.warteAufText(warteAuf)
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich)
    }

    private fun bild(name: String, dunkel: Boolean, inhalt: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
            FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { inhalt() } }
        }
    }
}
