package de.foody.feature.heute.tagebuch

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ZutatTreffer
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.bestand
import de.foody.feature.heute.haferflocken
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals

/** Blatt „Eintragen“ → „Frei“ mit dem Scan-Knopf; der Scan ist ein Fake ohne Play-Dienste. Ohne Breiten-Qualifier (siehe [EintragenBlattTest]). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EintragenBlattPackungTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank

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

    private fun zeigen(scan: PackungScan, schrift200: Boolean = false): EintragenViewModel {
        // ViewModel außerhalb der Komposition, wie in der App
        val vm = halter.behalten(EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, scan))
        val auftrag = BlattAuftrag(Mahlzeit.FRUEHSTUECK, heute)
        compose.setContent {
            FoodyTheme {
                if (schrift200) MitSchrift200 { EintragenBlatt(auftrag, {}, vm) } else EintragenBlatt(auftrag, {}, vm)
            }
        }
        compose.warteAufText("Frei")
        compose.onNodeWithText("Frei").performClick()
        return vm
    }

    private fun scannen() {
        compose.onNodeWithText("Packung scannen").performScrollTo().performClick()
        compose.onNodeWithText("Strichcode").performClick()
        compose.waitForIdle()
    }

    private fun packungZeigen(p: Packungswerte = haferflocken(), schrift200: Boolean = false): EintragenViewModel {
        val vm = zeigen(testScan(packung = p), schrift200)
        scannen()
        compose.warteAufText("Werte aus Open Food Facts – bitte prüfen")
        return vm
    }

    @Test
    fun knopfInFrei() {
        zeigen(testScan())
        compose.onNodeWithText("Packung scannen").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
    }

    @Test
    fun packungsansicht() {
        packungZeigen()
        compose.onNode(hasSetTextAction() and hasText("Haferflocken")).assertExists()
        compose.onNodeWithText("je 100 g").assertExists()
        compose.onNode(hasSetTextAction() and hasText("359")).assertExists()
        compose.onNodeWithText("Als Zutat speichern").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Menge")).performTextInput("100")
        compose.onNodeWithText("= 359 kcal").assertExists()
        // Mit Basis keine Wahl zwischen g und ml
        assertEquals(0, compose.onAllNodesWithText("ml").fetchSemanticsNodes().size)
    }

    @Test
    fun chipsOhneBasis() {
        packungZeigen(haferflocken(basis = null))
        compose.onNode(hasText("g") and isSelected()).assertExists()
        compose.onNode(hasText("ml") and hasClickAction()).performClick()
        compose.onNode(hasText("ml") and isSelected()).assertExists()
        compose.onNodeWithText("je 100 ml").assertExists()
    }

    @Test
    fun zurueckZuFrei() {
        packungZeigen()
        compose.onNodeWithText("Zurück zu Frei").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Werte aus Open Food Facts – bitte prüfen").fetchSemanticsNodes().size)
        compose.onNodeWithText("Packung scannen").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Name")).assertExists()
    }

    @Test
    fun imKatalogFokusAufMenge() {
        zeigen(testScan(imKatalog = ZutatTreffer("z-reis", "Reis")))
        scannen()
        compose.onNode(hasText("Zutat") and isSelected()).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Reis")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Menge")).assertIsFocused()
    }

    @Test
    fun schrift200() {
        packungZeigen(haferflocken(basis = null), schrift200 = true)
        compose.onNode(hasSetTextAction() and hasText("Menge")).performTextInput("100")
        compose.pruefeNichtsAbgeschnitten()
    }
}
