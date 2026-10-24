package de.foody.feature.rezepte

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.liste.RezeptListeViewModel
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
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val geoeffnet = mutableListOf<String>()

    @Before
    fun setUp() {
        db = testDatenbank()
        runBlocking { db.bestand() }
    }

    @After
    fun tearDown() = db.close()

    private fun zeigen() {
        val repo = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication())) { 1L }
        val liste = RezeptListeViewModel(repo)
        val detail = RezeptDetailViewModel(repo)
        compose.setContent {
            FoodyTheme {
                RezepteBereich(
                    onOeffnen = { geoeffnet += it }, onNeu = {}, onKochen = { _, _ -> },
                    listeVm = liste, detailVm = detail,
                )
            }
        }
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
