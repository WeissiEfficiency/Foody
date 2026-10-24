package de.foody.feature.heute

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.PLATZHALTER_TAG
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
@OptIn(DelicateCoilApi::class)
class HeuteFotoTest {
    @get:Rule
    val compose = createComposeRule()

    private fun platzhalter() = compose.onAllNodesWithTag(PLATZHALTER_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun ohneFotoPlatzhalter() {
        compose.setContent { FoodyTheme(dunkel = false) { HeuteScreen(beispielZustand(bild = null), KEINE_AKTIONEN) } }
        compose.waitUntil(5_000) { platzhalter() == 1 }
    }

    @Test
    fun fehlendeDateiPlatzhalter() {
        val weg = Uri.fromFile(File(RuntimeEnvironment.getApplication().filesDir, "recipe_images/gibt-es-nicht.jpg")).toString()
        compose.setContent { FoodyTheme(dunkel = false) { HeuteScreen(beispielZustand(bild = weg), KEINE_AKTIONEN) } }
        compose.waitUntil(5_000) { platzhalter() == 1 }
    }

    @After
    fun tearDown() = SingletonImageLoader.reset()

    /** Liefert für [link] ein fertiges Farbbild; alles andere läuft durch Coil wie in der App. */
    private fun bildFuer(link: String) {
        val ctx = RuntimeEnvironment.getApplication()
        val engine = FakeImageLoaderEngine.Builder().intercept(link, ColorImage(android.graphics.Color.rgb(200, 90, 40))).build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(ctx).components { add(engine) }.build())
    }

    @Test
    fun vorhandenesFotoStattPlatzhalter() {
        val link = Uri.fromFile(File(RuntimeEnvironment.getApplication().filesDir, "recipe_images/ofen.jpg")).toString()
        bildFuer(link)
        compose.setContent { FoodyTheme(dunkel = false) { HeuteScreen(beispielZustand(bild = link), KEINE_AKTIONEN) } }
        compose.waitUntil(10_000) { platzhalter() == 0 }
    }
}
