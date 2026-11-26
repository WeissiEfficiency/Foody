package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilBlatt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Knöpfe des Grauens ohne Kasten (Plan „Zwei Gesichter“, Schritt 2): Zeichen statt Schrift, 48 dp, TalkBack. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GrauenKnopfTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun zeichenFuerKnopftexte() {
        assertEquals(GrauenZeichen.ZURUECK, grauenZeichenFuer("‹"))
        assertEquals(GrauenZeichen.MENUE, grauenZeichenFuer("⋯"))
        assertEquals(GrauenZeichen.BAND, grauenZeichenFuer("◇"))
        assertEquals(GrauenZeichen.BAND_VOLL, grauenZeichenFuer("♦"))
        assertNull(grauenZeichenFuer("Das Ritual beginnen"))
        assertNull(grauenZeichenFuer("+"))
    }

    /** Vorher stand „‹“ als Schrift in einem Kasten; jetzt ist es gezeichnet, TalkBack hört die Beschreibung. */
    @Test
    fun zeichenKnopfIstGezeichnetMitBeschreibungUnd48dp() {
        var variante by mutableStateOf(StilVariante.RUHIG)
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = variante) {
                Column {
                    GrauenKnopf("‹", { klicks++ }, beschreibung = "Zurück")
                    GrauenKnopf("⋯", { klicks++ }, beschreibung = "Weitere Aktionen")
                    GrauenKnopf("+", { klicks++ }, beschreibung = "Mehr Portionen")
                }
            }
        }
        StilVariante.entries.forEach { v ->
            variante = v
            compose.waitForIdle()
            compose.onAllNodesWithText("‹").assertCountEquals(0)
            compose.onAllNodesWithText("⋯").assertCountEquals(0)
            listOf("Zurück", "Weitere Aktionen", "Mehr Portionen").forEach { b ->
                compose.onNodeWithContentDescription(b).assertHasClickAction()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            }
        }
        assertEquals(6, klicks)
    }

    @Test
    fun siegelUndUmrissZeigenIhrenText() {
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) {
                Column {
                    GrauenKnopf("Das Ritual beginnen", { klicks++ })
                    GrauenKnopf("In die Liste", { klicks++ }, umriss = true)
                }
            }
        }
        listOf("Das Ritual beginnen", "In die Liste").forEach {
            compose.onNodeWithText(it).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(2, klicks)
    }

    @Test
    fun inaktivNichtKlickbar() {
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) {
                GrauenKnopf("‹", { klicks++ }, beschreibung = "Zurück", aktiv = false)
            }
        }
        compose.onNodeWithContentDescription("Zurück").assertIsNotEnabled().performClick()
        assertEquals(0, klicks)
    }

    /** Schritt 1 hatte Chip und Blatt übersehen: In Ominös (Ruhig) fehlte ihr Grauen-Aussehen. */
    @Test
    fun ominoesHatBlattDesGrauens() {
        var blatt: Any? = null
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) { blatt = stilBlatt() }
        }
        compose.waitForIdle()
        assertNotNull(blatt)
    }

    /** Wahlwörter statt Chips mit Rahmen: gewählt meldet TalkBack, 48 dp, beide Gesichter. */
    @Test
    fun chipIstWahlwortInBeidenGesichtern() {
        var variante by mutableStateOf(StilVariante.RUHIG)
        var gewaehlt by mutableStateOf(false)
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = variante) {
                FoodyChip("Mittagessen", gewaehlt, { gewaehlt = !gewaehlt })
            }
        }
        StilVariante.entries.forEach { v ->
            variante = v
            compose.waitForIdle()
            compose.onNodeWithText("Mittagessen").assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Mittagessen").assertIsSelected().performClick()
        }
    }

    @Test
    fun notizknopfOhneKastenMitBeschreibung() {
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) {
                Column {
                    GrauenNotizknopf("+ notieren", { klicks++ }, beschreibung = "Mittag eintragen")
                    GrauenNotizknopf("Gegessen?", { klicks++ })
                }
            }
        }
        // Das „+“ wird zur Feder, das Wort bleibt
        compose.onNodeWithContentDescription("Mittag eintragen").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        compose.onAllNodesWithText("+ notieren").assertCountEquals(0)
        compose.onNodeWithText("Gegessen?").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(2, klicks)
    }
}
