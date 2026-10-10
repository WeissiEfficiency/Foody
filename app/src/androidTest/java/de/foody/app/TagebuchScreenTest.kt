package de.foody.app

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import de.foody.app.ui.tagebuch.HinzufuegenDialog
import de.foody.domain.Mahlzeit
import kotlin.test.assertEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.GoalPreferences
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.TagebuchRepository
import de.foody.app.ui.tagebuch.TagebuchScreen
import de.foody.app.ui.tagebuch.TagebuchViewModel
import de.foody.app.ui.theme.FoodyTheme
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/** Tagebuch-Bildschirm: vier Mahlzeiten, Vorschlag aus dem Plan, „Gegessen“ macht daraus einen Eintrag. */
@RunWith(AndroidJUnit4::class)
class TagebuchScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: FoodyDatabase
    private lateinit var vm: TagebuchViewModel

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        val recipes = RecipeRepository(db.recipeDao())
        db.ingredientDao().upsert(
            IngredientEntity(id = "reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G,
                energyKj = BigDecimal("1464"), createdAt = 0, updatedAt = 0),
        )
        val curry = recipes.save(
            RecipeDraft(id = null, name = "Curry", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("reis", BigDecimal("200"), MeasureUnit.GRAM, null, false))),
        )
        val plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        plan.add(LocalDate.now(), "ABENDESSEN", curry, 2)
        vm = TagebuchViewModel(
            TagebuchRepository(db, db.tagebuchDao()), plan, recipes, IngredientRepository(db, db.ingredientDao(), context),
            GoalPreferences(context), SavedStateHandle(), ohneScan(),
        )
    }

    @After fun tearDown() {
        vm.aufraeumen()
        db.close()
    }

    @Test fun vorschlagWirdZumEintrag() {
        compose.setContent { FoodyTheme { TagebuchScreen(vm = vm) } }
        for (titel in listOf("Frühstück", "Mittagessen", "Snack", "Abendessen")) {
            compose.onNodeWithText(titel).performScrollTo().assertIsDisplayed()
        }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Geplant: Curry")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Gegessen").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Geplant: Curry")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Curry").performScrollTo().assertIsDisplayed()
        // Zeile, Mahlzeit und Tag zeigen dieselbe Summe
        assert(compose.onAllNodesWithText("350 kcal").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun portionenKnopfAnDerKarte() {
        compose.setContent { FoodyTheme { TagebuchScreen(vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Geplant: Curry")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Portionen wählen").performScrollTo().performClick()
        compose.onNodeWithText("1 Portion").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("1,5 Portionen").assertIsDisplayed()
        compose.onAllNodesWithText("Gegessen").onLast().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("525 kcal")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun doppeltippLegtZutatNurEinmalAn() {
        compose.setContent {
            FoodyTheme {
                val s by vm.state.collectAsState()
                HinzufuegenDialog(Mahlzeit.SNACK, s, vm, onDismiss = {})
            }
        }
        compose.waitUntil(5_000) { vm.state.value.zutaten.isNotEmpty() }
        compose.onNodeWithText("Zutat").performClick()
        compose.onNode(hasSetTextAction() and hasText("Zutat suchen")).performTextInput("Reis")
        compose.onNode(hasText("Reis") and !hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Menge")).performTextInput("100")
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.waitForIdle()
        runBlocking { kotlinx.coroutines.delay(500) }
        assertEquals(1, runBlocking { db.tagebuchDao().getAll() }.size)
    }

    @Test fun bearbeitenUeberstehtWiederherstellung() {
        vm.rezeptEintragen(Mahlzeit.MITTAGESSEN, runBlocking { db.recipeDao().getAll().single().id }, BigDecimal.ONE)
        val tester = StateRestorationTester(compose)
        tester.setContent { FoodyTheme { TagebuchScreen(vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("1 Portion")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 Portion").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("1,5 Portionen").assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1,5 Portionen").assertIsDisplayed()
    }

    @Test fun packungImReiterFrei() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val skyr = de.foody.app.scan.Packung(
            "Skyr", NutrientBasis.PER_100_G, mapOf(de.foody.domain.Nutrient.ENERGY_KJ to BigDecimal(260)), "4001234567890",
            de.foody.app.scan.OpenFoodFacts.QUELLE,
        )
        val mitScan = TagebuchViewModel(
            TagebuchRepository(db, db.tagebuchDao()), PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao()),
            RecipeRepository(db.recipeDao()), IngredientRepository(db, db.ingredientDao(), context), GoalPreferences(context), SavedStateHandle(),
            de.foody.app.scan.PackungScan(
                leser = { de.foody.app.scan.StrichcodeStatus.Gelesen("4001234567890") }, tabelle = { de.foody.app.scan.ScanStatus.Fehler },
                suche = { de.foody.app.scan.ProduktSuche.Antwort.Gefunden(skyr) }, katalog = { null }, play = { true }, online = { true },
            ),
        )
        compose.setContent {
            FoodyTheme {
                val s by mitScan.state.collectAsState()
                HinzufuegenDialog(Mahlzeit.SNACK, s, mitScan, onDismiss = {})
            }
        }
        compose.onNodeWithText("Frei").performClick()
        compose.onNodeWithText("Von Packung scannen").performClick()
        compose.onNodeWithText("Strichcode scannen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Als Zutat im Katalog speichern")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Skyr")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Menge", substring = true)).performTextInput("150")
        compose.onNodeWithText("≈ 93 kcal").assertExists()
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.waitUntil(5_000) { runBlocking { db.tagebuchDao().getAll().isNotEmpty() } }
        assertEquals("4001234567890", runBlocking { db.ingredientDao().findByNameExact("Skyr") }?.barcode)
        mitScan.aufraeumen()
    }
}
