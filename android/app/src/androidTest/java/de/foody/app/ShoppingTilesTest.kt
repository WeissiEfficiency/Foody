package de.foody.app

import de.foody.app.data.ShoppingPreferences
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
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
        compose.waitUntil(5_000) { openItems() == expected }

    @Test fun catalogTileToListAndBack() {
        // Abteilung aufklappen und Bananen antippen → legt automatisch eine Liste an
        compose.onNode(hasText("Obst & Gemüse")).performClick()
        compose.onAllNodes(hasText("Bananen") and state("nicht auf der Liste")).onFirst().performClick()
        waitForOpen(listOf("Bananen"))

        // Rote Kachel oben antippen = gekauft
        compose.onAllNodes(hasText("Bananen") and state("auf der Liste")).onFirst().performClick()
        waitForOpen(emptyList())
        compose.onNode(hasText("Nichts einzukaufen!")).assertExists()

        // „Zuletzt verwendet“ → wieder auf die Liste
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

    @Test fun searchAddsOwnItemOrKnownCatalogItem() {
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("schoki")
        field.performImeAction()
        waitForOpen(listOf("schoki"))

        field.performTextInput("Milch")
        field.performImeAction()
        compose.waitUntil(5_000) { openItems().toSet() == setOf("schoki", "Milch") }
        val milk = runBlocking { db.shoppingDao().getAllItems().single { it.name == "Milch" } }
        assertEquals("Milch & Käse", milk.category, "Katalogartikel bekommt seine Abteilung")
    }
}
