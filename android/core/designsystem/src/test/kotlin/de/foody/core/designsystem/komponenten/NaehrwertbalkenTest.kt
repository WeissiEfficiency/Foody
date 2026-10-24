package de.foody.core.designsystem.komponenten

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class NaehrwertbalkenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun zahlenDeutsch() {
        assertEquals("1.240", ganzzahlDeutsch(1240))
        assertEquals("0", ganzzahlDeutsch(0))
        assertEquals("1.000.000", ganzzahlDeutsch(1_000_000))
    }

    @Test
    fun anteileNachEnergie() {
        val a = balkenanteile(1240, 2000, 68, 142, 41)
        assertEquals(0.62f, a.eiweiss + a.kh + a.fett, 0.005f)
        // Energie: Eiweiß 272, KH 568, Fett 369 kcal
        assertEquals(272f / 568f, a.eiweiss / a.kh, 0.001f)
        assertEquals(369f / 568f, a.fett / a.kh, 0.001f)
        assertFalse(a.ueberZiel)
    }

    @Test
    fun ohneZielFuellt() {
        val a = balkenanteile(1240, null, 68, 142, 41)
        assertEquals(1f, a.eiweiss + a.kh + a.fett, 0.001f)
    }

    @Test
    fun zielNullKeinAbsturz() {
        val a = balkenanteile(1240, 0, 68, 142, 41)
        assertEquals(1f, a.eiweiss + a.kh + a.fett, 0.001f)
        assertFalse(a.ueberZiel)
    }

    @Test
    fun nichtsGegessen() {
        val a = balkenanteile(0, 2000, 0, 0, 0)
        assertEquals(Balkenanteile(0f, 0f, 0f, false), a)
        compose.setContent { FoodyTheme { Naehrwertbalken(0, 2000, 0, 0, 0) } }
        compose.onNodeWithText("0 / 2.000 kcal").assertExists()
    }

    @Test
    fun ueberZiel() {
        val a = balkenanteile(2300, 2000, 120, 250, 80)
        assertTrue(a.ueberZiel)
        assertEquals(1f, a.eiweiss + a.kh + a.fett, 0.001f)
        compose.setContent { FoodyTheme { Naehrwertbalken(2300, 2000, 120, 250, 80) } }
        compose.onNodeWithText("über Ziel", substring = true).assertExists()
    }

    @Test
    fun beschreibungVollstaendig() {
        compose.setContent { FoodyTheme { Naehrwertbalken(1240, 2000, 68, 142, 41) } }
        compose.onNodeWithText("1.240 / 2.000 kcal").assertExists()
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("1.240 von 2.000 kcal: Eiweiß 68 g, Kohlenhydrate 142 g, Fett 41 g"),
            ),
        ).assertExists()
    }

    @Test
    fun ohneZielText() {
        compose.setContent { FoodyTheme { Naehrwertbalken(1240, null, 68, 142, 41) } }
        compose.onNodeWithText("1.240 kcal").assertExists()
    }
}
