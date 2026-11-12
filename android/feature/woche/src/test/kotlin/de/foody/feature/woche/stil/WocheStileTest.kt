package de.foody.feature.woche.stil

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import de.foody.feature.woche.MitSchrift200
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
 * Mahlzeit-Blatt, „+ Mahlzeit“ die Mahlzeitwahl, „Leere Tage füllen“ das Blatt, Blättern zeigt „Heute“; bei 200 %
 * Schrift wird nichts abgeschnitten.
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

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false) {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable { WocheRoute({ _, _ -> }, {}, vm = vm) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
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
        compose.warteAufText("Mahlzeit wählen")
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

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        val tag = { tage: Long, n: Int -> WochenTag(MONTAG.plusDays(tage), List(n) { eintrag(MONTAG.plusDays(tage)) }, null) }
        assertEquals("KW 44 · 26. Oktober – 1. November", kwText(MONTAG))
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
