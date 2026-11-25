package de.foody.feature.einkauf.stil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.einkauf.EinkaufRoute
import de.foody.feature.einkauf.EinkaufTestUmgebung
import de.foody.feature.einkauf.EinkaufViewModel
import de.foody.feature.einkauf.MITTWOCH
import de.foody.feature.einkauf.MONTAG
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Einkaufs-Szenen der Stile R–V in Immersiv: Titel für TalkBack „Einkauf“, Antippen hakt ab, Langdruck öffnet
 * die Details. Die Abteilungen wählt man in allen Szenen über Pillen
 * im Stil. In Q–V führt nur der Knopf „Listen“ zu den Listen (kein
 * Menü ⋮), dort steht im Kopf auch „Diese Liste löschen“; Milchglas behält das Menü.
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

    private fun zeigen(stil: FoodyStil): EinkaufViewModel {
        liste()
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable { EinkaufRoute(null, {}, {}, vm = vm) }
                inhalt()
            }
        }
        compose.warteAufText("Zwiebeln")
        return vm
    }

    /** Der Posten in der Szene (mit Langdruck „Details“), nicht die Kachel im Katalog. */
    private fun posten(name: String) = hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

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

    private fun listenSeiteDa() = compose.onAllNodes(hasContentDescription("Einkaufslisten") and isHeading()).fetchSemanticsNodes().isNotEmpty()

    @Test fun anime() = pruefen(FoodyStil.ANIME)

    @Test fun edo() = pruefen(FoodyStil.EDO)

    @Test fun bayrisch() = pruefen(FoodyStil.BAYRISCH)

    @Test fun grauen() = pruefen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = pruefen(FoodyStil.KREUZER)

    /** Q–V in beiden Varianten: „Listen“ ist der einzige Weg zu den Listen, kein Menü ⋮ daneben. */
    @Test
    fun keinMenueInDenStilen() {
        liste()
        val vm = u.vm()
        var auswahl by mutableStateOf(FoodyStil.SAGE to StilVariante.IMMERSIV)
        compose.setContent {
            val (stil, variante) = auswahl
            FoodyTheme(stil = stil, variante = variante) { EinkaufRoute(null, {}, {}, vm = vm) }
        }
        compose.warteAufText("Zwiebeln")
        for (stil in listOf(FoodyStil.SAGE, FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            for (variante in listOf(StilVariante.IMMERSIV, StilVariante.RUHIG)) {
                auswahl = stil to variante
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Weitere Aktionen").assertDoesNotExist()
                compose.onNodeWithContentDescription("Einkaufslisten").assertExists()
            }
        }
    }

    /** Pille einer Abteilung unter „Hinzufügen“ (wählbar), deren Text [teil] enthält. */
    private fun pille(teil: String) = hasText(teil, substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    /**
     * Alle Szenen (Immersiv): Unter „Hinzufügen“ wählt eine Pille im Stil die Abteilung, darunter stehen nur deren
     * Kacheln; nichts klappt auf und nichts scrollt waagerecht.
     */
    @Test
    fun abteilungPerPille() {
        liste()
        val vm = u.vm()
        var stil by mutableStateOf(FoodyStil.SAGE)
        compose.setContent { FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) { EinkaufRoute(null, {}, {}, vm = vm) } }
        compose.warteAufText("Zwiebeln")
        val raster = compose.onNode(hasScrollToIndexAction())
        for (s in listOf(FoodyStil.SAGE, FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            stil = s
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Hinzufügen") and isHeading()).assertExists()
            raster.performScrollToNode(pille("Getränke"))
            compose.onNode(pille("Getränke")).performClick()
            raster.performScrollToNode(hasText("Mineralwasser"))
            compose.onAllNodesWithText("Toilettenpapier").assertCountEquals(0)
            raster.performScrollToNode(pille("Haushalt"))
            compose.onNode(pille("Haushalt")).performClick()
            raster.performScrollToNode(hasText("Toilettenpapier"))
            compose.onAllNodesWithText("Mineralwasser").assertCountEquals(0)
            compose.onAllNodes(SemanticsMatcher("Aufklappen") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Aufklappen" })
                .assertCountEquals(0)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        }
    }

    /** „Listen“ öffnet die Listen-Seite wie im Entwurf auf dem ersten Reiter „Neue Liste“. */
    @Test
    fun listenOeffnetNeueListe() {
        zeigen(FoodyStil.ANIME)
        compose.onNodeWithContentDescription("Einkaufslisten").performClick()
        compose.waitUntil(5_000) { listenSeiteDa() }
        // Die Reiter heißen für TalkBack wie ihr Inhalt; das Wort des Stils („Vorschau“, …) ist nur zu sehen
        compose.onNode(hasContentDescription("Neue Liste") and isSelected()).assertExists()
    }

    /** Milchglas hat keinen Knopf „Listen“ und behält das Menü ⋮. */
    @Test
    fun milchglasBehaeltMenue() {
        zeigen(FoodyStil.MILCHGLAS)
        compose.onNodeWithContentDescription("Weitere Aktionen").assertExists()
        compose.onNodeWithContentDescription("Einkaufslisten").assertDoesNotExist()
    }

    /** Der Papierkorb im Kopf der Listen-Seite fragt nach; nach dem Löschen ist die Liste weg und die Seite zu. */
    @Test
    fun loeschenAufListenSeite() {
        val vm = zeigen(FoodyStil.ANIME)
        compose.onNodeWithContentDescription("Einkaufslisten").performClick()
        compose.waitUntil(5_000) { listenSeiteDa() }
        compose.onNodeWithContentDescription("Diese Liste löschen").performClick()
        compose.warteAufText("Liste löschen?")
        compose.onNodeWithText("Löschen").performClick()
        compose.waitUntil(5_000) { runBlocking { db.einkaufslisteDao().alle() }.isEmpty() }
        compose.waitUntil(5_000) { !listenSeiteDa() }
        assertNull(vm.blatt.value)
    }

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
