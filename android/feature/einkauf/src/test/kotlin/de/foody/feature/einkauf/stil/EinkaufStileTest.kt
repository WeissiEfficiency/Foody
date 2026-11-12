package de.foody.feature.einkauf.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.einkauf.EinkaufRoute
import de.foody.feature.einkauf.EinkaufTestUmgebung
import de.foody.feature.einkauf.MITTWOCH
import de.foody.feature.einkauf.MONTAG
import de.foody.feature.einkauf.MitSchrift200
import de.foody.feature.einkauf.einkaufBestand
import de.foody.feature.einkauf.planen
import de.foody.feature.einkauf.pruefeNichtsAbgeschnitten
import de.foody.feature.einkauf.testDatenbank
import de.foody.feature.einkauf.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Einkaufs-Szenen der Stile R–V in Immersiv: Titel für TalkBack „Einkauf“, Antippen hakt ab, Langdruck öffnet
 * die Details, der Knopf „Frühere Listen“ das Blatt; bei 200 % Schrift wird nichts abgeschnitten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class EinkaufStileTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung

    @Before
    fun setUp() {
        db = testDatenbank()
        u = EinkaufTestUmgebung(db)
        runBlocking { db.einkaufBestand() }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    /** Curry (Mo) und Reispfanne (Mi), Reis schon gekauft. */
    private fun liste() = runBlocking {
        db.planen("s1", MONTAG, "c")
        db.planen("s2", MITTWOCH, "p")
        val id = u.repo.ausPlanAnlegen(MONTAG, MONTAG.plusDays(6), emptySet())!!
        u.repo.abhaken(db.einkaufsartikelDao().nachListe(id).single { it.name == "Reis" }.id, true)
    }

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false) {
        liste()
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable { EinkaufRoute(null, {}, {}, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Zwiebeln")
    }

    /** Der Posten (Checkbox) in der Szene, nicht die Kachel im Katalog. */
    private fun posten(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun gekauft(name: String) = runBlocking { db.einkaufsartikelDao().alle().single { it.name == name }.checked }

    private fun pruefen(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes((hasContentDescription("Einkauf") or hasText("Einkauf")) and isHeading()).onFirst().assertExists()
        // Antippen hakt ab und wieder auf
        compose.onNode(posten("Zwiebeln")).performClick()
        compose.waitUntil(5_000) { gekauft("Zwiebeln") }
        compose.onNode(posten("Zwiebeln")).performClick()
        compose.waitUntil(5_000) { !gekauft("Zwiebeln") }
        assertTrue(gekauft("Reis"))
        // Langdruck öffnet die Details
        compose.onNode(posten("Zwiebeln")).performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Ziehgriff").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun frueher(stil: FoodyStil) {
        zeigen(stil)
        compose.onNodeWithContentDescription("Frühere Listen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Ziehgriff").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gross(stil: FoodyStil) {
        zeigen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun anime() = pruefen(FoodyStil.ANIME)

    @Test fun edo() = pruefen(FoodyStil.EDO)

    @Test fun bayrisch() = pruefen(FoodyStil.BAYRISCH)

    @Test fun grauen() = pruefen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = pruefen(FoodyStil.KREUZER)

    @Test fun animeFrueher() = frueher(FoodyStil.ANIME)

    @Test fun edoFrueher() = frueher(FoodyStil.EDO)

    @Test fun bayrischFrueher() = frueher(FoodyStil.BAYRISCH)

    @Test fun grauenFrueher() = frueher(FoodyStil.GRAUEN)

    @Test fun kreuzerFrueher() = frueher(FoodyStil.KREUZER)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("Vom Gmiasstandl", bayrischAbteilung("Obst & Gemüse"))
        assertEquals("Konserven", bayrischAbteilung("Konserven"))
        assertEquals("no 3 offn", bayrischOffen(3))
        assertEquals("nix mehr offn", bayrischOffen(0))
        assertEquals("Vom Markt", edoAbteilung("Obst & Gemüse"))
        assertEquals("Aus der Kälte", grauenAbteilung("Kühlregal"))
        assertEquals("Aus der Kälte", grauenAbteilung("Milch & Käse"))
        assertEquals("Für d'Speis", bayrischAbteilung("Getreideprodukte"))
        assertEquals("Ein Posten fehlt noch. Ohne ihn kehrt niemand zurück.", grauenFluestern(1))
        assertEquals("OBS", kistenCode("Obst & Gemüse"))
        assertEquals("ÖLE", kistenCode("Öle & Gewürze"))
        assertEquals("---", kistenCode("&"))
    }
}
