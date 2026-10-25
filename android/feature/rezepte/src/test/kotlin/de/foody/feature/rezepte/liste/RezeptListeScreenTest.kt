package de.foody.feature.rezepte.liste

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
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

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class RezeptListeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val geoeffnet = mutableListOf<String>()
    private var neu = 0
    private var importiert = 0

    @Before
    fun setUp() {
        db = testDatenbank()
    }

    @After
    fun tearDown() = db.close()

    private fun zeigen(schrift200: Boolean = false) {
        val vm = RezeptListeViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication())) { 1L })
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    RezeptListeRoute(onOeffnen = { geoeffnet += it }, onNeu = { neu++ }, onImport = { importiert++ }, vm = vm)
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
    fun leerZeigtAnlegenUndImportieren() {
        zeigen()
        compose.warteAufText("Noch keine Rezepte")
        compose.onNodeWithText("Anlegen").performClick()
        compose.onNodeWithText("Importieren").performClick()
        assertEquals(1 to 1, neu to importiert)
    }

    @Test
    fun keinTrefferZeigtZuruecksetzen() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Rezepte suchen").performTextInput("xyz")
        compose.warteAufText("Nichts gefunden")
        compose.onNodeWithText("Filter zurücksetzen").performClick()
        compose.warteAufText("Reispfanne")
    }

    @Test
    fun menuNeuUndImport() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Neues Rezept").performClick()
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Markdown importieren").performClick()
        assertEquals(1 to 1, neu to importiert)
    }

    @Test
    fun schrift200OhneAbschneiden() {
        runBlocking { db.bestand() }
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.pruefeNichtsAbgeschnitten()
    }
}
