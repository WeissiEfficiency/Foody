package de.foody.feature.zutaten

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.IngredientCatalog
import de.foody.domain.NutrientBasis
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
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class ZutatEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: ZutatenTestUmgebung
    private var fertig = 0

    @Before
    fun setUp() {
        u = ZutatenTestUmgebung()
    }

    @After
    fun tearDown() = u.db.close()

    private fun zeigen(id: String, schrift200: Boolean = false): ZutatEditorViewModel {
        val vm = u.editor()
        compose.setContent {
            FoodyTheme {
                val inhalt = @Composable { ZutatEditorRoute(id, onFertig = { fertig++ }, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Speichern")
        compose.warteAufText("Name") // erst nach dem Laden da; die Abfrage lässt die Hauptschleife laufen
        return vm
    }

    private fun feld(beschriftung: String) = compose.onNode(hasSetTextAction() and hasText(beschriftung))

    private fun reis() = u.zutat("seed-reis", "Reis") {
        it.copy(nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1502"), densityGPerMl = BigDecimal("0.85"))
    }

    private fun menue(eintrag: String) {
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText(eintrag).performClick()
    }

    @Test
    fun felderZeigenWerte() {
        reis()
        zeigen("seed-reis")
        compose.warteAufText("359")
        compose.onNodeWithText("0,85").assertExists()
    }

    @Test
    fun synonymeSichtbar() {
        u.zutat("z", IngredientCatalog.canonicalName("Zwiebeln"))
        zeigen("z")
        compose.warteAufTeil("Auch: ")
    }

    @Test
    fun kategorieChipSetzt() {
        reis()
        val vm = zeigen("seed-reis")
        compose.onNodeWithText("Kühlregal").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.zustand.value.entwurf?.kategorie == "Kühlregal" }
    }

    @Test
    fun freieKategorieAlsChip() {
        u.zutat("z", "Würze") { it.copy(category = "Gewürzregal") }
        zeigen("z")
        compose.onNodeWithText("Gewürzregal").assertIsSelected()
    }

    @Test
    fun feldfehlerNachFertig() {
        reis()
        zeigen("seed-reis")
        feld("kcal").performTextReplacement("5000")
        compose.onAllNodesWithText("0 bis 1.000").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        feld("kcal").performImeAction()
        compose.warteAufText("0 bis 1.000")
    }

    @Test
    fun zurueckMitAenderungFragt() {
        reis()
        zeigen("seed-reis")
        feld("Dichte (g/ml)").performTextReplacement("1")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.warteAufText("Änderungen verwerfen?")
        assertEquals(0, fertig)
        compose.onNodeWithText("Verwerfen").performClick()
        compose.warteBisWeg("Änderungen verwerfen?")
        assertEquals(1, fertig)
    }

    @Test
    fun loeschenFragt() {
        u.zutat("z", "Würze")
        zeigen("z")
        menue("Löschen")
        compose.warteAufText("„Würze“ löschen?")
        compose.onNodeWithText("Löschen").performClick()
        compose.warteBisWeg("„Würze“ löschen?")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Speichern").fetchSemanticsNodes().isNotEmpty() && fertig == 1 }
    }

    @Test
    fun inVerwendungBietetZusammenfuehren() {
        u.zutat("z", "Würze")
        u.zutat("y", "Gewürz")
        u.rezept("r1", "A", "z")
        u.rezept("r2", "B", "z")
        zeigen("z")
        menue("Löschen")
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText(inVerwendungText(2))
        compose.onNodeWithText("Zusammenführen …").performClick()
        compose.warteAufText("Gewürz")
        compose.onNodeWithText("Zusammenführen mit …").assertExists()
    }

    @Test
    fun zielwahlOhneSichSelbst() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        zeigen("alt")
        val vorher = compose.onAllNodesWithText("Zwiebeln").fetchSemanticsNodes().size
        menue("Zusammenführen mit …")
        compose.warteAufText("Zwiebel")
        assertEquals(vorher, compose.onAllNodesWithText("Zwiebeln").fetchSemanticsNodes().size)
    }

    @Test
    fun zusammenfuehrenBestaetigt() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        u.rezept("r1", "A", "alt")
        u.rezept("r2", "B", "alt")
        u.rezept("r3", "C", "alt")
        zeigen("alt")
        menue("Zusammenführen mit …")
        compose.warteAufText("Zwiebel")
        compose.onNodeWithText("Zwiebel").performClick()
        compose.warteAufText(zusammenfuehrenFrage("Zwiebeln", "Zwiebel", 3))
        compose.onNodeWithText("Zusammenführen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Speichern").fetchSemanticsNodes().isNotEmpty() && fertig == 1 }
    }

    @Test
    fun nameVergebenFragtZusammenfuehren() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        zeigen("alt")
        feld("Name").performTextReplacement("Zwiebel")
        compose.onNodeWithText("Speichern").performClick()
        compose.warteAufText("„Zwiebel“ gibt es schon. Diese Zutat damit zusammenführen?")
        compose.onNodeWithText("Zusammenführen").performClick()
        compose.warteAufText(zusammenfuehrenFrage("Zwiebeln", "Zwiebel", 0))
    }

    @Test
    fun verwendetIn() {
        reis()
        u.rezept("r1", "Reispfanne", "seed-reis")
        u.rezept("r2", "curry", "seed-reis")
        zeigen("seed-reis")
        compose.warteAufText("Verwendet in: curry, Reispfanne")
    }

    @Test
    fun schrift200() {
        reis()
        zeigen("seed-reis", schrift200 = true)
        compose.pruefeNichtsAbgeschnitten()
    }
}
