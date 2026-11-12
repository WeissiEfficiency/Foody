package de.foody.feature.woche.stil

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.FoodyBildvergleich
import de.foody.feature.woche.MONTAG
import de.foody.feature.woche.MitSchrift200
import de.foody.feature.woche.WocheRoute
import de.foody.feature.woche.WocheTestUmgebung
import de.foody.feature.woche.bestand
import de.foody.feature.woche.hauptspeisen
import de.foody.feature.woche.planen
import de.foody.feature.woche.pruefeNichtsAbgeschnitten
import de.foody.feature.woche.testDatenbank
import de.foody.feature.woche.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Planen-Blätter der Stile R–V in Immersiv: „Leere Tage füllen“ mit Vorschlagszeilen des Stils (abwählen, neu ziehen),
 * Portionen im Mahlzeit-Blatt mit den Knöpfen des Stils; bei 200 % Schrift wird nichts abgeschnitten. Bilder
 * `stil_*_fuellen_handy` und `stil_*_mahlzeit_handy`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class PlanenStileTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung

    private val vorschlag = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        u = WocheTestUmgebung(db)
        runBlocking {
            db.bestand()
            db.hauptspeisen()
            db.planen("p1", MONTAG, "MITTAGESSEN")
        }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                val inhalt = @Composable { WocheRoute({ _, _ -> }, {}, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Mittwoch, 28.10.", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun fuellenOeffnen(stil: FoodyStil, schrift200: Boolean = false) {
        zeigen(stil, schrift200)
        compose.onAllNodes(hasContentDescription("Leere Tage füllen", substring = true)).onFirst().performScrollTo().performClick()
        compose.warteAufText("Neu mischen")
    }

    private fun fuellen(stil: FoodyStil) {
        fuellenOeffnen(stil)
        val zeile = compose.onAllNodes(hasContentDescription("MI 28.10. übernehmen") and vorschlag).onFirst()
        zeile.performScrollTo().assertIsOn()
        zeile.performClick()
        zeile.assertIsOff()
        zeile.performClick()
        zeile.assertIsOn()
        compose.onNodeWithContentDescription("Neuer Vorschlag für MI 28.10.").performScrollTo().performClick()
        zeile.assertIsOn()
    }

    private fun mahlzeitOeffnen(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(hasContentDescription("Montag, 26.10.", substring = true)).onFirst().performClick()
        compose.onNode(hasText("Reispfanne") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performScrollTo().performClick()
        compose.warteAufText("2 Portionen")
    }

    private fun portionen(stil: FoodyStil) {
        mahlzeitOeffnen(stil)
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.waitUntil(5_000) { runBlocking { db.planEintragDao().alle().single().servings } == 3 }
        compose.warteAufText("3 Portionen")
        compose.onNodeWithContentDescription("Weniger Portionen").performClick()
        compose.waitUntil(5_000) { runBlocking { db.planEintragDao().alle().single().servings } == 2 }
        assertEquals(2, runBlocking { db.planEintragDao().alle().single().servings })
    }

    private fun gross(stil: FoodyStil) {
        fuellenOeffnen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun animeFuellen() = fuellen(FoodyStil.ANIME)

    @Test fun edoFuellen() = fuellen(FoodyStil.EDO)

    @Test fun bayrischFuellen() = fuellen(FoodyStil.BAYRISCH)

    @Test fun grauenFuellen() = fuellen(FoodyStil.GRAUEN)

    @Test fun kreuzerFuellen() = fuellen(FoodyStil.KREUZER)

    @Test fun animePortionen() = portionen(FoodyStil.ANIME)

    @Test fun bayrischPortionen() = portionen(FoodyStil.BAYRISCH)

    @Test fun kreuzerPortionen() = portionen(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("水", planenTag(FoodyStil.EDO, MONTAG.plusDays(2)))
        assertEquals("MI", planenTag(FoodyStil.KREUZER, MONTAG.plusDays(2)))
        assertEquals("Mi", planenTag(FoodyStil.BAYRISCH, MONTAG.plusDays(2)))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun bild(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png", roborazziOptions = FoodyBildvergleich)
    }

    private fun fuellenBild(stil: FoodyStil) {
        fuellenOeffnen(stil)
        bild("stil_${stil.name.lowercase()}_fuellen_handy")
    }

    private fun mahlzeitBild(stil: FoodyStil) {
        mahlzeitOeffnen(stil)
        bild("stil_${stil.name.lowercase()}_mahlzeit_handy")
    }

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun animeFuellenBild() = fuellenBild(FoodyStil.ANIME)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun edoFuellenBild() = fuellenBild(FoodyStil.EDO)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun bayrischFuellenBild() = fuellenBild(FoodyStil.BAYRISCH)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun grauenFuellenBild() = fuellenBild(FoodyStil.GRAUEN)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun kreuzerFuellenBild() = fuellenBild(FoodyStil.KREUZER)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun animeMahlzeitBild() = mahlzeitBild(FoodyStil.ANIME)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun edoMahlzeitBild() = mahlzeitBild(FoodyStil.EDO)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun bayrischMahlzeitBild() = mahlzeitBild(FoodyStil.BAYRISCH)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun grauenMahlzeitBild() = mahlzeitBild(FoodyStil.GRAUEN)

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun kreuzerMahlzeitBild() = mahlzeitBild(FoodyStil.KREUZER)
}
