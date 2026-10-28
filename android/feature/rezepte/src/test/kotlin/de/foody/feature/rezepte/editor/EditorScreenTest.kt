package de.foody.feature.rezepte.editor

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.testing.ViewModelHalter
import de.foody.feature.rezepte.MitSchrift200
import de.foody.feature.rezepte.bestand
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
import kotlin.test.assertEquals

/** Ohne Breiten-Qualifier: der Verwerfen-Dialog liegt in einem eigenen Fenster (Robolectric hängt sonst). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EditorScreenTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val fertig = mutableListOf<String>()
    private var zurueck = 0

    // ViewModel außerhalb der Komposition: übersteht den Neuaufbau wie ein Hilt-ViewModel
    private val vm by lazy {
        val ablage = FotoAblage(RuntimeEnvironment.getApplication())
        halter.behalten(EditorViewModel(RezeptRepository(db, ablage) { 1_000L }, db, ablage))
    }

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

    private val inhalt = @androidx.compose.runtime.Composable {
        FoodyTheme { EditorRoute(id = null, onFertig = { fertig += it }, onZurueck = { zurueck++ }, vm = vm) }
    }

    private fun feld(beschriftung: String) = compose.onNode(hasSetTextAction() and hasText(beschriftung))

    @Test
    fun hinweisGekochtMitAnderenPortionen() {
        compose.setContent { FoodyTheme { EditorRoute("r1", {}, {}, gekochtMit = 4, vm = vm) } }
        compose.warteAufText("Gekocht mit 4 statt 2 Portionen – Mengen beziehen sich auf 2")
    }

    @Test
    fun keinHinweisBeiGleichenPortionen() {
        compose.setContent { FoodyTheme { EditorRoute("r1", {}, {}, gekochtMit = 2, vm = vm) } }
        compose.warteAufText("Bearbeiten")
        compose.onNodeWithText("Gekocht mit", substring = true).assertDoesNotExist()
    }

    @Test
    fun neuesRezeptSpeichern() {
        compose.setContent { inhalt() }
        compose.warteAufText("Neues Rezept")
        feld("Name").performTextInput("Bowl")
        feld("Zutaten (eine je Zeile)").performTextInput("200 g Reis")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("200 g · Reis · im Katalog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Speichern").performClick()
        compose.waitUntil(5_000) { fertig.isNotEmpty() }
        assertEquals("Bowl", runBlocking { db.rezeptDao().nachId(fertig.single())?.name })
    }

    @Test
    fun verwerfenFragt() {
        compose.setContent { inhalt() }
        compose.warteAufText("Neues Rezept")
        feld("Name").performTextInput("Bowl")
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.warteAufText("Änderungen verwerfen?")
        compose.onNodeWithText("Verwerfen").performClick()
        assertEquals(1, zurueck)
    }

    @Test
    fun ueberstehtNeuaufbau() {
        val wiederherstellen = StateRestorationTester(compose)
        wiederherstellen.setContent { inhalt() }
        compose.warteAufText("Neues Rezept")
        feld("Name").performTextInput("Bowl")
        wiederherstellen.emulateSavedInstanceStateRestore()
        compose.warteAufText("Bowl")
    }

    @Test
    fun schrift200OhneAbschneiden() {
        compose.setContent { FoodyTheme { MitSchrift200 { EditorRoute(id = "r1", onFertig = {}, onZurueck = {}, vm = vm) } } }
        compose.warteAufText("400 g Reis")
        compose.onNodeWithText("Bearbeiten").assertExists()
        compose.pruefeNichtsAbgeschnitten()
    }
}
