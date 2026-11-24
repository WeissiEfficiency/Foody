package de.foody.feature.rezepte.liste.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Einordnung
import de.foody.feature.rezepte.bestand
import de.foody.feature.rezepte.liste.RezeptListeRoute
import de.foody.feature.rezepte.liste.RezeptListeViewModel
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
 * Die Rezeptlisten-Szenen der Stile R–V in Immersiv: Titel für TalkBack, Rezept wählen und öffnen (Anime und die
 * Bibliothek des Grauens öffnen direkt), Seiten blättern (Anime und Grauen: endlos), „+ Neu“ öffnet ein neues Rezept, leere Liste bietet „Anlegen“.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class RezepteStileTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val geoeffnet = mutableListOf<String>()
    private var neu = 0

    @Before
    fun setUp() {
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    /** Reispfanne (r1) und [weitere] Rezepte r2 … mit den Namen aus [NAMEN]. */
    private fun rezepte(weitere: Int) = runBlocking {
        db.bestand()
        NAMEN.take(weitere).forEachIndexed { i, name ->
            db.rezeptDao().upsert(
                RezeptEntity(
                    id = "r${i + 2}", name = name, defaultServings = 2, prepMinutes = 10, cookMinutes = 15, imageUri = null,
                    notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
                ),
            )
        }
    }

    private fun zeigen(stil: FoodyStil) {
        val vm = halter.behalten(RezeptListeViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1L }))
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                val inhalt = @Composable {
                    RezeptListeRoute(onOeffnen = { geoeffnet += it }, onNeu = { neu++ }, vm = vm)
                }
                inhalt()
            }
        }
    }

    private fun warteAufBeschreibung(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun pruefen(stil: FoodyStil) {
        rezepte(2)
        zeigen(stil)
        warteAufBeschreibung("Shakshuka")
        compose.onAllNodes(isHeading()).onFirst().assertExists()
        if (stil != FoodyStil.ANIME && stil != FoodyStil.GRAUEN) {
            // Wählen: der Tab ist gewählt, die Karte zeigt das Rezept
            compose.onNode(hasContentDescription("Shakshuka", substring = true) and tab).performClick()
            compose.onNode(hasContentDescription("Shakshuka", substring = true) and tab and isSelected()).assertExists()
        }
        compose.onAllNodes(hasContentDescription("Öffnen: Shakshuka", substring = true)).onFirst().performScrollTo().performClick()
        assertEquals(listOf("r3"), geoeffnet)
    }

    private fun blaettern(stil: FoodyStil) {
        rezepte(7)
        zeigen(stil)
        warteAufBeschreibung("Seite 1 von 2")
        compose.onNodeWithContentDescription("Nächste Seite").performScrollTo().performClick()
        warteAufBeschreibung("Seite 2 von 2")
        compose.onNodeWithContentDescription("Vorige Seite").performScrollTo().performClick()
        warteAufBeschreibung("Seite 1 von 2")
    }

    private fun menue(stil: FoodyStil) {
        rezepte(0)
        zeigen(stil)
        warteAufBeschreibung("Reispfanne")
        compose.onNodeWithContentDescription("Neues Rezept").performClick()
        assertEquals(1, neu)
    }

    private fun leer(stil: FoodyStil) {
        zeigen(stil)
        compose.warteAufText("Noch keine Rezepte")
        compose.onNodeWithText("Anlegen").performClick()
        assertEquals(1, neu)
    }

    /**
     * Kleiner Bildschirm: Nach dem Antippen eines Rezepts ist „Öffnen“ ohne Scrollen zu sehen. Auf dem Handy lag die
     * Karte vorher unter der schwebenden Leiste (Gerätetest 2026-11-16).
     */
    private fun karteInsBild(stil: FoodyStil) {
        rezepte(7)
        zeigen(stil)
        warteAufBeschreibung("Shakshuka")
        compose.onNode(hasContentDescription("Shakshuka", substring = true) and tab).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Öffnen: Shakshuka", substring = true)).onFirst().assertIsDisplayed()
    }

    @Config(qualifiers = "w360dp-h640dp")
    @Test fun edoKarteInsBild() = karteInsBild(FoodyStil.EDO)

    @Config(qualifiers = "w360dp-h640dp")
    @Test fun bayrischKarteInsBild() = karteInsBild(FoodyStil.BAYRISCH)

    @Config(qualifiers = "w360dp-h640dp")
    @Test fun kreuzerKarteInsBild() = karteInsBild(FoodyStil.KREUZER)

    /**
     * Die ersten Filter wie in den Entwürfen mit den Wörtern des Stils; TalkBack hört „Alle“, „Favoriten“, „Höchstens
     * 30 Minuten“. Der schnelle Filter lässt nur Rezepte bis 30 Minuten stehen.
     */
    private fun filterWoerter(stil: FoodyStil, alle: String, favoriten: String, schnell: String) {
        rezepte(2)
        runBlocking {
            db.rezeptDao().upsert(
                RezeptEntity(
                    id = "r9", name = "Schmorbraten", defaultServings = 4, prepMinutes = 30, cookMinutes = 120, imageUri = null,
                    notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
                ),
            )
        }
        zeigen(stil)
        // Die Sage zeigt ein Rezept nach dem anderen („1 / 4“), die Szenen R–V alle in der Wahl
        val sage = stil == FoodyStil.SAGE
        if (sage) compose.warteAufText("1 / 4") else warteAufBeschreibung("Schmorbraten")
        compose.onNode(hasText(alle) and hasContentDescription("Alle") and isSelected()).assertExists()
        compose.onNode(hasText(favoriten) and hasContentDescription("Favoriten")).assertExists()
        compose.onNode(hasText(schnell) and hasContentDescription("Höchstens 30 Minuten")).performClick()
        compose.onNode(hasContentDescription("Höchstens 30 Minuten") and isSelected()).assertExists()
        if (sage) {
            compose.warteAufText("1 / 3")
        } else {
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Schmorbraten", substring = true).fetchSemanticsNodes().isEmpty() }
            warteAufBeschreibung("Shakshuka")
        }
    }

    @Test fun sageFilter() = filterWoerter(FoodyStil.SAGE, "Alle", "♦", "≤ 30′")

    @Test fun animeFilter() = filterWoerter(FoodyStil.ANIME, "Alle", "Favoriten", "Schnell")

    @Test fun edoFilter() = filterWoerter(FoodyStil.EDO, "Alle", "Favoriten", "≤ 30 Min.")

    @Test fun bayrischFilter() = filterWoerter(FoodyStil.BAYRISCH, "Olle", "♥ Leibgerichte", "G’schwind")

    @Test fun grauenFilter() = filterWoerter(FoodyStil.GRAUEN, "Alle", "♦ Favoriten", "≤ 30 Min.")

    @Test fun kreuzerFilter() = filterWoerter(FoodyStil.KREUZER, "ALLE", "★ FAVORITEN", "≤ 30 MIN.")

    @Test fun anime() = pruefen(FoodyStil.ANIME)

    @Test fun edo() = pruefen(FoodyStil.EDO)

    @Test fun bayrisch() = pruefen(FoodyStil.BAYRISCH)

    @Test fun grauen() = pruefen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = pruefen(FoodyStil.KREUZER)

    /** Anime und Grauen zeigen alle Treffer in einer Liste ohne Seiten; das letzte Rezept erreicht man durch Scrollen. */
    private fun endlos(stil: FoodyStil) {
        rezepte(7)
        zeigen(stil)
        warteAufBeschreibung("Shakshuka")
        compose.onAllNodesWithContentDescription("Seite 1 von", substring = true).assertCountEquals(0)
        val liste = compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        liste.performScrollToNode(hasContentDescription("Öffnen: Tomatensuppe", substring = true))
        compose.onNodeWithContentDescription("Öffnen: Tomatensuppe", substring = true).performClick()
        assertEquals(listOf("r8"), geoeffnet)
    }

    @Test fun animeEndlos() = endlos(FoodyStil.ANIME)

    @Test fun grauenEndlos() = endlos(FoodyStil.GRAUEN)

    @Test fun edoBlaettern() = blaettern(FoodyStil.EDO)

    @Test fun bayrischBlaettern() = blaettern(FoodyStil.BAYRISCH)

    @Test fun kreuzerBlaettern() = blaettern(FoodyStil.KREUZER)

    @Test fun animeMenue() = menue(FoodyStil.ANIME)

    @Test fun edoMenue() = menue(FoodyStil.EDO)

    @Test fun bayrischMenue() = menue(FoodyStil.BAYRISCH)

    @Test fun grauenMenue() = menue(FoodyStil.GRAUEN)

    @Test fun kreuzerMenue() = menue(FoodyStil.KREUZER)

    @Test fun animeLeer() = leer(FoodyStil.ANIME)

    @Test fun kreuzerLeer() = leer(FoodyStil.KREUZER)

    @Test
    fun texte() {
        val r = karte()
        assertEquals("Reispfanne, 30 Min., 717 kcal, Favorit", rezeptBeschreibung(r))
        assertEquals("Reispfanne", rezeptBeschreibung(karte(zeit = null, kcal = null, favorit = false)))
        assertEquals("30 Min. · 717 kcal", zeitUndKcal(r))
        assertEquals(null, zeitUndKcal(karte(zeit = null, kcal = null)))
        assertEquals("R", anfang(r))
        assertEquals("FAV", animeMarke(r))
        assertEquals("FIX", animeMarke(karte(favorit = false)))
        assertEquals("NEU", animeMarke(karte(favorit = false, zeit = 45)))
        assertEquals("★★☆", animeSterne(2))
        assertEquals("★★★", animeSterne(9))
        assertEquals("☆☆☆", animeSterne(0))
        assertEquals(17, sternGroesse(717))
        assertEquals(22, sternGroesse(3000))
        assertEquals("SPINAT", kreuzerKurz(karte(name = "Spinat-Lasagne")))
        assertEquals("BAND · OFENGERICHT", grauenBand(karte(tags = listOf("Ofengericht"))))
        assertEquals("BAND · OHNE ORDNUNG", grauenBand(r))
        assertEquals("AUS DER KÜCHE", edoArt(r))
        assertEquals("AUS DA KUCHL", bayrischArt(r))
    }

    private fun karte(
        name: String = "Reispfanne",
        zeit: Int? = 30,
        kcal: Int? = 717,
        favorit: Boolean = true,
        tags: List<String> = emptyList(),
    ) = RezeptKarte(
        id = "r", name = name, bild = null, kcalJePortion = kcal, eiweissJePortion = null, zeitMinuten = zeit,
        favorit = favorit, gekocht = 0, diaeten = emptySet(), einordnung = Einordnung(emptySet(), emptySet(), true, true),
        tags = tags, zutatNamen = emptyList(), archiviert = false, angelegt = 0,
    )

    private companion object {
        val NAMEN = listOf("Kürbissuppe mit Ingwer", "Shakshuka", "Linsencurry mit Kokosmilch", "Spinat-Lasagne", "Ofengemüse mit Feta", "Bohnenchili", "Tomatensuppe")
    }
}
