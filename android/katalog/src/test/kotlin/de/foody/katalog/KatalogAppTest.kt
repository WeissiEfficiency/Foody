package de.foody.katalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class KatalogAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun jedeSeiteRendert() {
        var aktuell by mutableStateOf(KatalogSeite.entries.first())
        compose.setContent { KatalogApp(start = aktuell) }
        for (seite in KatalogSeite.entries) {
            aktuell = seite
            compose.waitForIdle()
            compose.onNode(hasText(seite.titel) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        }
    }

    @Test
    fun umschalterWechseltDarstellung() {
        compose.setContent { KatalogApp(einstellung = KatalogEinstellung(darstellung = Darstellung.HELL)) }
        compose.onNodeWithTag(INHALT_TAG).assert(SemanticsMatcher.expectValue(DarstellungKey, "hell"))
        compose.onNodeWithText("Dunkel").performClick()
        compose.onNodeWithTag(INHALT_TAG).assert(SemanticsMatcher.expectValue(DarstellungKey, "dunkel"))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletZweispaltig() {
        compose.setContent { KatalogApp() }
        compose.onNodeWithTag(SEITENLISTE_TAG).assertExists()
        compose.onNodeWithTag(SEITENREIHE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(INHALT_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun handyEinspaltig() {
        compose.setContent { KatalogApp() }
        compose.onNodeWithTag(SEITENREIHE_TAG).assertExists()
        compose.onNodeWithTag(SEITENLISTE_TAG).assertDoesNotExist()
    }
}
