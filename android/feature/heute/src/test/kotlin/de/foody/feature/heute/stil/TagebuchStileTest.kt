package de.foody.feature.heute.stil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.domain.Summe
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.keinErkenner
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.tagebuch.EintragenViewModel
import de.foody.feature.heute.tagebuch.TagAbschnitt
import de.foody.feature.heute.tagebuch.TagZustand
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import de.foody.feature.heute.tagebuch.TagebuchTagViewModel
import de.foody.feature.heute.testBls
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
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
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals

/**
 * Tagebuch-Szenen der Stile R–V in Immersiv: Titel für TalkBack „Tagebuch“, Tag blättern, Eintrag öffnen und
 * löschen, „+“ und leere Mahlzeit öffnen das Eintragen-Blatt; bei 200 % Schrift wird nichts abgeschnitten.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TagebuchStileTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var tagebuch: TagebuchRepository
    private var aktuell = heute

    @Before
    fun setUp() {
        db = testDatenbank()
        tagebuch = TagebuchRepository(db) { 1L }
        runBlocking { tagebuch.frei(heute, Mahlzeit.SNACK, "Apfel", 500, null, null, null) }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, schrift200: Boolean = false) {
        val kontext = RuntimeEnvironment.getApplication()
        val vm = halter.behalten(TagebuchTagViewModel(tagebuch, TageszielSpeicher(kontext)))
        val eintragen = halter.behalten(
            EintragenViewModel(
                SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan(), keinErkenner, testErkennung(),
                testGedaechtnis(), testBls(), ZutatenRepository(db) { 1L },
            ),
        )
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                var datum by remember { mutableStateOf(heute) }
                val inhalt = @Composable { TagebuchTagRoute(datum, onDatum = { datum = it; aktuell = it }, vm = vm, eintragenVm = eintragen) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Apfel")
    }

    private fun warteAufBlatt() =
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Eintragen") and isHeading()).fetchSemanticsNodes().isNotEmpty() }

    private fun oeffnenUndLoeschen(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(hasContentDescription("Tagebuch") and isHeading()).onFirst().assertExists()
        compose.onNodeWithText("Apfel").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Apfel löschen").performScrollTo().performClick()
        compose.warteAufText("Eintrag gelöscht")
        compose.onNodeWithText("Apfel").assertDoesNotExist()
    }

    private fun blaetternUndEintragen(stil: FoodyStil) {
        zeigen(stil)
        compose.onNodeWithContentDescription("Vorheriger Tag").performClick()
        compose.waitUntil(5_000) { aktuell == heute.minusDays(1) }
        compose.onAllNodesWithContentDescription("Noch nichts eingetragen", substring = true).onFirst().performScrollTo().performClick()
        warteAufBlatt()
    }

    private fun plus(stil: FoodyStil) {
        zeigen(stil)
        compose.onAllNodes(hasContentDescription("Eintragen") and !isHeading()).onFirst().performClick()
        warteAufBlatt()
    }

    private fun gross(stil: FoodyStil) {
        zeigen(stil, schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test fun anime() = oeffnenUndLoeschen(FoodyStil.ANIME)

    @Test fun edo() = oeffnenUndLoeschen(FoodyStil.EDO)

    @Test fun bayrisch() = oeffnenUndLoeschen(FoodyStil.BAYRISCH)

    @Test fun grauen() = oeffnenUndLoeschen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = oeffnenUndLoeschen(FoodyStil.KREUZER)

    @Test fun animeBlaettern() = blaetternUndEintragen(FoodyStil.ANIME)

    @Test fun bayrischBlaettern() = blaetternUndEintragen(FoodyStil.BAYRISCH)

    @Test fun kreuzerBlaettern() = blaetternUndEintragen(FoodyStil.KREUZER)

    @Test fun edoPlus() = plus(FoodyStil.EDO)

    @Test fun grauenPlus() = plus(FoodyStil.GRAUEN)

    @Test fun animeSchrift200() = gross(FoodyStil.ANIME)

    @Test fun edoSchrift200() = gross(FoodyStil.EDO)

    @Test fun bayrischSchrift200() = gross(FoodyStil.BAYRISCH)

    @Test fun grauenSchrift200() = gross(FoodyStil.GRAUEN)

    @Test fun kreuzerSchrift200() = gross(FoodyStil.KREUZER)

    @Test
    fun texte() {
        assertEquals("heute", tagZusatzKurz(heute, heute))
        assertEquals("vor 3 Tagen", tagZusatzKurz(heute.minusDays(3), heute))
        assertEquals(null, tagZusatzKurz(heute.minusDays(9), heute))
        assertEquals("heid", bayrischZusatz(heute, heute))
        assertEquals("vorgestern", bayrischZusatz(heute.minusDays(2), heute))
        assertEquals("HEUTE · LOGBUCH 297", logbuchZusatz(heute, heute))
        assertEquals("LOGBUCH 296", logbuchZusatz(heute.minusDays(1), heute))
        assertEquals("Brotzeit", mahlzeitBayrisch(Mahlzeit.SNACK))
        assertEquals("Mittag", mahlzeitKurz(Mahlzeit.MITTAGESSEN))
        val tag = { kcal: Int, ziel: Int?, voll: Int ->
            TagZustand(
                heute, Summe(kcal, 0, 0, 0, true), ziel,
                listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN).mapIndexed { i, m ->
                    TagAbschnitt(m, if (i < voll) listOf(apfel()) else emptyList())
                },
            )
        }
        assertEquals("Passt scho · no 1.140 frei", bayrischSpruch(tag(860, 2000, 1)))
        assertEquals("Gnua für heid · 100 drüber", bayrischSpruch(tag(2100, 2000, 1)))
        assertEquals(null, bayrischSpruch(tag(860, null, 1)))
        assertEquals("Die Seite ist leer. Noch.", grauenNotiz(tag(0, 2000, 0), heute))
        assertEquals("Der Tag ist noch nicht vorüber. Etwas wartet auf den Abend.", grauenNotiz(tag(860, 2000, 2), heute))
        assertEquals("Drei Mahlzeiten. Keine davon wurde bereut.", grauenNotiz(tag(2100, 2000, 3), heute))
    }

    private fun apfel() = runBlocking { db.tagebuchDao().alle().first() }
}
