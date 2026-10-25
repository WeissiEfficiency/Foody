package de.foody.feature.einkauf

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
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

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EinkaufScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung
    private val geoeffnet = mutableListOf<String>()
    private var aktuelle = 0

    @Before
    fun setUp() {
        db = testDatenbank()
        u = EinkaufTestUmgebung(db)
        runBlocking { db.einkaufBestand() }
    }

    @After
    fun tearDown() = db.close()

    private fun zeigen(listeId: String? = null, schrift200: Boolean = false): EinkaufViewModel {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    EinkaufRoute(listeId, onListe = { geoeffnet += it }, onAktuelle = { aktuelle++ }, vm = vm)
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        return vm
    }

    /** Liste „26.10.–1.11.“ aus dem Curry am Mittwoch: Zwiebeln 200 g, Reis 300 g. */
    private fun planListe(): String = runBlocking {
        db.planen("s1", MITTWOCH, "c")
        u.repo.ausPlanAnlegen(MONTAG, MONTAG.plusDays(6), emptySet())!!
    }

    private fun artikel() = runBlocking { db.einkaufsartikelDao().alle() }

    @Test
    fun kachelnJeAbteilung() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onAllNodesWithText("Obst & Gemüse", substring = true).onFirst().assertExists()
        compose.onNodeWithText("200 g").assertExists()
        compose.onNodeWithText("300 g").assertExists()
    }

    @Test
    fun kopfMitZeitraumUndAnzahl() {
        planListe()
        zeigen()
        compose.warteAufText("26.10.–1.11. · 2 offen")
    }

    @Test
    fun abhakenWandertZuZuletzt() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onNodeWithText("Zwiebeln").performClick()
        compose.warteAufText("26.10.–1.11. · 1 offen")
        compose.onNodeWithText("Zuletzt verwendet", substring = true).assertExists()
        assertTrue(artikel().single { it.name == "Zwiebeln" }.checked)
    }

    @Test
    fun eingabeNeuerArtikel() {
        zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNode(hasSetTextAction()).performTextInput("Grillkohle")
        compose.warteAufText("„Grillkohle“ hinzufügen")
        compose.onNodeWithText("„Grillkohle“ hinzufügen").performClick()
        compose.warteAufText("Grillkohle")
        compose.warteAufTeil("Eigene Artikel")
        assertEquals(listOf("Grillkohle"), artikel().map { it.name })
    }

    @Test
    fun eingabeMitEnter() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onNode(hasSetTextAction()).performTextInput("zwiebeln")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { artikel().single { it.name == "Zwiebeln" }.checked }
    }

    @Test
    fun katalogAufklappenUndAntippen() {
        zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onAllNodesWithText("Brot & Gebäck", substring = true).onFirst().performScrollTo().performClick()
        compose.warteAufText("Brötchen")
        compose.onNodeWithText("Brötchen").performScrollTo().performClick()
        compose.waitUntil(5_000) { artikel().any { it.name == "Brötchen" && !it.checked } }
    }

    @Test
    fun leerzustandMitPlanKnopf() {
        val vm = zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
        compose.waitUntil(5_000) { vm.blatt.value == Blatt.Vorschau }
    }

    @Test
    fun hinweisNurBeiAbgleich() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onNodeWithText("Plan geändert – abgleichen").assertDoesNotExist()
        runBlocking { db.planen("s1", MITTWOCH, "c", portionen = 4) }
        compose.warteAufText("Plan geändert – abgleichen")
    }

    @Test
    fun listeFehlt() {
        zeigen(listeId = "weg")
        compose.warteAufText("Diese Liste gibt es nicht mehr")
        compose.onNodeWithText("Zur aktuellen Liste").performClick()
        assertEquals(1, aktuelle)
    }

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tabletZweiBereiche() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.warteAufTeil("Brot & Gebäck")
        val zwiebeln = compose.onNodeWithText("Zwiebeln").getUnclippedBoundsInRoot()
        val brot = compose.onAllNodesWithText("Brot & Gebäck", substring = true).onFirst().getUnclippedBoundsInRoot()
        assertTrue(brot.left > zwiebeln.right, "Katalog rechts neben der Liste")
    }

    @Test
    fun schrift200OhneAbschneiden() {
        planListe()
        zeigen(schrift200 = true)
        compose.warteAufText("Zwiebeln")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun ueberstehtDrehen() {
        val vm = u.vm()
        val tester = StateRestorationTester(compose)
        tester.setContent { FoodyTheme { EinkaufRoute(null, {}, {}, vm = vm) } }
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNode(hasSetTextAction()).performTextInput("Grill")
        tester.emulateSavedInstanceStateRestore()
        compose.warteAufText("„Grill“ hinzufügen")
    }

    // ---- Blätter ----

    @Test
    fun vorschauAbwaehlenUndAnlegen() {
        runBlocking { db.planen("s1", MITTWOCH, "c") }
        zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
        compose.warteAufText("Liste anlegen (2)")
        compose.onNodeWithContentDescription("Reis übernehmen").performClick()
        compose.warteAufText("Liste anlegen (1)")
        compose.onNodeWithText("Liste anlegen (1)").performClick()
        compose.waitUntil(5_000) { artikel().map { it.name } == listOf("Zwiebeln") }
        compose.warteAufText("28.10.–3.11. · 1 offen")
    }

    @Test
    fun vorschauNichtsGeplant() {
        zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
        compose.warteAufText("Im Zeitraum ist nichts geplant")
        compose.onNodeWithText("Liste anlegen (0)").assertIsNotEnabled()
    }

    @Test
    fun vorschauZeitraumVerschieben() {
        runBlocking { db.planen("s1", MITTWOCH.plusDays(7), "c") }
        zeigen()
        compose.warteAufText("Noch nichts auf der Liste")
        compose.onNodeWithText("Neue Liste aus dem Plan").performClick()
        compose.warteAufText("Im Zeitraum ist nichts geplant")
        compose.onNodeWithContentDescription("Ende später").performClick()
        compose.warteAufText("Liste anlegen (2)")
    }

    @Test
    fun abgleichUebernehmen() {
        planListe()
        zeigen()
        compose.warteAufText("Zwiebeln")
        runBlocking { db.planen("s1", MITTWOCH, "c", portionen = 4) }
        compose.warteAufText("Plan geändert – abgleichen")
        compose.onNodeWithText("Plan geändert – abgleichen").performClick()
        compose.warteAufText("Übernehmen (2)")
        compose.onNodeWithText("200 g → 400 g", substring = true).assertExists()
        compose.onNodeWithText("Übernehmen (2)").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Plan geändert – abgleichen").fetchSemanticsNodes().isEmpty() }
        compose.warteAufText("400 g")
    }

    private fun milchListe() = runBlocking {
        db.einkaufslisteDao().upsert(liste("l1", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l1", "Milch"))
    }

    @Test
    fun detailsSpeichern() {
        milchListe()
        zeigen()
        compose.warteAufText("Milch")
        compose.onNodeWithText("Milch").performTouchInput { longClick() }
        compose.warteAufText("Speichern")
        compose.onNode(hasSetTextAction() and hasText("Notiz")).performTextInput("Bio")
        compose.onNodeWithText("Speichern").performClick()
        compose.warteAufText("Bio")
        assertEquals("Bio", artikel().single().note)
    }

    @Test
    fun detailsHerkunft() {
        runBlocking {
            db.planen("s1", MONTAG, "c")
            db.planen("s2", MITTWOCH, "p")
            u.repo.ausPlanAnlegen(MONTAG, MONTAG.plusDays(6), emptySet())
        }
        zeigen()
        compose.warteAufText("Zwiebeln")
        compose.onNodeWithText("Zwiebeln").performTouchInput { longClick() }
        compose.warteAufText("200 g Curry (Mo) + 100 g Reispfanne (Mi)")
    }

    @Test
    fun loeschenMitRueckgaengig() {
        milchListe()
        zeigen()
        compose.warteAufText("Milch")
        compose.onNodeWithText("Milch").performTouchInput { longClick() }
        compose.warteAufText("Löschen")
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText("Rückgängig")
        compose.waitUntil(5_000) { artikel().isEmpty() }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { artikel().size == 1 }
    }

    @Test
    fun rueckgaengigVerschwindetNachEinerWeile() {
        milchListe()
        zeigen()
        compose.warteAufText("Milch")
        compose.onNodeWithText("Milch").performTouchInput { longClick() }
        compose.warteAufText("Löschen")
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText("Rückgängig")
        compose.mainClock.advanceTimeBy(15_000)
        compose.waitForIdle()
        compose.onNodeWithText("Rückgängig").assertDoesNotExist()
    }

    @Test
    fun fruehereOeffnen() {
        runBlocking {
            db.einkaufslisteDao().upsert(liste("alt", 1, name = "12.10.–18.10."))
            db.einkaufslisteDao().upsert(liste("neu", 2))
        }
        zeigen()
        compose.warteAufText("neu · 0 offen")
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Frühere Listen").performClick()
        compose.warteAufText("12.10.–18.10.")
        compose.onNodeWithText("12.10.–18.10.").performClick()
        assertEquals(listOf("alt"), geoeffnet)
    }

    @Test
    fun listeLoeschenMitRueckfrage() {
        milchListe()
        zeigen()
        compose.warteAufText("Milch")
        compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
        compose.onNodeWithText("Liste löschen").performClick()
        compose.warteAufText("Liste löschen?")
        compose.onNodeWithText("Löschen").performClick()
        compose.waitUntil(5_000) { runBlocking { db.einkaufslisteDao().alle() }.isEmpty() }
        compose.warteAufText("Noch nichts auf der Liste")
    }
}
