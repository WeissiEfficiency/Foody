package de.foody.feature.rezepte.liste

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
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
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class RezeptListeScreenTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val geoeffnet = mutableListOf<String>()
    private var neu = 0

    @Before
    fun setUp() {
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(schrift200: Boolean = false, stil: FoodyStil = FoodyStil.MILCHGLAS) {
        val vm = halter.behalten(RezeptListeViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1L }))
        compose.setContent {
            FoodyTheme(stil = stil) {
                val inhalt = @androidx.compose.runtime.Composable {
                    RezeptListeRoute(onOeffnen = { geoeffnet += it }, onNeu = { neu++ }, vm = vm)
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    @Test
    fun rasterMitKcal() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("717 kcal")
        compose.onNodeWithText("Reispfanne").performClick()
        assertEquals(listOf("r1"), geoeffnet)
    }

    @Test
    fun leerZeigtAnlegen() {
        zeigen()
        compose.warteAufText("Noch keine Rezepte")
        compose.onNodeWithText("Anlegen").performClick()
        assertEquals(1, neu)
    }

    @Test
    fun keinTrefferZeigtZuruecksetzen() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Rezepte, Zutaten …").performTextInput("xyz")
        compose.warteAufText("Nichts gefunden")
        compose.onNodeWithText("Filter zurücksetzen").performClick()
        compose.warteAufText("Reispfanne")
    }

    /** „+“ öffnet gleich ein neues Rezept; importiert wird in den Einstellungen. */
    @Test
    fun plusOeffnetNeuesRezept() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Neues Rezept").performClick()
        assertEquals(1, neu)
        compose.onNodeWithText("Markdown importieren").assertDoesNotExist()
    }

    /** Der Sortierknopf neben der Suche öffnet ein Menü, das die gewählte abhakt. */
    @Test
    fun sortierungZeigtDieGewaehlte() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Sortierung: Name").performClick()
        compose.onNodeWithText("Name").assertIsSelected()
        compose.onAllNodesWithTag("menue-haken", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Neueste").performClick()
        compose.onNodeWithContentDescription("Sortierung: Neueste").performClick()
        compose.onNodeWithText("Neueste").assertIsSelected()
        compose.onAllNodesWithTag("menue-haken", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun alleIstGewaehltBisEinFilterGreift() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Alle").assertIsSelected()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("Alle").assertIsNotSelected()
        compose.onNodeWithText("Alle").performClick()
        compose.onNodeWithText("Favoriten").assertIsNotSelected()
    }

    @Test
    fun schrift200OhneAbschneiden() {
        runBlocking { db.bestand() }
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun grimoireBlaettertUndSchlaegtAuf() {
        runBlocking { db.bestand() }
        zeigen(stil = FoodyStil.SAGE)
        compose.warteAufText("GRIMOIRE")
        val n = compose.onAllNodesWithContentDescription("Öffnen: ", substring = true).fetchSemanticsNodes().size
        assertTrue(n >= 1)
        compose.onNodeWithText("AUFSCHLAGEN").performScrollTo().performClick()
        assertEquals(1, geoeffnet.size)
        compose.onNodeWithContentDescription("Nächstes Rezept").performScrollTo().performClick()
        compose.onNodeWithText("AUFSCHLAGEN").performScrollTo().performClick()
        // Mit mehr als einem Rezept zeigt „Nächstes“ ein anderes; mit einem bleibt es dasselbe
        assertEquals(2, geoeffnet.size)
    }

    @Test
    fun grimoireSucheUndNeu() {
        runBlocking { db.bestand() }
        zeigen(stil = FoodyStil.SAGE)
        compose.warteAufText("GRIMOIRE")
        compose.onNode(hasSetTextAction()).performTextInput("Reispfanne")
        compose.warteAufText("Reispfanne ›")
        compose.onNodeWithText("Reispfanne ›").performClick()
        assertEquals(listOf("r1"), geoeffnet)
        compose.onNodeWithContentDescription("Neues Rezept").performClick()
        assertEquals(1, neu)
    }

    @Test
    fun grimoireLeerBietetAnlegen() {
        zeigen(stil = FoodyStil.SAGE)
        compose.warteAufText("Noch keine Rezepte")
        compose.onNodeWithText("Anlegen").performClick()
        assertEquals(1, neu)
    }
}
