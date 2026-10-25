package de.foody.feature.woche

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class WocheScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung
    private val gekocht = mutableListOf<Pair<String, Int>>()
    private val rezepte = mutableListOf<String>()

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        db = testDatenbank()
        u = WocheTestUmgebung(db)
        runBlocking { db.bestand() }
    }

    @After
    fun tearDown() = db.close()

    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    private fun zeigen(schrift200: Boolean = false) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    WocheRoute(onKochen = { r, p -> gekocht += r to p }, onRezept = { rezepte += it }, vm = vm)
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    private fun planen(tag: Long, slot: String) = runBlocking { db.planen("p$tag$slot", MONTAG.plusDays(tag), slot) }

    @Test
    fun zeigtWocheMitKcal() {
        planen(2, "ABENDESSEN")
        zeigen()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onNodeWithText("Reispfanne").assertExists()
        compose.onNodeWithText("2 P.").assertExists()
        compose.onNodeWithText("717 kcal").assertExists()
    }

    @Test
    fun durchschnittImKopf() {
        planen(2, "ABENDESSEN")
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Ø", substring = true).assertDoesNotExist()
        planen(2, "MITTAGESSEN")
        compose.warteAufText("Ø 1.434 kcal/Tag")
    }

    @Test
    fun mindBeiFehlendenWerten() {
        runBlocking {
            db.zutatDao().upsert(ZutatEntity(id = "z-x", canonicalName = "Unbekannt", createdAt = 1, updatedAt = 1))
            db.rezeptZutatDao().upsert(RezeptZutatEntity("l2", "r1", "z-x", BigDecimal("50"), MeasureUnit.GRAM, 1, null, false))
        }
        planen(2, "ABENDESSEN")
        zeigen()
        compose.warteAufText("mind. 717 kcal")
    }

    @Test
    fun tageszielBalken() {
        prefs().edit().putInt(Einstellungsschluessel.TAGESZIEL, 2000).commit()
        planen(2, "ABENDESSEN")
        zeigen()
        compose.warteAufText("717 / 2.000 kcal")
    }

    @Test
    fun hinzufuegenMitEigenemNamen() {
        zeigen()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onAllNodesWithText("+ Mahlzeit")[2].performClick()
        compose.warteAufText("Mahlzeit wählen")
        compose.onNode(hasSetTextAction()).performTextInput("Brotzeit")
        compose.onNodeWithText("Weiter").performClick()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("Brotzeit")
        val e = runBlocking { db.planEintragDao().alle().single() }
        assertEquals(MITTWOCH, e.date)
        assertEquals("Brotzeit", e.slotType)
        assertEquals(2, e.servings)
    }

    @Test
    fun blaetternPfeile() {
        zeigen()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onNodeWithContentDescription("Nächste Woche").performClick()
        compose.warteAufText("KW 45 · 2.11.–8.11.")
        compose.onNodeWithText("Heute").performClick()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
    }

    private fun blattOeffnen(tag: Long = 2) {
        planen(tag, "ABENDESSEN")
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("Rezept ansehen")
    }

    private fun eintrag() = runBlocking { db.planEintragDao().alle().single() }

    @Test
    fun portionenImBlatt() {
        blattOeffnen()
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.waitUntil(5_000) { eintrag().servings == 3 }
        compose.warteAufText("3 P.")
    }

    @Test
    fun umbenennenImBlatt() {
        blattOeffnen()
        compose.onNodeWithText("Umbenennen").performClick()
        compose.warteAufText("Mittagessen")
        compose.onNodeWithText("Mittagessen").performClick()
        compose.waitUntil(5_000) { eintrag().slotType == "MITTAGESSEN" }
    }

    @Test
    fun verschiebenAufMittwoch() {
        blattOeffnen(tag = 0)
        compose.onNodeWithText("Mi").performClick()
        compose.waitUntil(5_000) { eintrag().date == MITTWOCH }
    }

    @Test
    fun verschiebenNaechsteWoche() {
        blattOeffnen(tag = 6)
        compose.onNodeWithText("Nächste Woche").performClick()
        compose.waitUntil(5_000) { eintrag().date == MONTAG.plusDays(13) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reispfanne").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("KW 44 · 26.10.–1.11.").assertExists()
    }

    @Test
    fun entfernenMitRueckgaengig() {
        blattOeffnen()
        val id = eintrag().id
        compose.onNodeWithText("Entfernen").performClick()
        compose.warteAufText("Rückgängig")
        compose.waitUntil(5_000) { runBlocking { db.planEintragDao().alle().isEmpty() } }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { runBlocking { db.planEintragDao().nachId(id) != null } }
    }

    @Test
    fun rueckgaengigVerschwindetNachEinerWeile() {
        blattOeffnen()
        compose.onNodeWithText("Entfernen").performClick()
        compose.warteAufText("Rückgängig")
        compose.mainClock.advanceTimeBy(15_000)
        compose.waitForIdle()
        compose.onNodeWithText("Rückgängig").assertDoesNotExist()
    }

    @Test
    fun gekochtUmschalten() {
        blattOeffnen()
        compose.onNodeWithText("Gekocht").performClick()
        compose.waitUntil(5_000) { eintrag().cookedAt != null }
        compose.onNodeWithText("Gekocht").performClick()
        compose.waitUntil(5_000) { eintrag().cookedAt == null }
    }

    @Test
    fun kochenUndRezept() {
        blattOeffnen()
        compose.onNodeWithText("Kochen").performClick()
        assertEquals(listOf("r1" to 2), gekocht)
        compose.onNodeWithText("Reispfanne").performClick()
        compose.warteAufText("Rezept ansehen")
        compose.onNodeWithText("Rezept ansehen").performClick()
        assertEquals(listOf("r1"), rezepte)
    }

    @Test
    fun fuellenVorschauUndUebernehmen() {
        runBlocking { db.hauptspeisen() }
        zeigen()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onNodeWithText("Leere Tage füllen").performClick()
        compose.warteAufText("Übernehmen (5)")
        compose.onNodeWithContentDescription("MI 28.10. übernehmen").performClick()
        compose.warteAufText("Übernehmen (4)")
        compose.onNodeWithText("Übernehmen (4)").performClick()
        compose.waitUntil(5_000) { runBlocking { db.planEintragDao().alle().size == 4 } }
        assertTrue(runBlocking { db.planEintragDao().alle() }.none { it.date == MITTWOCH })
    }

    @Test
    fun fuellenKeinVorschlag() {
        runBlocking { db.rezeptDao().upsert(db.rezeptDao().nachId("r1")!!.copy(mahlzeiten = "ABENDESSEN", gaenge = "HAUPTSPEISE")) }
        zeigen()
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onNodeWithText("Leere Tage füllen").performClick()
        compose.warteAufText("Übernehmen (1)")
        assertEquals(4, compose.onAllNodesWithText("kein Vorschlag", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tabletZweiSpalten() {
        zeigen()
        compose.warteAufText("MO 26.10.")
        compose.onNodeWithText("MO 26.10.").assertIsDisplayed()
        compose.onNodeWithText("FR 30.10.").assertIsDisplayed()
        // Zwei Spalten: Freitag steht rechts neben Montag, nicht darunter
        val mo = compose.onNodeWithText("MO 26.10.").getUnclippedBoundsInRoot()
        val fr = compose.onNodeWithText("FR 30.10.").getUnclippedBoundsInRoot()
        assertTrue(fr.left > mo.right, "Freitag in der rechten Spalte")
    }

    @Test
    fun schrift200OhneAbschneiden() {
        planen(2, "ABENDESSEN")
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun ueberstehtDrehen() {
        val vm = u.vm()
        val tester = StateRestorationTester(compose)
        tester.setContent { FoodyTheme { WocheRoute({ _, _ -> }, {}, vm = vm) } }
        compose.warteAufText("KW 44 · 26.10.–1.11.")
        compose.onNodeWithContentDescription("Nächste Woche").performClick()
        compose.warteAufText("KW 45 · 2.11.–8.11.")
        tester.emulateSavedInstanceStateRestore()
        compose.warteAufText("KW 45 · 2.11.–8.11.")
    }
}
