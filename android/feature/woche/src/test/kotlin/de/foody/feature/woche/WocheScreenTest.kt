package de.foody.feature.woche

import android.content.Context
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
