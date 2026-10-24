package de.foody.app3.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
            FoodyTheme { Hauptgeruest(nav) }
        }
        compose.runOnIdle { assertTrue(nav.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(link)))) }
        compose.waitForIdle()
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
