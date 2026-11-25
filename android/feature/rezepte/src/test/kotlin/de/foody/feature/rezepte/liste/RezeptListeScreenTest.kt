package de.foody.feature.rezepte.liste

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.testing.ViewModelHalter
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

    private fun zeigen(stil: FoodyStil = FoodyStil.MILCHGLAS) {
        val vm = halter.behalten(RezeptListeViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1L }))
        compose.setContent {
            FoodyTheme(stil = stil) {
                val inhalt = @androidx.compose.runtime.Composable {
                    RezeptListeRoute(onOeffnen = { geoeffnet += it }, onNeu = { neu++ }, vm = vm)
                }
                inhalt()
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

    /**
     * Grimoire als Inventar: Das erste Fach ist gewählt, „Aufschlagen“ öffnet es; ein Tipp auf ein anderes Fach wählt
     * es (öffnet nicht), ein zweiter Tipp schlägt es auf.
     */
    @Test
    fun grimoireWaehltUndSchlaegtAuf() {
        runBlocking {
            db.bestand()
            db.rezeptDao().upsert(
                RezeptEntity(
                    id = "r2", name = "Shakshuka", defaultServings = 2, prepMinutes = 10, cookMinutes = 15, imageUri = null,
                    notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
                ),
            )
        }
        zeigen(stil = FoodyStil.SAGE)
        compose.warteAufText("GRIMOIRE")
        val faecher = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        val n = faecher.fetchSemanticsNodes().size
        assertTrue(n >= 2, "Fächer: $n")
        faecher[0].assertIsSelected()
        compose.onNodeWithText("AUFSCHLAGEN").performClick()
        assertEquals(1, geoeffnet.size)
        faecher[1].performClick()
        faecher[1].assertIsSelected()
        assertEquals(1, geoeffnet.size)
        faecher[1].performClick()
        assertEquals(2, geoeffnet.size)
        assertTrue(geoeffnet[0] != geoeffnet[1])
    }

    @Test
    fun grimoireSucheUndNeu() {
        runBlocking { db.bestand() }
        zeigen(stil = FoodyStil.SAGE)
        compose.warteAufText("GRIMOIRE")
        compose.onNode(hasSetTextAction()).performTextInput("Reispfanne")
        compose.warteAufText("1 Rezept")
        compose.onNodeWithContentDescription("Öffnen: Reispfanne").performClick()
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
