package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.KEINE_AKTIONEN
import de.foody.feature.heute.beispielZustand
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
 * gleich groß (48 dp), auch auf einem schmalen Handy (360 dp) mit einem langen Datum (Mittwoch, 30. Dezember).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
class EinstellungenKnopfLageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun obenRechtsUndGleichGross() {
        val zustand = beispielZustand().copy(datum = LocalDate.of(2026, 12, 30))
        var auswahl by mutableStateOf(FoodyStil.MILCHGLAS to StilVariante.IMMERSIV)
        compose.setContent {
            val (stil, variante) = auswahl
            FoodyTheme(stil = stil, variante = variante, unschaerfe = false) {
                FoodyHintergrund { HeuteScreen(zustand, KEINE_AKTIONEN) }
            }
        }
        for (stil in FoodyStil.entries) {
            for (variante in StilVariante.entries) {
                auswahl = stil to variante
                compose.waitForIdle()
                val b = compose.onNodeWithContentDescription("Einstellungen").getBoundsInRoot()
                val breite = compose.onRoot().getBoundsInRoot().right
                assertTrue("$stil/$variante rechts", b.right >= breite - 32.dp)
                assertTrue("$stil/$variante oben", b.top <= 96.dp)
                assertEquals("$stil/$variante Breite", 48.dp, b.right - b.left)
                assertEquals("$stil/$variante Höhe", 48.dp, b.bottom - b.top)
            }
        }
    }
}
