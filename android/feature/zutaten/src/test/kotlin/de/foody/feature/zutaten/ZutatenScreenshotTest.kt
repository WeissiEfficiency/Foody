package de.foody.feature.zutaten

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.domain.NutrientBasis
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Referenzbilder des Zutatenkatalogs: Liste hell/dunkel, Liste ohne Treffer, Editor Handy/Tablet, dazu Vorschau,
 * Zielwahl-Blatt und „In Verwendung“ als ganzer Bildschirm.
 */
@OptIn(ExperimentalRoborazziApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ZutatenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: ZutatenTestUmgebung

    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        u = ZutatenTestUmgebung()
        u.zutat("seed-reis", "Reis") {
            it.copy(
                category = "Trockenwaren", nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1502"),
                protein = BigDecimal("7"), carbs = BigDecimal("78"), fat = BigDecimal("0.6"), densityGPerMl = BigDecimal("0.85"),
                nutrientSource = "Näherungswert (Startdaten)",
            )
        }
        u.zutat("seed-zwiebel", "Zwiebel") { it.copy(category = "Obst & Gemüse", energyKj = BigDecimal("167"), pieceWeightG = BigDecimal("80")) }
        u.zutat("eigen-1", "Zwiebeln") { it.copy(category = "Obst & Gemüse") }
        u.zutat("eigen-2", "Meine Würzmischung") { it.copy(barcode = "4001234567890") }
        u.rezept("r1", "Reispfanne", "seed-reis", "eigen-1")
        u.rezept("r2", "Risotto", "seed-reis")
        u.rezept("r3", "Zwiebelkuchen", "eigen-1")
    }

    @After
    fun tearDown() = u.db.close()

    private fun zeigen(dunkel: Boolean = false, inhalt: @Composable () -> Unit) {
        compose.setContent { FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { inhalt() } } }
    }

    private fun liste(dunkel: Boolean = false) {
        val vm = u.liste()
        zeigen(dunkel) { ZutatenListeRoute(onZutat = {}, onZurueck = {}, vm = vm) }
        compose.warteAufText("Reis")
    }

    private fun editor(id: String) {
        val vm = u.editor()
        zeigen { ZutatEditorRoute(id, onFertig = {}, vm = vm) }
        compose.warteAufText("Name")
        compose.warteAufTeil("Verwendet in")
    }

    private fun bild(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich)
    }

    private fun bildschirm(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich)
    }

    @Test
    fun listeHell() {
        liste()
        bild("liste_hell_handy")
    }

    @Test
    fun listeDunkel() {
        liste(dunkel = true)
        bild("liste_dunkel_handy")
    }

    @Test
    fun listeOhneTreffer() {
        liste()
        compose.onNode(hasSetTextAction() and hasText("Suchen")).performTextInput("Safran")
        compose.warteAufText("Keine Zutat gefunden")
        bild("liste_leer_handy")
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun editorHandy() {
        editor("seed-reis")
        bild("editor_hell_handy")
    }

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun editorTablet() {
        editor("seed-reis")
        bild("editor_hell_tablet")
    }

    @Test
    fun vorschau() {
        liste()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Namen vereinheitlichen").performClick()
        compose.warteAufText("Anwenden")
        bildschirm("vorschau_hell_handy")
    }

    @Test
    fun zielwahl() {
        editor("eigen-1")
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Zusammenführen mit …").performClick()
        compose.warteAufText("Meine Würzmischung")
        bildschirm("zielwahl_hell_handy")
    }

    @Test
    fun inVerwendung() {
        editor("eigen-1")
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText("„Zwiebeln“ löschen?")
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText(inVerwendungText(2))
        bildschirm("inverwendung_hell_handy")
    }
}
