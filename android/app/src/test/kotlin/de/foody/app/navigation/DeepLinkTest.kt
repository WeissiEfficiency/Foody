package de.foody.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class DeepLinkTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun oeffnen(link: String) {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
        compose.runOnIdle { assertTrue(nav.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(link)))) }
        compose.waitForIdle()
    }

    @Test
    fun laufendeAppBehaeltDenStapel() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
        compose.runOnIdle { nav.navigate(RezeptDetail("r1")) }
        // Wie der Tipp auf die Timer-Meldung: aus einer Benachrichtigung mit NEW_TASK
        val link = { Intent(Intent.ACTION_VIEW, Uri.parse("foody://kochen/r1?portionen=3")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        compose.runOnIdle { assertTrue(nav.deepLinkOeffnen(link())) }
        compose.onNodeWithText("Rezept r1 · 3 Portionen").assertExists()
        val tiefe = compose.runOnIdle { nav.currentBackStack.value.size }
        compose.runOnIdle { assertTrue(nav.deepLinkOeffnen(link())) }
        compose.runOnIdle {
            assertEquals(tiefe, nav.currentBackStack.value.size, "derselbe Link nicht doppelt")
            nav.popBackStack()
        }
        compose.onNodeWithText("Rezept r1").assertExists()
    }

    @Test
    fun fremderLinkWirdIgnoriert() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
        compose.runOnIdle { assertFalse(nav.deepLinkOeffnen(Intent(Intent.ACTION_VIEW, Uri.parse("foody://gibt-es-nicht/1")))) }
    }

    /**
     * Widget „Scannen“ bei laufender App: zurück zu „Heute“ (wie der Tab), dort eine Scan-Anfrage, die „Heute“ einmal
     * annimmt. Ein zweiter Tipp bringt eine neue Anfrage mit eigener ID.
     */
    @Test
    fun scanLinkFuehrtZuHeuteMitAnfrage() {
        val angenommen = mutableListOf<String>()
        val bildschirme = Bildschirme.platzhalter.mitHeute { _, scan, annehmen ->
            LaunchedEffect(scan) {
                if (scan != null) {
                    angenommen += scan
                    annehmen()
                }
            }
            Platzhalter("Heute")
        }
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, bildschirme) }
        }
        compose.runOnIdle { nav.navigate(RezeptDetail("r1")) }
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(SCAN_LINK))
        compose.runOnIdle { assertTrue(nav.deepLinkOeffnen(link)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<Heute>())
            assertEquals(1, nav.currentBackStack.value.count { it.destination.hasRoute<Heute>() })
            assertEquals(1, angenommen.size)
            assertNull(nav.getBackStackEntry<Heute>().savedStateHandle.get<String>(SCAN_ANFRAGE), "angenommen und weg")
        }
        compose.runOnIdle { assertTrue(nav.deepLinkOeffnen(link)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, angenommen.size)
            assertEquals(2, angenommen.toSet().size, "jeder Tipp eine eigene Anfrage")
        }
    }

    @Test
    fun scanLinkErkannt() {
        assertTrue(Intent(Intent.ACTION_VIEW, Uri.parse("foody://scannen")).istScan())
        assertFalse(Intent(Intent.ACTION_VIEW, Uri.parse("foody://rezept/r1")).istScan())
        assertFalse(Intent(Intent.ACTION_MAIN).istScan())
    }

    @Test
    fun rezeptLink() {
        oeffnen("foody://rezept/r1")
        compose.onNodeWithText("Rezept r1").assertExists()
    }

    @Test
    fun kochenLinkMitPortionen() {
        oeffnen("foody://kochen/r1?portionen=3")
        compose.onNodeWithText("Rezept r1 · 3 Portionen").assertExists()
    }

    @Test
    fun kochenLinkOhnePortionen() {
        oeffnen("foody://kochen/r1")
        compose.onNodeWithText("Rezept r1").assertExists()
    }

    @Test
    fun einkaufLink() {
        oeffnen("foody://einkauf/l1")
        compose.onNodeWithText("Liste l1").assertExists()
    }

    @Test
    fun tagebuchLink() {
        oeffnen("foody://tagebuch/2026-10-20")
        compose.onNodeWithText("Tagebuch 2026-10-20").assertExists()
    }

    @Test
    fun tagebuchLinkKaputtesDatumZeigtHeute() {
        oeffnen("foody://tagebuch/gestern")
        compose.onNodeWithText("Tagebuch ${LocalDate.now()}").assertExists()
    }

    @Test
    fun unbekannteRezeptIdKeinAbsturz() {
        oeffnen("foody://rezept/gibt-es-nicht")
        compose.onNodeWithText("Rezept gibt-es-nicht").assertExists()
    }

    @Test
    fun tagebuchDatumParst() {
        val heute = LocalDate.of(2026, 10, 24)
        assertEquals(LocalDate.of(2026, 2, 28), tagebuchDatum("2026-02-28", heute))
        assertEquals(heute, tagebuchDatum("2026-02-30", heute))
        assertEquals(heute, tagebuchDatum("", heute))
    }
}
