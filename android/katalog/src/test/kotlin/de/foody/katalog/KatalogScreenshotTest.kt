package de.foody.katalog

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Referenzbilder aller Katalogseiten: hell/dunkel × Handy/Tablet, dazu Schrift 200 % auf dem Handy. Breiten wie in der
 * Spec (411 dp, 1280 dp), Höhe größer, damit lange Seiten ganz im Bild sind. Robolectric rendert keine Unschärfe –
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
    fun handy() = alleSeiten("handy", schrift200 = listOf(false, true))

    @Test
    @Config(qualifiers = "w1280dp-h1600dp")
    fun tablet() = alleSeiten("tablet", schrift200 = listOf(false))

    /** Höchstens 0,5 % geänderte Pixel gelten als gleich (Schriftglättung), mehr ist eine echte Änderung. */
    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f))

    private fun alleSeiten(geraet: String, schrift200: List<Boolean>) {
        for (seite in KatalogSeite.entries) {
            for (dunkel in listOf(false, true)) {
                for (gross in schrift200) {
                    if (gross && dunkel) continue
                    val name = buildString {
                        append(seite.name.lowercase()).append('_').append(if (dunkel) "dunkel" else "hell").append('_').append(geraet)
                        if (gross) append("_schrift200")
                    }
                    captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
                        KatalogApp(
                            start = seite,
                            einstellung = KatalogEinstellung(
                                darstellung = if (dunkel) Darstellung.DUNKEL else Darstellung.HELL,
                                schrift200 = gross,
                                ohneUnschaerfe = true,
                            ),
                        )
                    }
                }
            }
        }
    }
}
