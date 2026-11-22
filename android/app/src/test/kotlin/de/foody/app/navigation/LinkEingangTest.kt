package de.foody.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Links über `onNewIntent` gehen nicht verloren: Nach einem Prozessende im Hintergrund baut `singleTask` die Activity
 * neu auf und liefert den Widget-Link noch vor dem ersten Aufbau der Navigation.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class LinkEingangTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val links = LinkEingang()

    private fun link(text: String) = Intent(Intent.ACTION_VIEW, Uri.parse(text))

    /** Wie MainActivity: Die Navigation übernimmt die Links, sobald sie steht. */
    private fun starten() {
        compose.setContent {
            nav = rememberNavController()
            LaunchedEffect(nav) { links.laufendOeffnen(nav) }
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
        compose.waitForIdle()
    }

    /** Die Ziele im Stapel (ohne den Graphen). */
    private fun stapel() = compose.runOnIdle { nav.currentBackStack.value.filterNot { it.destination is NavGraph } }

    @Test
    fun linkVorDemAufbauGenauEinmal() {
        links.ankommen(link(SCAN_LINK))
        assertTrue(links.sofortWartet(), "Heute bleibt verdeckt")
        starten()
        val ziele = stapel()
        assertEquals(2, ziele.size, "Stapel: ${ziele.map { it.destination.route }}")
        assertTrue(ziele[0].destination.hasRoute<Heute>())
        val eintragen = ziele[1].toRoute<Eintragen>()
        assertEquals("SCANNEN", eintragen.sofort)
        assertFalse(links.sofortWartet(), "übernommen")
        // Nichts wartet mehr: ein weiterer Durchgang öffnet nichts neu
        compose.runOnIdle { links.oeffnen(nav) }
        assertEquals(eintragen.auftrag, stapel().last().toRoute<Eintragen>().auftrag)
        assertEquals(2, stapel().size)
    }

    @Test
    fun zweiLinksVorDemAufbauDerReiheNach() {
        links.ankommen(link(SCAN_LINK))
        links.ankommen(link("foody://rezept/r1"))
        starten()
        val ziele = stapel()
        assertEquals(3, ziele.size, "beide geöffnet: ${ziele.map { it.destination.route }}")
        assertEquals("SCANNEN", ziele[1].toRoute<Eintragen>().sofort)
        assertEquals("r1", ziele[2].toRoute<RezeptDetail>().id)
    }

    /** Bei laufender App: zwei Links kurz hintereinander, beide der Reihe nach, jeder einmal. */
    @Test
    fun linksBeiLaufenderAppDerReiheNach() {
        starten()
        compose.runOnIdle {
            links.ankommen(link(FOTO_LINK))
            links.ankommen(link("foody://rezept/r1"))
        }
        val ziele = stapel()
        assertEquals(3, ziele.size, "beide geöffnet: ${ziele.map { it.destination.route }}")
        assertEquals("FOTO", ziele[1].toRoute<Eintragen>().sofort)
        assertEquals("r1", ziele[2].toRoute<RezeptDetail>().id)
    }

    @Test
    fun nurKameraUndScannerVerdecken() {
        links.ankommen(link(EINTRAGEN_LINK))
        links.ankommen(link("foody://rezept/r1"))
        assertFalse(links.sofortWartet())
        links.ankommen(link(SCAN_LINK).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertFalse(links.sofortWartet(), "aus „Zuletzt verwendet“ kein Sofort-Start")
        links.ankommen(link(FOTO_LINK))
        assertTrue(links.sofortWartet())
    }
}
