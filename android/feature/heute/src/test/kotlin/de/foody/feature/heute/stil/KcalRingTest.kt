package de.foody.feature.heute.stil

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.feature.heute.MitSchrift200
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Bei 200 % Schrift bleiben Zahl und Ziel im Innern des Rings, statt über den Strich zu laufen (Review W6). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class KcalRingTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pruefe(stil: FoodyStil, groesse: Dp, strich: Dp, zahlStil: TextStyle, unterStil: TextStyle) {
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                MitSchrift200 { KcalRing(0.6f, FoodyTheme.stil.zier, groesse, strich, true, "1.240", zahlStil, "von 2.000", unterStil) }
            }
        }
        // Innen: Durchmesser ohne die beiden Striche, davon der Anteil RING_INNEN (Kreis, nicht Quadrat)
        val innen = (groesse - strich * 2) * RING_INNEN
        for (tag in listOf(RING_ZAHL, RING_UNTER)) {
            val knoten = compose.onNodeWithTag(tag, useUnmergedTree = true)
            val b = knoten.getUnclippedBoundsInRoot()
            assertTrue(b.width <= innen + 0.5.dp, "$tag in ${stil.name}: ${b.width} breit, innen nur $innen")
            // Und nichts abgeschnitten: Die Zahl muss ganz zu lesen sein
            val layout = mutableListOf<TextLayoutResult>()
            knoten.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
            assertTrue(!layout.single().hasVisualOverflow, "$tag in ${stil.name}: abgeschnitten")
        }
    }

    @Test
    fun sage() = pruefe(FoodyStil.SAGE, 110.dp, 8.dp, Sage.titel(22f, 0f), Sage.text(13f))

    @Test
    fun edo() = tagebuchArt(FoodyStil.EDO).let { pruefe(FoodyStil.EDO, 112.dp, 10.dp, it.titelSchrift(22f), it.klein(11.5f)) }

    @Test
    fun grauen() = tagebuchArt(FoodyStil.GRAUEN).let { pruefe(FoodyStil.GRAUEN, 112.dp, 10.dp, it.titelSchrift(22f), it.klein(11.5f)) }
}
