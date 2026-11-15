package de.foody.feature.einstellungen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Einstellungen der Stile R–V in Immersiv wie in den Entwürfen: Überschriften mit Stilwort (TalkBack liest das
 * normale Wort), Schalter-Zeilen, Tagesziel mit − und + und Feld, Varianten-Leiste und Farbmuster als Optionsfelder,
 * Abgleich öffnet „Server verbinden“; bei 200 % Schrift wird nichts abgeschnitten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EinstellungenStileTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: EinstellungenTestUmgebung
    private var server = 0

    @Before
    fun setUp() {
        u = EinstellungenTestUmgebung()
    }

    @After
    fun tearDown() = u.schliessen()

    private val keineDateien: @Composable ((Uri) -> Unit, (Uri) -> Unit) -> Dateiwahl = { _, _ -> remember { Dateiwahl({}, {}) } }

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false): EinstellungenViewModel {
        u.darstellung.stilSetzen(stil.name)
        u.darstellung.stilVarianteSetzen(StilVariante.IMMERSIV.name)
        u.ziel.setzen(2000)
        u.verbinden()
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                val inhalt = @Composable { EinstellungenRoute(onServer = { server++ }, onZutaten = {}, onZurueck = {}, vm = vm, dateiwahl = keineDateien) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Darstellung") and isHeading()).fetchSemanticsNodes().isNotEmpty() }
        return vm
    }

    private fun ziel() = runBlocking { u.ziel.tagesziel().first() }

    private fun bedienen(stil: FoodyStil) {
        val vm = zeigen(stil)
        // Abgleich: Marke und Zeile, Tipp öffnet „Server verbinden“
        val verbunden = if (stil == FoodyStil.KREUZER) "VERBUNDEN · HTTP://10.0.2.2:8081" else "Verbunden mit http://10.0.2.2:8081"
        compose.onNodeWithText(verbunden).performClick()
        assertEquals(1, server)
        // Variante und Farbe als Optionsfelder
        compose.onNode(isSelectable() and hasContentDescription(stil.akzente[1].anzeigename)).performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.zustand.value.akzent == stil.akzente[1].name }
        compose.onNode(isSelectable() and hasContentDescription(stil.akzente[1].anzeigename)).assertIsSelected()
        compose.onNodeWithText(if (stil == FoodyStil.KREUZER) "RUHIG" else "Ruhig").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.zustand.value.variante == StilVariante.RUHIG }
        // Tagesziel: − und + in Schritten von 50, das Feld nimmt eine eigene Zahl
        compose.onNodeWithContentDescription("Ziel erhöhen").performScrollTo().performClick()
        compose.waitUntil(5_000) { ziel() == 2050 }
        compose.onNodeWithContentDescription("Ziel senken").performClick().performClick()
        compose.waitUntil(5_000) { ziel() == 1950 }
        val feld = compose.onNode(hasSetTextAction() and hasContentDescription("Tagesziel in kcal"))
        feld.performTextReplacement("2400")
        feld.performImeAction()
        compose.waitUntil(5_000) { ziel() == 2400 }
        // Schalter-Zeile
        val schalter = compose.onNodeWithText("Online-Produktsuche").performScrollTo()
        schalter.assertIsOn()
        schalter.performClick()
        compose.waitUntil(5_000) { !u.scan.onlineSuche.value }
        schalter.assertIsOff()
    }

    private fun gross(stil: FoodyStil) {
        zeigen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun anime() = bedienen(FoodyStil.ANIME)

    @Test fun edo() = bedienen(FoodyStil.EDO)

    @Test fun bayrisch() = bedienen(FoodyStil.BAYRISCH)

    @Test fun grauen() = bedienen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = bedienen(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun schritte() {
        assertEquals(2050, zielSchritt("2000", 1))
        assertEquals(1950, zielSchritt("", -1))
        assertEquals(ZIEL_MIN, zielSchritt("520", -1))
        assertEquals(ZIEL_MAX, zielSchritt("10000", 1))
    }
}
