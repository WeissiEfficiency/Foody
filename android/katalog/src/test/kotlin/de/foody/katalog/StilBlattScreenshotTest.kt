package de.foody.katalog

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.FoodyBildvergleich
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Das Blatt (Bottom Sheet) in den Stilen R–V (Immersiv): Form, Grund, Kante und Griff des Stils, darin Titel, Chips
 * und Knopf. Ein Bild je Stil; das Blatt liegt in einem eigenen Fenster, deshalb ein Bild vom ganzen Bildschirm.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class StilBlattScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun anime() = blatt(FoodyStil.ANIME)

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun edo() = blatt(FoodyStil.EDO)

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun bayrisch() = blatt(FoodyStil.BAYRISCH)

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun grauen() = blatt(FoodyStil.GRAUEN)

    @Test
    @Config(qualifiers = "w411dp-h640dp")
    fun kreuzer() = blatt(FoodyStil.KREUZER)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun blatt(stil: FoodyStil) {
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                FoodyHintergrund(Modifier.fillMaxSize()) {
                    GlasBlatt(onSchliessen = {}, ganzOffen = true) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BlattTitel("Frühere Listen")
                            Text("Eine Liste von früher übernehmen.", color = FoodyTheme.farben.nebentext)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodyChip("Diese Woche", ausgewaehlt = true, onKlick = {})
                                FoodyChip("Älter", ausgewaehlt = false, onKlick = {})
                            }
                            FoodyKnopf("Übernehmen", {}, Modifier.fillMaxWidth().padding(bottom = 24.dp))
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Übernehmen").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/stil_blatt_${stil.name.lowercase()}.png", roborazziOptions = FoodyBildvergleich)
    }
}
