package de.foody.feature.heute

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.feature.heute.tagebuch.TagebuchTagInhalt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Auf dem Tablet bleibt der Inhalt lesbar schmal und mittig, statt sich über 1280 dp zu ziehen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w1280dp-h1200dp")
class BreiteTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pruefeSchmalUndMittig(tag: String) {
        val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(b.width <= INHALT_MAX_BREITE, "zu breit: ${b.width}")
        val links = b.left
        val rechts = 1280.dp - b.right
        assertTrue((links - rechts).value in -1f..1f, "nicht mittig: links $links, rechts $rechts")
    }

    @Test
    fun heuteSchmal() {
        compose.setContent { FoodyTheme(dunkel = false) { HeuteScreen(beispielZustand(), KEINE_AKTIONEN) } }
        pruefeSchmalUndMittig(HEUTE_INHALT)
    }

    @Test
    fun tagebuchSchmal() {
        compose.setContent { FoodyTheme(dunkel = false) { TagebuchTagInhalt(BEISPIEL_TAG, null, {}, {}, {}, {}, {}) } }
        pruefeSchmalUndMittig(TAGEBUCH_INHALT)
    }
}
