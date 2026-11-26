package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.TitelArt
import de.foody.core.designsystem.stil.stilTitel
import de.foody.core.designsystem.token.UnifrakturFamilie
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Schmuck des Grauens (Plan „Zwei Gesichter“, Schritt 3): Fraktur-Initiale, Trenner und Wasserzeichen ohne Semantik. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GrauenSchmuckTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun frakturNurFuerDenErstenBuchstaben() {
        val t = grauenFraktur("Reispfanne", Color.Red, 26f)
        assertEquals("Reispfanne", t.text)
        val span = t.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(1, span.end)
        assertEquals(UnifrakturFamilie, span.item.fontFamily)
    }

    /** Im Necronomicon stehen Seitentitel in Fraktur, Blatttitel und Ominös bleiben in Kapitälchen. */
    @Test
    fun seitentitelImBuchInFraktur() {
        val familien = mutableMapOf<String, androidx.compose.ui.text.font.FontFamily?>()
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.IMMERSIV) {
                familien["seite"] = stilTitel(TitelArt.SEITE)?.fontFamily
                familien["zurueck"] = stilTitel(TitelArt.SEITE_ZURUECK)?.fontFamily
                familien["blatt"] = stilTitel(TitelArt.BLATT)?.fontFamily
            }
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) {
                familien["ominoes"] = stilTitel(TitelArt.SEITE)?.fontFamily
            }
        }
        compose.waitForIdle()
        assertEquals(UnifrakturFamilie, familien["seite"])
        assertEquals(UnifrakturFamilie, familien["zurueck"])
        assertTrue(familien["blatt"] != UnifrakturFamilie)
        assertTrue(familien["ominoes"] != UnifrakturFamilie)
    }

    @Test
    fun frakturOhneText() {
        assertEquals("", grauenFraktur("", Color.Red, 26f).text)
    }

    /** Trenner und Wasserzeichen sind nur Zeichnung: Im Baum steht allein der Text daneben. */
    @Test
    fun schmuckOhneSemantik() {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.RUHIG) {
                FoodyHintergrund {
                    Box(Modifier.fillMaxSize()) {
                        GrauenTrenner(Color.Green)
                        Text("Inhalt")
                    }
                }
            }
        }
        val knoten = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val alle = generateSequence(listOf(knoten)) { ebene -> ebene.flatMap { it.children }.takeIf { it.isNotEmpty() } }.flatten().toList()
        val mitInhalt = alle.filter { it.config.any() }
        assertEquals(1, mitInhalt.size, "nur der Text: $mitInhalt")
        assertTrue(mitInhalt.single().config.toString().contains("Inhalt"))
    }
}
