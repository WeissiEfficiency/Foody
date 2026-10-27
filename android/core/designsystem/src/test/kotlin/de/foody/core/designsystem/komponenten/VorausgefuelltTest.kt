package de.foody.core.designsystem.komponenten

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyFarben
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Vorausgefüllte Felder (Packung scannen): Rahmen und Beschriftung in der Akzentfarbe. */
@RunWith(AndroidJUnit4::class)
class VorausgefuelltTest {
    @get:Rule
    val compose = createComposeRule()

    private fun farben(an: Boolean, dunkel: Boolean = false): Pair<TextFieldColors, TextFieldColors> {
        lateinit var ergebnis: Pair<TextFieldColors, TextFieldColors>
        compose.setContent {
            FoodyTheme(dunkel = dunkel) { ergebnis = vorausgefuelltFarben(an) to OutlinedTextFieldDefaults.colors() }
        }
        compose.waitForIdle()
        return ergebnis
    }

    @Test
    fun anInAkzentfarbe() {
        val (f, _) = farben(an = true)
        val akzent = FoodyFarben.Hell.primaer
        assertEquals(akzent, f.unfocusedIndicatorColor)
        assertEquals(akzent, f.focusedIndicatorColor)
        assertEquals(akzent, f.unfocusedLabelColor)
        assertEquals(akzent, f.focusedLabelColor)
    }

    @Test
    fun anDunkel() {
        val (f, _) = farben(an = true, dunkel = true)
        assertEquals(FoodyFarben.Dunkel.primaer, f.unfocusedIndicatorColor)
    }

    @Test
    fun ausWieStandard() {
        val (f, standard) = farben(an = false)
        assertEquals(standard, f)
    }
}
