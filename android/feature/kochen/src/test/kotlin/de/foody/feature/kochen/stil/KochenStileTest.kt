package de.foody.feature.kochen.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.roemisch
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.kochen.KochTestUmgebung
import de.foody.feature.kochen.KochenRoute
import de.foody.feature.kochen.pruefeNichtsAbgeschnitten
import de.foody.feature.kochen.reistopf
import de.foody.feature.kochen.testDatenbank
import de.foody.feature.kochen.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Kochmodus der Stile R–V in Immersiv: Bereitlegen mit Abhaken, Weiter und Zurück, Schließen, Timer starten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KochenStileTest {
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

    private fun zeigen(stil: FoodyStil) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable { KochenRoute("r1", null, onRezeptAnpassen = { _, _ -> }, onSchliessen = { geschlossen++ }, vm = vm) }
                inhalt()
            }
        }
        warteAufTitel("Bereitlegen")
    }

    private fun warteAufTitel(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(text) and isHeading()).fetchSemanticsNodes().isNotEmpty() }

    private fun ablauf(stil: FoodyStil) {
        zeigen(stil)
        compose.warteAufText("0 von 2 bereit")
        compose.onNodeWithText("Reis").performClick()
        compose.warteAufText("1 von 2 bereit")
        compose.onNodeWithContentDescription("Weiter").performClick()
        warteAufTitel("Schritt 1 von 3")
        compose.onNodeWithText("von 400 g").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()
        warteAufTitel("Bereitlegen")
        compose.onNodeWithContentDescription("Kochen beenden").performClick()
        assertEquals(1, geschlossen)
    }

    private fun timer(stil: FoodyStil) {
        zeigen(stil)
        compose.onNodeWithContentDescription("Weiter").performClick()
        warteAufTitel("Schritt 1 von 3")
        compose.onNodeWithContentDescription("Weiter").performClick()
        warteAufTitel("Schritt 2 von 3")
        compose.onAllNodesWithContentDescription("Timer 20 Minuten starten", substring = true).onFirst().performScrollTo().performClick()
        compose.waitUntil(5_000) { u.timer.timer.value.isNotEmpty() }
    }

    @Test fun anime() = ablauf(FoodyStil.ANIME)

    @Test fun edo() = ablauf(FoodyStil.EDO)

    @Test fun bayrisch() = ablauf(FoodyStil.BAYRISCH)

    @Test fun grauen() = ablauf(FoodyStil.GRAUEN)

    @Test fun kreuzer() = ablauf(FoodyStil.KREUZER)

    @Test fun animeTimer() = timer(FoodyStil.ANIME)

    @Test fun bayrischTimer() = timer(FoodyStil.BAYRISCH)

    @Test fun kreuzerTimer() = timer(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("XIV", roemisch(14))
        assertEquals("T-03", kochArt(FoodyStil.KREUZER).nummer(3, 5))
        assertEquals("T-01", kochArt(FoodyStil.KREUZER).nummer(5, 5))
        assertEquals("PHASE 2 / 5", kochArt(FoodyStil.KREUZER).ueberSchritt(2, 5))
        assertEquals("MISSION · REISTOPF · CREW 2", kochArt(FoodyStil.KREUZER).kopf("Reistopf", 2))
        assertEquals("Reistopf · 2 Portiona", kochArt(FoodyStil.BAYRISCH).kopf("Reistopf", 2))
        // Bayrisch Ruhig wie im Entwurf T-Kochen: „Weiter“ und „Fertig“ statt „Weida“ und „Feddig!“
        assertEquals("Weiter", kochArt(FoodyStil.BAYRISCH, inszeniert = false).weiter)
        assertEquals("Fertig", kochArt(FoodyStil.BAYRISCH, inszeniert = false).fertig)
        // Grauen hat zwei inszenierte Gesichter (ADR 0022): kein schlichter Kochmodus mehr
        assertEquals("Vollenden", kochArt(FoodyStil.GRAUEN, inszeniert = false).fertig)
        assertEquals("III", kochArt(FoodyStil.GRAUEN, inszeniert = false).nummer(3, 5))
        assertEquals("03", kochArt(FoodyStil.KREUZER, inszeniert = false).nummer(3, 5))
        assertEquals("SCHRITT 2 / 5", kochArt(FoodyStil.KREUZER, inszeniert = false).ueberSchritt(2, 5))
        assertEquals("REISTOPF · 2 PORTIONEN", kochArt(FoodyStil.KREUZER, inszeniert = false).kopf("Reistopf", 2))
        assertEquals("Reistopf · 2 Portiona", kochArt(FoodyStil.BAYRISCH, inszeniert = false).kopf("Reistopf", 2))
        assertEquals("3 Port.", kochArt(FoodyStil.ANIME).portionen(3))
    }
}
