package de.foody.feature.rezepte.editor.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.ImportedIngredient
import de.foody.domain.MeasureUnit
import de.foody.feature.rezepte.MitSchrift200
import de.foody.feature.rezepte.bestand
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.pruefeNichtsAbgeschnitten
import de.foody.feature.rezepte.testDatenbank
import de.foody.feature.rezepte.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals

/**
 * Editor der Stile R–V in Immersiv: Felder mit Etikett (TalkBack liest das Etikett), erkannte Zutatenzeilen, Speichern
 * unten (gesperrt ohne Namen), Verwerfen fragt nach; bei 200 % Schrift wird nichts abgeschnitten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EditorStileTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val fertig = mutableListOf<String>()
    private var zurueck = 0

    @Before
    fun setUp() {
        db = testDatenbank()
        runBlocking { db.bestand() }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, id: String?, schrift200: Boolean = false) {
        val ablage = FotoAblage(RuntimeEnvironment.getApplication())
        val vm = halter.behalten(EditorViewModel(SavedStateHandle(), RezeptRepository(db, ablage, EmptyCoroutineContext) { 1_000L }, db, ablage))
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable { EditorRoute(id, onFertig = { fertig += it }, onZurueck = { zurueck++ }, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        val titel = if (id == null) "Neues Rezept" else "Bearbeiten"
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(titel) and isHeading()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun feld(etikett: String) = compose.onNode(hasSetTextAction() and hasContentDescription(etikett))

    private fun neuSpeichern(stil: FoodyStil) {
        zeigen(stil, null)
        compose.onNodeWithContentDescription("Speichern").assertIsNotEnabled()
        feld("Name").performTextInput("Bowl")
        feld("Eine Zutat je Zeile, Menge zuerst").performScrollTo().performTextInput("200 g Reis")
        compose.warteAufText("Reis")
        compose.onNodeWithContentDescription("Speichern").performClick()
        compose.waitUntil(5_000) { fertig.isNotEmpty() }
        assertEquals("Bowl", runBlocking { db.rezeptDao().nachId(fertig.single())?.name })
    }

    private fun verwerfen(stil: FoodyStil) {
        zeigen(stil, "r1")
        compose.warteAufText("Reispfanne")
        feld("Name").performTextReplacement("Reispfanne scharf")
        compose.onNodeWithText("Verwerfen").performClick()
        compose.warteAufText("Änderungen verwerfen?")
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.onNodeWithContentDescription("Abbrechen").performClick()
        compose.warteAufText("Änderungen verwerfen?")
        assertEquals(0, zurueck)
    }

    private fun gross(stil: FoodyStil) {
        zeigen(stil, "r1", schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun anime() = neuSpeichern(FoodyStil.ANIME)

    @Test fun edo() = neuSpeichern(FoodyStil.EDO)

    @Test fun bayrisch() = neuSpeichern(FoodyStil.BAYRISCH)

    @Test fun grauen() = neuSpeichern(FoodyStil.GRAUEN)

    @Test fun kreuzer() = neuSpeichern(FoodyStil.KREUZER)

    @Test fun animeVerwerfen() = verwerfen(FoodyStil.ANIME)

    @Test fun bayrischVerwerfen() = verwerfen(FoodyStil.BAYRISCH)

    @Test fun kreuzerVerwerfen() = verwerfen(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("400 g", mengeText(ImportedIngredient(name = "Reis", amount = BigDecimal("400"), unit = MeasureUnit.GRAM, note = null, group = null)))
        assertEquals("2", mengeText(ImportedIngredient(name = "Ei", amount = BigDecimal("2.0"), unit = MeasureUnit.PIECE, note = null, group = null)))
        assertEquals("Zuadaten", editorArt(FoodyStil.BAYRISCH).zutaten)
        assertEquals("Crew", editorArt(FoodyStil.KREUZER).portionen)
    }
}
