package de.foody.feature.rezepte.detail.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.ViewModelHalter
import de.foody.feature.rezepte.MitSchrift200
import de.foody.feature.rezepte.bestand
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.einkauf
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

/**
 * Die Rezept-Szenen der Stile R–V in Immersiv: Name als Überschrift, Portionen skalieren, die drei Reiter, Kochen,
 * Favorit, Einkaufsliste, Menü, Zurück, Archivieren und Zurückholen; bei 200 % Schrift wird nichts abgeschnitten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class RezeptStileTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val gekocht = mutableListOf<Pair<String, Int>>()
    private val bearbeitet = mutableListOf<String>()
    private var zurueck = 0

    @Before
    fun setUp() {
        db = testDatenbank()
        runBlocking {
            db.bestand()
            db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 0, "Reis kochen."))
        }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false) {
        val vm = halter.behalten(
            RezeptDetailViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1_000L }, db.einkauf()),
        )
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable {
                    RezeptDetailRoute(
                        "r1", onKochen = { r, p -> gekocht += r to p }, onBearbeiten = { bearbeitet += it },
                        onDupliziert = {}, onZurueck = { zurueck++ }, vm = vm,
                    )
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Reispfanne")
    }

    private fun warteAufBeschreibung(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    private fun reiter(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(isHeading()).onFirst().assertExists()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.warteAufText("600 g")
        compose.onNodeWithContentDescription("Zubereitung").performClick()
        compose.warteAufText("Reis kochen.")
        compose.onNodeWithContentDescription("Schritt 1").assertExists()
        compose.onNodeWithContentDescription("Nährwerte").performClick()
        compose.warteAufText("Energie")
    }

    private fun aktionen(stil: FoodyStil) {
        zeigen(stil)
        compose.onNodeWithContentDescription("Kochen").performClick()
        assertEquals(listOf("r1" to 2), gekocht)
        compose.onNodeWithContentDescription("Als Favorit merken").performClick()
        warteAufBeschreibung("Favorit entfernen")
        compose.onNodeWithContentDescription("Auf die Einkaufsliste").performClick()
        compose.warteAufText("Hinzufügen")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithContentDescription("Bearbeiten").performClick()
        assertEquals(listOf("r1"), bearbeitet)
        compose.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(1, zurueck)
    }

    private fun archiv(stil: FoodyStil) {
        zeigen(stil)
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithContentDescription("Archivieren").performClick()
        warteAufBeschreibung("Wiederherstellen")
        compose.onNodeWithContentDescription("Wiederherstellen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Wiederherstellen").fetchSemanticsNodes().isEmpty() }
    }

    private fun gross(stil: FoodyStil) {
        zeigen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun animeReiter() = reiter(FoodyStil.ANIME)

    @Test fun edoReiter() = reiter(FoodyStil.EDO)

    @Test fun bayrischReiter() = reiter(FoodyStil.BAYRISCH)

    @Test fun grauenReiter() = reiter(FoodyStil.GRAUEN)

    @Test fun kreuzerReiter() = reiter(FoodyStil.KREUZER)

    @Test fun animeAktionen() = aktionen(FoodyStil.ANIME)

    @Test fun edoAktionen() = aktionen(FoodyStil.EDO)

    @Test fun bayrischAktionen() = aktionen(FoodyStil.BAYRISCH)

    @Test fun grauenAktionen() = aktionen(FoodyStil.GRAUEN)

    @Test fun kreuzerAktionen() = aktionen(FoodyStil.KREUZER)

    @Test fun animeArchiv() = archiv(FoodyStil.ANIME)

    @Test fun grauenArchiv() = archiv(FoodyStil.GRAUEN)

    @Test fun kreuzerArchiv() = archiv(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("I", roemisch(1))
        assertEquals("IV", roemisch(4))
        assertEquals("XIX", roemisch(19))
        assertEquals("40", roemisch(40))
        assertEquals("2 PORT.", rezeptArt(FoodyStil.ANIME).portionen(2))
        assertEquals("2 Portiona", rezeptArt(FoodyStil.BAYRISCH).portionen(2))
        assertEquals("CREW 3", rezeptArt(FoodyStil.KREUZER).portionen(3))
        assertEquals("03", rezeptArt(FoodyStil.KREUZER).nummer(3))
        assertEquals("II.", rezeptArt(FoodyStil.GRAUEN).nummer(2))
    }
}
