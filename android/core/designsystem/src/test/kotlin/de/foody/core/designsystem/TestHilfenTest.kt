package de.foody.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Gegenprobe: der Helfer erkennt wirklich abgeschnittene Texte – sonst wären die 200-%-Tests wertlos. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class TestHilfenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun erkenntGekuerztenUndZuNiedrigenText() {
        compose.setContent {
            FoodyTheme {
                Column {
                    Text("Rinderrouladen-Schmorgericht", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(60.dp))
                    Text("Zwei\nZeilen", modifier = Modifier.height(10.dp))
                    Text("passt")
                }
            }
        }
        assertEquals(listOf("Rinderrouladen-Schmorgericht", "Zwei\nZeilen"), compose.abgeschnitteneTexte())
    }
}
