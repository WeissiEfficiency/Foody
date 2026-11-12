package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.MitSchrift200
import de.foody.core.designsystem.pruefeNichtsAbgeschnitten
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class EntwurfsbausteineTest {
    @get:Rule
    val compose = createComposeRule()

    private val woche = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").mapIndexed { i, k ->
        LeistenTag(k, 26 + i, markiert = i < 3, beschreibung = "$k ${26 + i}.")
    }

    @Test
    fun seitenkopfMitUeberzeileUndZurueck() {
        var zurueck = 0
        compose.setContent { FoodyTheme { Seitenkopf("Tagebuch", ueberzeile = "Samstag, 31. Oktober", onZurueck = { zurueck++ }) } }
        compose.onNodeWithText("SAMSTAG, 31. OKTOBER").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithContentDescription("Zurück").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, zurueck)
    }

    /** Kurze Aktion: Titel und Knopf in einer Zeile. */
    @Test
    fun seitenkopfKurzeAktionNebenDemTitel() {
        compose.setContent {
            FoodyTheme { Seitenkopf("Bearbeiten", Modifier.width(360.dp), onZurueck = {}) { FoodyKnopf("OK", {}) } }
        }
        val titel = compose.onNodeWithText("Bearbeiten").getUnclippedBoundsInRoot()
        val knopf = compose.onNodeWithText("OK").getUnclippedBoundsInRoot()
        assertTrue(knopf.top < titel.bottom, "Knopf liegt neben dem Titel")
    }

    /** Lange Aktion: Der Knopf rückt unter den Titel, statt dessen Wort zu trennen. */
    @Test
    fun seitenkopfLangeAktionRueckUnterDenTitel() {
        compose.setContent {
            FoodyTheme {
                Seitenkopf("Bearbeiten", Modifier.width(360.dp), onZurueck = {}) { FoodyKnopf("Ein sehr langer Speicherknopf", {}) }
            }
        }
        val titel = compose.onNodeWithText("Bearbeiten").getUnclippedBoundsInRoot()
        val knopf = compose.onNodeWithText("Ein sehr langer Speicherknopf").getUnclippedBoundsInRoot()
        assertTrue(knopf.top >= titel.bottom, "Knopf liegt unter dem Titel")
        val zeilen = compose.onNodeWithText("Bearbeiten").fetchSemanticsNode().let { n ->
            val ergebnis = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            n.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(ergebnis)
            ergebnis.first().lineCount
        }
        assertEquals(1, zeilen)
    }

    @Test
    fun runderKnopfMindestens48dp() {
        var klicks = 0
        compose.setContent {
            FoodyTheme {
                Column {
                    RunderKnopf(Icons.Outlined.Add, "Neues Rezept", onKlick = { klicks++ }, primaer = true)
                    RunderKnopf(Icons.Outlined.Add, "Konto", onKlick = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Neues Rezept").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Konto").assertHeightIsAtLeast(48.dp)
        assertEquals(1, klicks)
    }

    @Test
    fun suchfeldNimmtEingabeUndLeert() {
        compose.setContent {
            FoodyTheme {
                var text by remember { mutableStateOf("") }
                Suchfeld(text, { text = it }, "Rezepte, Zutaten …")
            }
        }
        compose.onNodeWithText("Rezepte, Zutaten …").assertExists()
        compose.onNodeWithContentDescription("Rezepte, Zutaten …").performTextInput("Risotto")
        compose.onNodeWithText("Risotto").assertExists()
        compose.onNodeWithContentDescription("Eingabe leeren").performClick()
        compose.onNodeWithText("Rezepte, Zutaten …").assertExists()
    }

    @Test
    fun tagesleisteWaehltTag() {
        var gewaehlt = -1
        compose.setContent { FoodyTheme { Tagesleiste(woche, gewaehlt = 5, onWahl = { gewaehlt = it }) } }
        compose.onNodeWithContentDescription("Sa 31.").assertIsSelected()
        compose.onNodeWithContentDescription("Mo 26.").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, gewaehlt)
    }

    @Test
    fun fabMindestens48dp() {
        var klicks = 0
        compose.setContent { FoodyTheme { FoodyFab("Eintragen", Icons.Outlined.Add, onKlick = { klicks++ }) } }
        compose.onNodeWithText("Eintragen").performClick()
        assertEquals(1, klicks)
    }

    @Test
    fun doppelteSchriftOhneAbschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    Column(Modifier.width(411.dp)) {
                        Seitenkopf("Rezepte", ueberzeile = "6 Rezepte") { RunderKnopf(Icons.Outlined.Add, "Neu", {}, primaer = true) }
                        Suchfeld("", {}, "Suchen")
                        FoodyFab("Eintragen", Icons.Outlined.Add, {})
                        Initiale("Kürbis")
                    }
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }
}
