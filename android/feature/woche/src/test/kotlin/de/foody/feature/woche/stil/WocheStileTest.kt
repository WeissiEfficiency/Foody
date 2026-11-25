package de.foody.feature.woche.stil

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenTag
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.woche.MITTWOCH
import de.foody.feature.woche.MONTAG
import de.foody.feature.woche.WocheRoute
import de.foody.feature.woche.WocheTestUmgebung
import de.foody.feature.woche.bestand
import de.foody.feature.woche.planen
import de.foody.feature.woche.pruefeNichtsAbgeschnitten
import de.foody.feature.woche.testDatenbank
import de.foody.feature.woche.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Die Wochen-Szenen der Stile R–V in Immersiv: Titel für TalkBack „Woche“, Tag wählen, Eintrag öffnet das
 * Mahlzeit-Blatt, „+ Mahlzeit“ die Mahlzeitwahl, „Leere Tage füllen“ das Blatt, Blättern zeigt „Heute“.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class WocheStileTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        db = testDatenbank()
        u = WocheTestUmgebung(db)
        runBlocking {
            db.bestand()
            db.planen("p1", MONTAG, "MITTAGESSEN")
            db.planen("p2", MITTWOCH, "ABENDESSEN")
        }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = variante) {
                val inhalt = @Composable { WocheRoute({ _, _ -> }, {}, vm = vm) }
                inhalt()
            }
        }
        warteAufBeschreibung("Mittwoch, 28.10.")
    }

    private fun warteAufBeschreibung(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun pruefen(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes((hasContentDescription("Woche") or hasText("Woche")) and isHeading()).onFirst().assertExists()
        // Heute (Mittwoch) ist gewählt; Dienstag wählen
        compose.onAllNodes(hasContentDescription("Mittwoch, 28.10., heute", substring = true) and isSelected()).onFirst().assertExists()
        compose.onAllNodes(hasContentDescription("Dienstag, 27.10.", substring = true)).onFirst().performClick()
        compose.onAllNodes(hasContentDescription("Dienstag, 27.10.", substring = true) and isSelected()).onFirst().assertExists()
        // Montag hat einen Eintrag: wählen und öffnen
        compose.onAllNodes(hasContentDescription("Montag, 26.10.", substring = true)).onFirst().performClick()
        compose.onNode(hasText("Reispfanne") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performScrollTo().performClick()
        warteAufBeschreibung("Ziehgriff")
    }

    private fun plus(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(hasContentDescription("Mahlzeit hinzufügen")).onFirst().performClick()
        // Wie im Entwurf öffnet „+“ die Planen-Seite mit „Selbst wählen“
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Selbst wählen") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Selbst wählen") and isSelectable()).assertIsSelected()
    }

    private fun fuellen(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(hasContentDescription("Leere Tage füllen", substring = true)).onFirst().performScrollTo().performClick()
        compose.warteAufText("Neu mischen")
    }

    private fun blaettern(stil: FoodyStil) {
        zeigen(stil)
        assertEquals(0, compose.onAllNodes(hasContentDescription("Heute")).fetchSemanticsNodes().size)
        compose.onAllNodes(hasContentDescription("Nächste Woche")).onFirst().performClick()
        warteAufBeschreibung("Montag, 2.11.")
        compose.onAllNodes(hasContentDescription("Heute")).onFirst().performClick()
        warteAufBeschreibung("Mittwoch, 28.10.")
    }

    /** Sage in Ruhig wie im Entwurf: die Szene schlicht, „WOCHE“ mit Tageschips statt der Karte. */
    @Test
    fun sageRuhigTageschips() {
        zeigen(FoodyStil.SAGE, variante = StilVariante.RUHIG)
        compose.onNodeWithText("WOCHE").assertExists()
        compose.onNodeWithText("KARTE DER WOCHE").assertDoesNotExist()
        val mittwoch = compose.onNode(hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable())
        mittwoch.performClick()
        mittwoch.assertIsSelected()
        compose.onNodeWithText("TAGE FÜLLEN").assertExists()
    }

    /** Anime in Ruhig wie im Entwurf: dieselben Panels, aber „Woche“ statt „WOCHE!“; Wählen geht weiter. */
    @Test
    fun animeRuhigSchlicht() {
        zeigen(FoodyStil.ANIME, variante = StilVariante.RUHIG)
        compose.onNodeWithText("WOCHE!").assertDoesNotExist()
        compose.onNodeWithText("Woche").assertExists()
        val mittwoch = compose.onNode(hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable())
        mittwoch.performClick()
        mittwoch.assertIsSelected()
    }

    @Test fun anime() = pruefen(FoodyStil.ANIME)

    /** Edo in Ruhig wie im Entwurf: sieben Tageschips statt der Votivtafeln, „Leere Tage vorschlagen lassen“ ohne 絵馬. */
    @Test
    fun edoRuhigTageschips() {
        zeigen(FoodyStil.EDO, variante = StilVariante.RUHIG)
        compose.onNodeWithText("Woche").assertExists()
        val mittwoch = compose.onNode(hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable())
        mittwoch.performClick()
        mittwoch.assertIsSelected()
        compose.onNodeWithText("Leere Tage vorschlagen lassen").performScrollTo().assertExists()
    }

    /** Bayrisch in Ruhig wie im Entwurf: ohne Wimpelkette, den Tag wählt man in der Liste (je Tag ein wählbarer Eintrag). */
    @Test
    fun bayrischRuhigOhneWimpel() {
        zeigen(FoodyStil.BAYRISCH, variante = StilVariante.RUHIG)
        val mittwoch = hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable()
        assertEquals(1, compose.onAllNodes(mittwoch).fetchSemanticsNodes().size)
        compose.onNode(mittwoch).performScrollTo().performClick()
        compose.onNode(mittwoch).assertIsSelected()
    }

    @Test fun edo() = pruefen(FoodyStil.EDO)

    @Test fun bayrisch() = pruefen(FoodyStil.BAYRISCH)

    /** Grauen in Ruhig wie im Entwurf: „Woche“, sieben Tageschips statt des Mondbogens, „Leere Tage vorschlagen lassen“. */
    @Test
    fun grauenRuhigTageschips() {
        zeigen(FoodyStil.GRAUEN, variante = StilVariante.RUHIG)
        compose.onNodeWithText("Woche").assertExists()
        compose.onNodeWithText("Mondphasen").assertDoesNotExist()
        val mittwoch = compose.onNode(hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable())
        mittwoch.performClick()
        mittwoch.assertIsSelected()
        compose.onNodeWithText("Leere Tage vorschlagen lassen").performScrollTo().assertExists()
    }

    @Test fun grauen() = pruefen(FoodyStil.GRAUEN)

    @Test fun grauenBlaettern() = blaettern(FoodyStil.GRAUEN)

    /**
     * Grauen in Immersiv (Runde 2 G3): kein Blättern und kein „planen ›“ oben; Wischen über den Mondbogen blättert die
     * Woche, das Ritual unten nennt den Stand.
     */
    @Test
    fun grauenMondbogenWischen() {
        zeigen(FoodyStil.GRAUEN)
        compose.onAllNodes(hasText("planen ›")).assertCountEquals(0)
        compose.onNodeWithText("2 von 7 Nächten erhellt", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasContentDescription("Leere Tage füllen, 2 von 7 Tagen geplant")).onFirst().assertExists()
        compose.onAllNodes(hasContentDescription("Montag, 26.10.", substring = true)).onFirst().performTouchInput { swipe(center, center - Offset(400f, 0f)) }
        warteAufBeschreibung("Montag, 2.11.")
        compose.onAllNodes(hasContentDescription("Montag, 2.11.", substring = true)).onFirst().performTouchInput { swipe(center, center + Offset(400f, 0f)) }
        warteAufBeschreibung("Mittwoch, 28.10.")
    }

    @Test fun kreuzer() = pruefen(FoodyStil.KREUZER)

    @Test
    fun kreuzerRuhigTageschips() {
        zeigen(FoodyStil.KREUZER, variante = StilVariante.RUHIG)
        compose.onNodeWithText("Woche").assertExists()
        compose.onNodeWithText("Flugplan").assertDoesNotExist()
        // Chips und Liste wählen beide den Tag; der Chip kommt zuerst
        val mittwoch = compose.onAllNodes(hasContentDescription("Mittwoch, 28.10.", substring = true) and isSelectable()).onFirst()
        mittwoch.performClick()
        mittwoch.assertIsSelected()
        compose.onNodeWithText("Vorschlagen lassen").performScrollTo().assertExists()
    }

    @Test fun animePlus() = plus(FoodyStil.ANIME)

    @Test fun edoPlus() = plus(FoodyStil.EDO)

    @Test fun bayrischPlus() = plus(FoodyStil.BAYRISCH)

    @Test fun grauenPlus() = plus(FoodyStil.GRAUEN)

    @Test fun kreuzerPlus() = plus(FoodyStil.KREUZER)

    @Test fun animeFuellen() = fuellen(FoodyStil.ANIME)

    @Test fun edoFuellen() = fuellen(FoodyStil.EDO)

    @Test fun bayrischFuellen() = fuellen(FoodyStil.BAYRISCH)

    @Test fun grauenFuellen() = fuellen(FoodyStil.GRAUEN)

    @Test fun kreuzerFuellen() = fuellen(FoodyStil.KREUZER)

    @Test fun animeBlaettern() = blaettern(FoodyStil.ANIME)

    @Test fun kreuzerBlaettern() = blaettern(FoodyStil.KREUZER)

    @Test
    fun texte() {
        val tag = { tage: Long, n: Int -> WochenTag(MONTAG.plusDays(tage), List(n) { eintrag(MONTAG.plusDays(tage)) }, null) }
        assertEquals("KW 44 · 26. Oktober – 1. November", kwText(MONTAG))
        assertEquals("2 von 7 Nächten erhellt", naechteStand(2, 7))
        assertEquals("0 von 1 Nacht erhellt", naechteStand(0, 1))
        assertEquals("KW 45 · 2.–8. November", kwText(MONTAG.plusDays(7)))
        assertEquals("SAMSTAG · HEID", bayrischTagTitel(tag(5, 1), heute = true))
        assertEquals("SUNDA, 1.", bayrischTagTitel(tag(6, 0), heute = false))
        assertEquals("IM ORBIT", sprungStatus(tag(2, 1), MITTWOCH))
        assertEquals("ANGEKOMMEN", sprungStatus(tag(0, 1), MITTWOCH))
        assertEquals("KEIN SPRUNG", sprungStatus(tag(1, 0), MITTWOCH))
        assertEquals("ETA 1 TAG", sprungStatus(tag(3, 1), MITTWOCH))
        assertEquals("ETA 3 TAGE", sprungStatus(tag(5, 1), MITTWOCH))
        assertEquals("OFFEN", sprungStatus(tag(6, 0), MITTWOCH))
        assertEquals(0f, mondAnteil(tag(0, 0)))
        assertEquals(0.25f, mondAnteil(tag(0, 1)))
        assertEquals("Neumond. Nichts ist geplant.", mondNotiz(tag(0, 0), heute = false))
    }

    private fun eintrag(datum: LocalDate) =
        PlanEintrag("e", datum, "ABENDESSEN", "r1", "Reispfanne", null, 300, 2, gekocht = false)
}
