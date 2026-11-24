package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.KEINE_AKTIONEN
import de.foody.feature.heute.MitSchrift
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Das Zahnrad für die Einstellungen sitzt in jedem Stil und jeder Variante oben rechts im Kopf von „Heute“ und ist
 * gleich groß (48 dp), auch auf einem schmalen Handy (360 dp) mit einem langen Datum (Mittwoch, 30. Dezember) und bei
 * großer Schrift (130 %). Oben heißt in der ersten Zeile des Kopfes, nicht neben einem umgebrochenen Titel.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
class EinstellungenKnopfLageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zustand = beispielZustand().copy(datum = LocalDate.of(2026, 12, 30))

    /** Stil und Variante des Themes; ein Wechsel setzt den Inhalt nicht neu. */
    private var auswahl by mutableStateOf(FoodyStil.MILCHGLAS to StilVariante.IMMERSIV)

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(schrift: Float) {
        compose.setContent {
            val (stil, variante) = auswahl
            FoodyTheme(stil = stil, variante = variante, unschaerfe = false) {
                MitSchrift(schrift) { FoodyHintergrund { HeuteScreen(zustand, KEINE_AKTIONEN) } }
            }
        }
    }

    /** Alle Stile und Varianten bei der Schrift [schrift]; meldet alle Abweichungen auf einmal. */
    private fun obenRechts(schrift: Float) {
        zeigen(schrift)
        val fehler = mutableListOf<String>()
        for (stil in FoodyStil.entries) {
            for (variante in StilVariante.entries) {
                auswahl = stil to variante
                compose.waitForIdle()
                val b = compose.onNodeWithContentDescription("Einstellungen").getBoundsInRoot()
                val breite = compose.onRoot().getBoundsInRoot().right
                if (b.right < breite - 32.dp) fehler += "$stil/$variante rechts bei ${b.right}"
                if (b.top > 48.dp) fehler += "$stil/$variante oben bei ${b.top}"
                if (b.right - b.left != 48.dp) fehler += "$stil/$variante Breite ${b.right - b.left}"
                if (b.bottom - b.top != 48.dp) fehler += "$stil/$variante Höhe ${b.bottom - b.top}"
            }
        }
        assertTrue("Schrift $schrift:\n" + fehler.joinToString("\n"), fehler.isEmpty())
    }

    @Test
    fun obenRechtsUndGleichGross() = obenRechts(1f)

    @Test
    fun schrift130() = obenRechts(1.3f)

    /** Zeilen des Titels: Edo zeigt „Heute“, die Sage „HEUTE“ mit der Bedienhilfe „Heute“. */
    private fun titelZeilen(): Int {
        val titel = compose.onNode(isHeading() and (hasText("Heute") or hasContentDescription("Heute")), useUnmergedTree = true)
        val ergebnisse = mutableListOf<TextLayoutResult>()
        titel.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(ergebnisse)
        return ergebnisse.single().lineCount
    }

    /** Edo und Sage in Ruhig: Bei 130 % bleibt „Heute“ in einer Zeile, das lange Datum rückt darunter. */
    @Test
    fun ruhigTitelEinzeilig() {
        zeigen(1.3f)
        val zeilen = listOf(FoodyStil.EDO, FoodyStil.SAGE).associateWith { stil ->
            auswahl = stil to StilVariante.RUHIG
            compose.waitForIdle()
            compose.pruefeNichtsAbgeschnitten()
            titelZeilen()
        }
        assertEquals("Zeilen des Titels in Ruhig", mapOf(FoodyStil.EDO to 1, FoodyStil.SAGE to 1), zeilen)
    }
}
