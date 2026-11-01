package de.foody.feature.zutaten

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.IngredientCatalog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatenListeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: ZutatenTestUmgebung
    private val geoeffnet = ArrayList<String>()

    @Before
    fun setUp() {
        u = ZutatenTestUmgebung()
    }

    @After
    fun tearDown() = u.schliessen()

    private fun zeigen(schrift200: Boolean = false) {
        val vm = u.liste()
        compose.setContent {
            FoodyTheme {
                val inhalt = @Composable { ZutatenListeRoute(onZutat = { geoeffnet += it }, onZurueck = {}, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    private fun bestand() {
        u.zutat("seed-reis", "Reis") { it.copy(energyKj = BigDecimal("1502"), category = "Trockenwaren") }
        u.zutat("eigen-1", "Meine Würze")
        u.rezept("r1", "Reispfanne", "seed-reis")
        u.rezept("r2", "Risotto", "seed-reis")
    }

    @Test
    fun zeilen() {
        bestand()
        zeigen()
        compose.warteAufText("Reis")
        compose.onNodeWithText("359 kcal").assertExists()
        compose.onNodeWithText("in 2 Rezepten").assertExists()
        compose.onNodeWithText("– kcal").assertExists()
        compose.onNodeWithText("unbenutzt").assertExists()
        compose.onNodeWithText("2 Zutaten · 1 ohne Nährwerte").assertExists()
    }

    @Test
    fun chipFiltert() {
        bestand()
        zeigen()
        compose.warteAufText("Reis")
        compose.onNodeWithText("Unbenutzt").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reis").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Meine Würze").assertExists()
    }

    @Test
    fun leereSuche() {
        bestand()
        zeigen()
        compose.warteAufText("Reis")
        compose.onNode(hasSetTextAction() and hasContentDescription("Zutaten suchen")).performTextInput("xyz")
        compose.warteAufText("Keine Zutat gefunden")
    }

    @Test
    fun neueZutat() {
        zeigen()
        compose.onNodeWithContentDescription("Neue Zutat").performClick()
        compose.warteAufText("Anlegen")
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Paprika")
        compose.onNodeWithText("Anlegen").performClick()
        compose.warteBisWeg("Neue Zutat")
        assertEquals("Paprika", runBlocking { u.db.zutatDao().nachId(geoeffnet.single()) }?.canonicalName)
    }

    @Test
    fun neueZutatGibtEsSchon() {
        bestand()
        zeigen()
        compose.warteAufText("Reis")
        compose.onNodeWithContentDescription("Neue Zutat").performClick()
        compose.warteAufText("Anlegen")
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("reis")
        compose.onNodeWithText("Anlegen").performClick()
        compose.warteAufText("„Reis“ gibt es schon.")
        compose.onNodeWithText("Öffnen").performClick()
        compose.warteBisWeg("Neue Zutat")
        assertEquals("seed-reis", geoeffnet.single())
    }

    @Test
    fun vereinheitlichen() {
        val c = IngredientCatalog.canonicalName("Zwiebeln")
        u.zutat("a", "Zwiebeln") { it.copy(category = "Obst & Gemüse") }
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Namen vereinheitlichen").performClick()
        compose.warteAufText("Zwiebeln → $c")
        compose.onNodeWithText("Anwenden").performClick()
        compose.warteAufText("1 Zutat angepasst")
    }

    @Test
    fun schonEinheitlich() {
        u.zutat("a", "Meine Würze") { it.copy(category = "Öle & Gewürze") }
        zeigen()
        compose.warteAufText("Meine Würze")
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Namen vereinheitlichen").performClick()
        compose.warteAufText("Alle Namen sind schon einheitlich.")
        compose.onNodeWithText("OK").performClick()
        compose.warteBisWeg("Alle Namen sind schon einheitlich.")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun neuKnopfUeberDerLeiste() {
        bestand()
        zeigen()
        compose.warteAufText("Reis")
        compose.pruefeUeberLeiste(compose.onNodeWithContentDescription("Neue Zutat"))
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun schrift200() {
        bestand()
        zeigen(schrift200 = true)
        compose.warteAufText("Reis")
        compose.pruefeNichtsAbgeschnitten()
    }

    private fun neuOeffnen() {
        compose.onNodeWithContentDescription("Neue Zutat").performClick()
        compose.warteAufText("Anlegen")
    }

    private fun scannen() {
        compose.onNodeWithText("Packung scannen").performClick()
        compose.onNode(hasText("Strichcode") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
    }

    @Test
    fun neueZutatMitKnopf() {
        zeigen()
        neuOeffnen()
        compose.onNodeWithText("Packung scannen").assertExists()
    }

    @Test
    fun hinweisNachScan() {
        u.scan.packung = haferflocken()
        zeigen()
        neuOeffnen()
        scannen()
        compose.warteAufText("Werte aus Open Food Facts werden übernommen")
        compose.onNode(hasSetTextAction() and hasText("Haferflocken")).assertExists()
        compose.onNodeWithText("Anlegen").performClick()
        compose.warteBisWeg("Neue Zutat")
        val z = runBlocking { u.db.zutatDao().nachId(geoeffnet.single()) }
        assertEquals("Open Food Facts", z?.nutrientSource)
    }

    @Test
    fun getippterNameBleibt() {
        u.scan.packung = haferflocken()
        zeigen()
        neuOeffnen()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Hafer")
        scannen()
        compose.warteAufText("Werte aus Open Food Facts werden übernommen")
        compose.onNode(hasSetTextAction() and hasText("Hafer")).assertExists()
    }

    @Test
    fun imKatalogOeffnetEditor() {
        u.zutat("h", "Haferflocken") { it.copy(barcode = TEST_CODE) }
        u.scan.imKatalog = de.foody.core.scan.ZutatTreffer("h", "Haferflocken")
        zeigen()
        neuOeffnen()
        scannen()
        compose.warteBisWeg("Anlegen")
        assertEquals("h", geoeffnet.single())
    }
}
