package de.foody.feature.kochen

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KochenScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: KochTestUmgebung
    private var geschlossen = 0

    @Before
    fun setUp() {
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

    private val angepasst = mutableListOf<Pair<String, Int>>()

    private fun zeigen(id: String = "r1", schrift200: Boolean = false, portionen: Int? = null) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    KochenRoute(id, portionen, onRezeptAnpassen = { r, p -> angepasst += r to p }, onSchliessen = { geschlossen++ }, vm = vm)
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    @Test
    fun bereitlegenAbhaken() {
        zeigen()
        compose.warteAufText("0 von 2 bereit")
        compose.onNodeWithText("Reis").performClick()
        compose.warteAufText("1 von 2 bereit")
    }

    @Test
    fun weiterZuSchritt() {
        zeigen()
        compose.warteAufText("Bereitlegen")
        compose.onNodeWithText("Weiter").performClick()
        compose.warteAufText("Schritt 1 von 3")
        // Augenbraue des Designsystems: in Großbuchstaben
        compose.onNodeWithText("DU BRAUCHST").assertExists()
        compose.onNodeWithText("von 400 g").assertExists()
    }

    @Test
    fun timerStartenZeigtLeiste() {
        zeigen()
        compose.warteAufText("Bereitlegen")
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.warteAufText("Schritt 2 von 3")
        compose.onNodeWithText("▶ 20 Minuten").performClick()
        compose.waitUntil(5_000) { u.timer.timer.value.isNotEmpty() }
        compose.warteAufText("20:00")
        compose.onNodeWithContentDescription("Timer 20 Minuten beenden").performClick()
        compose.waitUntil(5_000) { u.timer.timer.value.isEmpty() }
    }

    private fun bisFertig() {
        compose.warteAufText("Bereitlegen")
        repeat(3) { compose.onNodeWithText("Weiter").performClick() }
        compose.warteAufText("Schritt 3 von 3")
        compose.onNodeWithText("Fertig").performClick()
        compose.warteAufText("Guten Appetit!")
    }

    @Test
    fun fertigBlattMitFoto() {
        zeigen()
        bisFertig()
        compose.onNodeWithText("Foto aufnehmen").assertExists()
        compose.onNodeWithText("Schließen").performClick()
        assertEquals(1, geschlossen)
    }

    @Test
    fun fertigBlattOhneFotoKnopf() {
        runBlocking { db.rezeptDao().bildSetzen("r1", "file:///foto.jpg", 2) }
        zeigen()
        bisFertig()
        compose.onNodeWithText("Foto aufnehmen").assertDoesNotExist()
    }

    @Test
    fun rezeptAnpassen() {
        zeigen(portionen = 3)
        bisFertig()
        compose.onNodeWithText("War etwas anders? Rezept anpassen").performClick()
        assertEquals(listOf("r1" to 3), angepasst)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletSeitenliste() {
        zeigen()
        compose.warteAufText("Bereitlegen")
        compose.onNodeWithText("Schritt 2").performClick()
        compose.warteAufText("Schritt 2 von 3")
    }

    @Test
    fun nichtGefundenZurueck() {
        zeigen(id = "gibt-es-nicht")
        compose.warteAufText("Rezept nicht gefunden")
        compose.onNodeWithText("Zurück").performClick()
        assertEquals(1, geschlossen)
    }

    @Test
    fun schliessenSymbol() {
        zeigen()
        compose.warteAufText("Bereitlegen")
        compose.onNodeWithContentDescription("Kochen beenden").performClick()
        assertEquals(1, geschlossen)
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.warteAufText("Bereitlegen")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun ueberstehtDrehen() {
        val vm = u.vm()
        val tester = StateRestorationTester(compose)
        tester.setContent { FoodyTheme { KochenRoute("r1", null, { _, _ -> }, {}, vm = vm) } }
        compose.warteAufText("Bereitlegen")
        compose.onNodeWithText("Weiter").performClick()
        compose.warteAufText("Schritt 1 von 3")
        tester.emulateSavedInstanceStateRestore()
        compose.warteAufText("Schritt 1 von 3")
    }
}
