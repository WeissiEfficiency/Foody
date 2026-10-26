package de.foody.app

import de.foody.domain.ShoppingCatalog
import de.foody.app.data.ShoppingPreferences
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.ShoppingRepository
import de.foody.app.ui.shopping.ShoppingScreen
import de.foody.app.ui.shopping.ShoppingViewModel
import de.foody.app.ui.theme.FoodyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Kachel-Einkaufsliste: Katalog → rot (auf der Liste) → gekauft → „Zuletzt verwendet“ → wieder auf die Liste. */
@RunWith(AndroidJUnit4::class)
class ShoppingTilesTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: FoodyDatabase
    private lateinit var vm: ShoppingViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        vm = ShoppingViewModel(ShoppingRepository(db), ShoppingPreferences(context), SavedStateHandle())
        compose.setContent { FoodyTheme { ShoppingScreen(vm) } }
    }

    @After fun tearDown() {
        vm.aufraeumen()
        db.close()
    }

    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
    private fun openItems() = runBlocking { db.shoppingDao().getAllItems().filter { !it.checked }.map { it.name } }

    private fun waitForOpen(expected: List<String>) =
        compose.waitUntil(WAIT_MS) { openItems() == expected }

    // Room meldet Änderungen über einen Flow auf einem Hintergrund-Thread, den die Compose-Idling-Logik
    // nicht kennt: Steht etwas in der DB, kann die Kachel noch den alten Zustand zeigen. Daher auf die UI warten.
    private fun waitForNode(matcher: SemanticsMatcher) =
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    // Dasselbe im Lazy-Grid: Kacheln außerhalb des Bildschirms stehen nicht im Semantik-Baum, also bis zum Treffer scrollen
    private fun scrollToNode(grid: SemanticsNodeInteraction, matcher: SemanticsMatcher) =
        compose.waitUntil(WAIT_MS) { runCatching { grid.performScrollToNode(matcher) }.isSuccess }

    @Test fun catalogTileToListAndBack() {
        // Abteilung aufklappen und Bananen antippen → legt automatisch eine Liste an
        compose.onNode(hasText("Obst & Gemüse")).performClick()
        compose.onAllNodes(hasText("Bananen") and state("nicht auf der Liste")).onFirst().performClick()
        waitForOpen(listOf("Bananen"))

        // Rote Kachel oben antippen = gekauft
        waitForNode(hasText("Bananen") and state("auf der Liste"))
        compose.onAllNodes(hasText("Bananen") and state("auf der Liste")).onFirst().performClick()
        waitForOpen(emptyList())
        waitForNode(hasText("Nichts einzukaufen!"))

        // „Zuletzt verwendet“ → wieder auf die Liste
        waitForNode(hasText("Bananen") and state("nicht auf der Liste"))
        compose.onAllNodes(hasText("Bananen") and state("nicht auf der Liste")).onFirst().performClick()
        waitForOpen(listOf("Bananen"))
        assertEquals(1, runBlocking { db.shoppingDao().getAllItems().size }, "kein doppelter Eintrag")
    }

    @Test fun sectionCollapsesAgainWithoutOpeningAnother() {
        val header = compose.onNode(hasText("Obst & Gemüse"))
        header.performClick()
        compose.onAllNodes(hasText("Bananen")).onFirst().assertExists()
        // Früher blieb die Abteilung offen, bis man eine andere aufklappte
        header.performClick()
        compose.onAllNodes(hasText("Bananen")).assertCountEquals(0)
        header.performClick()
        compose.onAllNodes(hasText("Bananen")).onFirst().assertExists()
    }

    /** Final-Review 1: Kachel eines eigenen Artikels (nicht im Katalog) hakt ab, statt ihn doppelt anzulegen. */
    @Test fun ownItemTileTogglesInsteadOfDuplicating() {
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("Grillkohle XL")
        field.performImeAction()
        waitForOpen(listOf("Grillkohle XL"))
        // Die Kachel in der Abteilung „Eigene Artikel“ (nicht die rote Kachel oben) geht über tapCatalog
        val grid = compose.onNode(hasScrollAction())
        scrollToNode(grid, hasText(ShoppingCatalog.OWN_ITEMS))
        compose.onNode(hasText(ShoppingCatalog.OWN_ITEMS)).performClick()
        scrollToNode(grid, hasText("Grillkohle XL") and state("auf der Liste"))
        compose.onAllNodes(hasText("Grillkohle XL") and state("auf der Liste")).onLast().performClick()
        waitForOpen(emptyList())
        scrollToNode(grid, hasText("Grillkohle XL") and state("nicht auf der Liste"))
        compose.onAllNodes(hasText("Grillkohle XL") and state("nicht auf der Liste")).onLast().performClick()
        waitForOpen(listOf("Grillkohle XL"))
        assertEquals(1, runBlocking { db.shoppingDao().getAllItems().size }, "kein doppelter Eintrag")
    }

    @Test fun searchAddsOwnItemOrKnownCatalogItem() {
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("schoki")
        field.performImeAction()
        waitForOpen(listOf("schoki"))

        field.performTextInput("Milch")
        field.performImeAction()
        compose.waitUntil(WAIT_MS) { openItems().toSet() == setOf("schoki", "Milch") }
        val milk = runBlocking { db.shoppingDao().getAllItems().single { it.name == "Milch" } }
        assertEquals("Milch & Käse", milk.category, "Katalogartikel bekommt seine Abteilung")
    }

    private companion object {
        // Großzügig für den überlasteten CI-Emulator (drei Managed Devices parallel, Software-Rendering)
        const val WAIT_MS = 15_000L
    }
}
