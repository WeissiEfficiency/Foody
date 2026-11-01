package de.foody.feature.rezepte

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.testing.ViewModelHalter
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.liste.RezeptListeViewModel
import kotlin.coroutines.EmptyCoroutineContext
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
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezepteBereichTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val geoeffnet = mutableListOf<String>()
    private var einkauf = 0

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

    private fun zeigen() {
        val ablage = FotoAblage(RuntimeEnvironment.getApplication())
        val repo = RezeptRepository(db, ablage, EmptyCoroutineContext) { 1L }
        val liste = halter.behalten(RezeptListeViewModel(repo))
        val detail = halter.behalten(RezeptDetailViewModel(repo, db.einkauf()))
        val editor = halter.behalten(EditorViewModel(SavedStateHandle(), repo, db, ablage))
        compose.setContent {
            FoodyTheme {
                RezepteBereich(
                    onOeffnen = { geoeffnet += it }, onNeu = {}, onKochen = { _, _ -> }, onEinkauf = { einkauf++ },
                    listeVm = liste, detailVm = detail, editorVm = editor,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletFragtVorVerwerfen() {
        // Ungespeicherte Eingaben rechts gehen beim Antippen einer Karte links nicht still verloren
        zeigen()
        compose.warteAufText("Wähle ein Rezept")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Bearbeiten").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("Reispfanne")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Reispfanne")).performTextReplacement("Reispfanne scharf")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("Änderungen verwerfen?")
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.onNode(hasSetTextAction() and hasText("Reispfanne scharf")).assertExists()
        compose.onNodeWithText("Reispfanne").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.warteAufText("400 g")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletZweitesNeuesRezeptBeginntLeer() {
        // Ein Editor-ViewModel für alle Aufträge: Eingaben des ersten Auftrags dürfen im zweiten nicht stehen
        zeigen()
        compose.warteAufText("Wähle ein Rezept")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Bearbeiten").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("Reispfanne")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Reispfanne")).performTextReplacement("Reispfanne scharf")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Bearbeiten").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction() and hasText("Reispfanne")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Reispfanne scharf")).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletAufEinkaufslisteAnsehen() {
        // Rechts eingebettetes Detail: „Ansehen“ muss bis zur App durchgereicht werden
        zeigen()
        compose.warteAufText("Wähle ein Rezept")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Auf die Einkaufsliste").performClick()
        compose.warteAufText("Hinzufügen")
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.warteAufText("Ansehen")
        compose.onNodeWithText("Ansehen").performClick()
        compose.waitUntil(5_000) { einkauf == 1 }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletZweispaltig() {
        zeigen()
        compose.warteAufText("Wähle ein Rezept")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("400 g")
        compose.onNodeWithText("Rezepte suchen").assertExists()
        assertTrue(geoeffnet.isEmpty(), "Tablet öffnet rechts, ohne Navigation")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun handyNavigiert() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Reispfanne").performClick()
        assertEquals(listOf("r1"), geoeffnet)
    }
}
