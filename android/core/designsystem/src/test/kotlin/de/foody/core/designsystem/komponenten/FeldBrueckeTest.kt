package de.foody.core.designsystem.komponenten

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Felder mit Wert und Rückruf über [FeldBruecke] (S14): Eingaben, verspätete Werte, Werte von außen. */
@RunWith(AndroidJUnit4::class)
class FeldBrueckeTest {
    @get:Rule
    val compose = createComposeRule()

    /** Der Wert kommt erst später zurück (wie aus einem `StateFlow`): Das Feld springt nicht auf einen alten Stand. */
    @Test
    fun verspaeteterWertSetztDasFeldNichtZurueck() {
        var wert by mutableStateOf("")
        val gemeldet = mutableListOf<String>()
        compose.setContent { FoodyTheme { Suchfeld(wert, { gemeldet += it }, "Suchen") } }
        val feld = compose.onNodeWithContentDescription("Suchen")

        feld.performTextInput("a")
        feld.performTextInput("b")
        assertEquals(listOf("a", "ab"), gemeldet)

        wert = "a"
        compose.waitForIdle()
        feld.assert(eingabe("ab"))
        wert = "ab"
        compose.waitForIdle()
        feld.assert(eingabe("ab"))
    }

    /** Ein neuer Wert von außen (Leeren nach dem Eintragen, nach dem Prozessende zurückgeholt) ersetzt den Text. */
    @Test
    fun wertVonAussenErsetztDenText() {
        var wert by mutableStateOf("Milch")
        compose.setContent { FoodyTheme { Suchfeld(wert, { wert = it }, "Suchen") } }
        val feld = compose.onNodeWithContentDescription("Suchen")
        feld.assert(eingabe("Milch"))

        feld.performTextInput(" und Brot")
        assertEquals("Milch und Brot", wert)
        wert = ""
        compose.waitForIdle()
        feld.assert(eingabe(""))
        wert = "Brot"
        compose.waitForIdle()
        feld.assert(eingabe("Brot"))
    }

    /** Nur der eingegebene Text, ohne den Platzhalter. */
    private fun eingabe(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
}
