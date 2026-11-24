package de.foody.katalog

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.foody.core.testing.FoodyBildvergleich
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Referenzbilder aller Katalogseiten: hell/dunkel × Handy/Tablet. Breiten wie in der Spec (411 dp, 1280 dp), Höhe größer, damit lange Seiten ganz im Bild sind. Robolectric rendert keine Unschärfe –
 * die Bilder zeigen Glas nur getönt, genau den Fall, den der Kontrast-Test absichert.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class KatalogScreenshotTest {
    /**
     * Animationen systemweit aus (wie „Animationen entfernen“): Das Sync-Symbol dreht sich sonst endlos, die Aufnahme
     * wartet vergeblich auf Ruhe und läuft je Bild in eine Zeitgrenze (Minuten statt Sekunden).
     */
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    @Config(qualifiers = "w411dp-h2400dp")
    fun handy() = alleSeiten("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1600dp")
    fun tablet() = alleSeiten("tablet")

    private val vergleich = FoodyBildvergleich

    private fun alleSeiten(geraet: String) {
        for (seite in KatalogSeite.entries) {
            for (dunkel in listOf(false, true)) {
                val name = seite.name.lowercase() + "_" + (if (dunkel) "dunkel" else "hell") + "_" + geraet
                captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
                    KatalogApp(
                        start = seite,
                        einstellung = KatalogEinstellung(darstellung = if (dunkel) Darstellung.DUNKEL else Darstellung.HELL, ohneUnschaerfe = true),
                    )
                }
            }
        }
    }
}
