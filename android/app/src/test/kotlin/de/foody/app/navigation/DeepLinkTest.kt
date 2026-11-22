package de.foody.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.feature.heute.tagebuch.SofortStart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
     * Widgets bei laufender App: „Scannen“ öffnet aus einem Rezept heraus Eintragen über „Heute“ (Zurück führt dorthin),
     * gleich mit dem Scanner. Ein zweiter Tipp ersetzt die offene Seite durch eine neue (neuer Auftrag, also ein neuer
     * Scan); „KI-Foto“ beginnt mit der Kamera, „Schnell eintragen“ mit Erfassen.
     */
    @Test
    fun widgetLinksOeffnenEintragenUeberHeute() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
        compose.runOnIdle { nav.navigate(RezeptDetail("r1")) }
        val scan = widgetTippen(SCAN_LINK)
        assertEquals(SofortStart.SCANNEN.name, scan.sofort)
        assertEquals(LocalDate.now().toString(), scan.datum)
        assertNull(scan.mahlzeit, "nach Uhrzeit")
        compose.onNodeWithText("Eintragen ${LocalDate.now()} null SCANNEN").assertExists()
        val nochmal = widgetTippen(SCAN_LINK)
        assertEquals(SofortStart.SCANNEN.name, nochmal.sofort)
        assertNotEquals(scan.auftrag, nochmal.auftrag, "jeder Tipp eine neue Seite")
        assertEquals(SofortStart.FOTO.name, widgetTippen(FOTO_LINK).sofort)
        assertNull(widgetTippen(EINTRAGEN_LINK).sofort, "„Schnell eintragen“ beginnt mit Erfassen")
    }

    /** Tippt ein Widget an (Link bei laufender App): Danach liegt genau eine Seite Eintragen auf „Heute“; ihr Ziel. */
    private fun widgetTippen(link: String): Eintragen {
        compose.runOnIdle { assertTrue(nav.deepLinkOeffnen(Intent(Intent.ACTION_VIEW, Uri.parse(link)))) }
        compose.waitForIdle()
        return compose.runOnIdle {
            val stapel = nav.currentBackStack.value.map { it.destination }.filterNot { it is NavGraph }
            assertEquals(2, stapel.size, "Stapel nach $link: ${stapel.map { it.route }}")
            assertTrue(stapel[0].hasRoute<Heute>())
            assertTrue(stapel[1].hasRoute<Eintragen>())
            nav.currentBackStackEntry!!.toRoute<Eintragen>()
        }
    }

    @Test
    fun widgetLinksErkannt() {
        assertEquals(WidgetStart.SCANNEN, Intent(Intent.ACTION_VIEW, Uri.parse("foody://scannen")).widgetStart())
        assertEquals(WidgetStart.FOTO, Intent(Intent.ACTION_VIEW, Uri.parse("foody://foto")).widgetStart())
        assertEquals(WidgetStart.EINTRAGEN, Intent(Intent.ACTION_VIEW, Uri.parse("foody://eintragen")).widgetStart())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("foody://rezept/r1")).widgetStart())
        assertNull(Intent(Intent.ACTION_MAIN).widgetStart())
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
    fun wocheLink() {
        oeffnen("foody://woche")
        compose.runOnIdle { assertTrue(nav.currentDestination?.hasRoute<Woche>() == true) }
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
