package de.foody.feature.heute

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.testing.FoodyBildvergleich
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Summe
import de.foody.domain.TagebuchArt
import de.foody.feature.heute.tagebuch.TagAbschnitt
import de.foody.feature.heute.tagebuch.TagZustand
import de.foody.feature.heute.tagebuch.TagebuchTagInhalt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Referenzbilder von „Heute“ und „Alle Einträge“ mit erfundenen Daten: hell/dunkel × Handy/Tablet. Robolectric rendert
 * keine Unschärfe und (unter Windows) keine Fotos – die Karten zeigen den Platzhalter.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class HeuteScreenshotTest {
    /** Animationen systemweit aus: sonst wartet die Aufnahme auf endlose Animationen (Sync-Symbol). */
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = alle("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = alle("tablet")

    private val vergleich = FoodyBildvergleich

    private fun alle(geraet: String) {
        for (dunkel in listOf(false, true)) {
            val modus = if (dunkel) "dunkel" else "hell"
            bild("heute_${modus}_$geraet", dunkel) { HeuteScreen(beispielZustand(), KEINE_AKTIONEN) }
            bild("tagebuch_${modus}_$geraet", dunkel) {
                TagebuchTagInhalt(BEISPIEL_TAG, beispielTag(), {}, {}, {}, {}, {})
            }
        }
    }

    private fun bild(name: String, dunkel: Boolean, inhalt: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
            FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { inhalt() } }
        }
    }

    private fun eintrag(id: String, m: Mahlzeit, name: String, kj: String, art: TagebuchArt = TagebuchArt.FREI) = TagebuchEintragEntity(
        id = id, datum = BEISPIEL_TAG, mahlzeit = m.name, art = art, name = name, energieKj = BigDecimal(kj),
        portionen = if (art == TagebuchArt.REZEPT) BigDecimal.ONE else null,
        menge = if (art == TagebuchArt.ZUTAT) BigDecimal("150") else null, einheit = if (art == TagebuchArt.ZUTAT) MeasureUnit.GRAM else null,
        createdAt = 1, updatedAt = 1,
    )

    private fun beispielTag() = TagZustand(
        datum = BEISPIEL_TAG,
        summe = Summe(kcal = 1_420, eiweiss = 68, kohlenhydrate = 160, fett = 52, vollstaendig = false),
        ziel = 2000,
        abschnitte = listOf(
            TagAbschnitt(Mahlzeit.FRUEHSTUECK, listOf(eintrag("e1", Mahlzeit.FRUEHSTUECK, "Haferflocken mit Apfel und Zimt", "1590"))),
            TagAbschnitt(Mahlzeit.MITTAGESSEN, listOf(eintrag("e2", Mahlzeit.MITTAGESSEN, "Linsensuppe", "2008", TagebuchArt.REZEPT))),
            TagAbschnitt(Mahlzeit.SNACK, listOf(eintrag("e3", Mahlzeit.SNACK, "Walnüsse", "1300", TagebuchArt.ZUTAT))),
            TagAbschnitt(Mahlzeit.ABENDESSEN, emptyList()),
        ),
    )
}
