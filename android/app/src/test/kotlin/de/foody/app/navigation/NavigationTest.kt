package de.foody.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.SofortStart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun tab(text: String) = compose.onNode(hasText(text) and isSelectable())

    private fun starten() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun handyZeigtLeiste() {
        starten()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertDoesNotExist()
        tab("Woche").performClick()
        tab("Woche").assertIsSelected()
        compose.onNodeWithText(Platzhalter.TEXT).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletZeigtRail() {
        starten()
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertExists()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zurueckVomTabFuehrtZuHeute() {
        starten()
        tab("Einkauf").performClick()
        tab("Einkauf").assertIsSelected()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        tab("Heute").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun heuteNachDetailUndTabwechselFuehrtNachHeute() {
        starten()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        tab("Heute").assertIsSelected()
        tab("Rezepte").performClick()
        tab("Heute").performClick()
        tab("Heute").assertIsSelected()
        compose.onNodeWithText("Rezept r1").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun heuteTabAufDemTagebuchFuehrtNachHeute() {
        starten()
        compose.runOnUiThread { nav.navigate(TagebuchTag("2026-10-24")) }
        compose.onNodeWithText("Tagebuch 2026-10-24").assertExists()
        tab("Heute").performClick()
        compose.onNodeWithText("Tagebuch 2026-10-24").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun aktiverTabErneutFuehrtZurWurzel() {
        starten()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r2")) }
        tab("Rezepte").assertIsSelected()
        tab("Rezepte").performClick()
        compose.onNodeWithText("Rezept r2").assertDoesNotExist()
        compose.onNodeWithText(Platzhalter.TEXT).assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun rezepteTabZeigtListe() {
        starten()
        tab("Rezepte").performClick()
        compose.onNodeWithText("Rezeptliste").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einkaufTabZeigtListe() {
        starten()
        tab("Einkauf").performClick()
        compose.onNodeWithText("Einkaufsliste").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufWechseltTab() {
        // „Ansehen“ nach „Auf die Einkaufsliste“ im Rezeptdetail
        starten()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        tab("Einkauf").assertIsSelected()
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        tab("Heute").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufAusFruehererListe() {
        // „Zur aktuellen Liste“ auf einer früheren (gelöschten) Liste: zurück an den Anfang des Tabs
        starten()
        tab("Einkauf").performClick()
        compose.runOnUiThread { nav.navigate(EinkaufListe("alt")) }
        compose.onNodeWithText("Liste alt").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.onNodeWithText("Liste alt").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufNichtAufGemerkteFruehereListe() {
        // „Ansehen“ nach Rezept → Liste: die aktuelle Liste, auch wenn im Einkauf-Tab noch eine frühere offen war
        starten()
        tab("Einkauf").performClick()
        compose.runOnUiThread { nav.navigate(EinkaufListe("alt")) }
        compose.onNodeWithText("Liste alt").assertExists()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        tab("Einkauf").assertIsSelected()
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.onNodeWithText("Liste alt").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einstellungenZumServer() {
        starten()
        compose.runOnUiThread { nav.navigate(Einstellungen) }
        compose.onNodeWithText("Einstellungen").assertExists()
        compose.runOnUiThread { nav.navigate(ServerVerbinden) }
        compose.onNodeWithText("Server verbinden").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Server verbinden").assertDoesNotExist()
        compose.onNodeWithText("Einstellungen").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einstellungenZumZutatenkatalog() {
        starten()
        compose.runOnUiThread { nav.navigate(Einstellungen) }
        compose.runOnUiThread { nav.navigate(Zutaten) }
        compose.onNodeWithText("Zutatenkatalog").assertExists()
        compose.runOnUiThread { nav.navigate(Zutat("z1")) }
        compose.onNodeWithText("Zutat z1").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Zutat z1").assertDoesNotExist()
        compose.onNodeWithText("Zutatenkatalog").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun wocheTabZeigtPlan() {
        starten()
        tab("Woche").performClick()
        compose.onNodeWithText("Wochenplan").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun kochenOhneLeiste() {
        starten()
        compose.runOnUiThread { nav.navigate(Kochen("r1", 2)) }
        compose.onNodeWithText("Rezept r1 · 2 Portionen").assertExists()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
        compose.runOnUiThread { nav.popBackStack() }
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun kochenOhneRail() {
        starten()
        compose.runOnUiThread { nav.navigate(Kochen("r1", 2)) }
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertDoesNotExist()
    }

    /** Heute öffnet Eintragen als eigenes Ziel; Zurück führt wieder nach Heute. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun eintragenAusHeuteUndZurueck() {
        starten()
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).performClick()
        compose.onNodeWithText("Eintragen ${Platzhalter.TAG} SNACK").assertExists()
        compose.runOnUiThread {
            val ziel = nav.currentBackStackEntry!!.toRoute<Eintragen>()
            assertEquals(Platzhalter.TAG.toString(), ziel.datum)
            assertEquals("SNACK", ziel.mahlzeit)
            assertNull(ziel.sofort)
            nav.popBackStack()
        }
        compose.runOnIdle { assertTrue(nav.currentBackStackEntry!!.destination.hasRoute<Heute>()) }
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertExists()
    }

    /** Eintragen ist wie der Kochmodus ein Vollbild ohne Navigationsleiste. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun eintragenOhneLeiste() {
        starten()
        compose.runOnUiThread { nav.navigate(EintragenStart(Platzhalter.TAG).alsZiel()) }
        compose.onNodeWithText("Eintragen ${Platzhalter.TAG} null").assertExists()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
        compose.runOnUiThread { nav.popBackStack() }
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
    }

    /**
     * Eintragen mit Sofort-Start (Widgets, „Scannen“ und „Foto“ in den Szenen) öffnet ohne Überblendung: „Heute“ ist
     * gleich weg, Kamera bzw. Scanner gehen über der leeren Seite auf. Geschlossen (Abbrechen wie Speichern) blendet die
     * Seite aus wie jede andere. Ohne Sofort-Start blendet Eintragen auch beim Öffnen wie gewohnt.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun sofortZielOhneUebergang() {
        starten()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val sofort = "Eintragen ${Platzhalter.TAG} null SCANNEN"
        compose.runOnUiThread { nav.navigate(EintragenStart(Platzhalter.TAG, null, SofortStart.SCANNEN).alsZiel()) }
        // Ein paar Bilder, weit vor dem Ende einer Überblendung (700 ms)
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText(sofort).assertExists()
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertDoesNotExist()
        // Schließen: Die Seite verblasst (ihr Text verliert Kontrast), statt voll stehen zu bleiben und dann zu verschwinden
        compose.runOnUiThread { nav.popBackStack() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val anfangs = compose.onNodeWithText(sofort).captureToImage().kontrast()
        repeat(25) { compose.mainClock.advanceTimeByFrame() }
        val spaeter = compose.onNodeWithText(sofort).captureToImage().kontrast()
        assertTrue(spaeter < anfangs / 2, "blendet aus: Kontrast $anfangs → $spaeter")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(sofort).assertDoesNotExist()
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertExists()
        // Zum Vergleich ohne Sofort-Start: nach denselben Bildern liegt „Heute“ noch unter der Einblendung
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigate(EintragenStart(Platzhalter.TAG).alsZiel()) }
        repeat(5) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertExists()
        compose.mainClock.autoAdvance = true
    }

    /** Meldet Eintragen zweimal fertig, schließt es nur sich selbst: „Heute“ bleibt, genau einmal auf dem Stapel. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun eintragenSchliesstNurEinmal() {
        starten()
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).performClick()
        compose.onNodeWithText("Eintragen ${Platzhalter.TAG} SNACK").assertExists()
        compose.onNodeWithText(Platzhalter.ZWEIMAL_FERTIG).performClick()
        compose.runOnIdle {
            assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<Heute>() == true, "jetzt: ${nav.currentBackStackEntry?.destination}")
            // Unter „Heute“ liegt kein weiteres Ziel (nur der Graph): „Heute“ ist genau einmal da
            assertNull(nav.previousBackStackEntry)
        }
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertExists()
    }

    /**
     * Auch wenn Eintragen nicht vorne (RESUMED) ist, schließt „fertig“ die Seite – etwa nach einem abgebrochenen
     * Sofort-Scan, solange die Scanner-Activity noch obenauf liegt, oder während der Einblendung. Hier: in der Einblendung
     * (der Eintrag ist erst STARTED), zweimal gemeldet, einmal zurück.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun eintragenSchliesstAuchNichtVorne() {
        starten()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigate(EintragenStart(Platzhalter.TAG, Mahlzeit.SNACK).alsZiel()) }
        // Ein paar Bilder der Einblendung (700 ms), nicht bis zum Ende
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread {
            val eintrag = nav.currentBackStackEntry!!
            assertTrue(eintrag.destination.hasRoute<Eintragen>())
            assertEquals(Lifecycle.State.STARTED, eintrag.lifecycle.currentState, "Voraussetzung: noch in der Einblendung")
        }
        // Semantik statt Tippen: Während der Überblendung liegen beide Seiten übereinander
        compose.onNodeWithText(Platzhalter.ZWEIMAL_FERTIG).performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnUiThread {
            assertTrue(nav.currentBackStackEntry?.destination?.hasRoute<Heute>() == true, "jetzt: ${nav.currentBackStackEntry?.destination}")
            assertNull(nav.previousBackStackEntry)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(Platzhalter.SNACK_EINTRAGEN).assertExists()
    }

    @Test
    fun eintragenZielRundreise() {
        val heute = LocalDate.of(2026, 11, 22)
        val start = EintragenStart(LocalDate.of(2026, 10, 24), Mahlzeit.MITTAGESSEN, SofortStart.FOTO)
        val ziel = start.alsZiel("a")
        assertEquals("a", ziel.auftrag)
        assertEquals(start, ziel.alsStart(heute))
        // Ungültiges aus einem alten Stapel: heute, nach Uhrzeit, ohne Sofort-Start
        assertEquals(EintragenStart(heute, null, null), Eintragen("kaputt", "QUARK", "BLITZ", "a").alsStart(heute))
        // Je Öffnen ein neuer Auftrag
        assertNotEquals(start.alsZiel().auftrag, start.alsZiel().auftrag)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun kochenZuRezeptAnpassen() {
        starten()
        compose.runOnUiThread {
            nav.navigate(RezeptDetail("r1"))
            nav.navigate(Kochen("r1", 2))
            nav.zuRezeptAnpassen("r1", 2)
        }
        compose.onNodeWithText("Rezept r1 bearbeiten · gekocht mit 2").assertExists()
        compose.runOnUiThread {
            assertTrue(runCatching { nav.getBackStackEntry<Kochen>() }.isFailure, "Kochen liegt nicht mehr im Stapel")
            nav.popBackStack()
        }
        compose.onNodeWithText("Rezept r1").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun rezeptNeuUndBearbeiten() {
        starten()
        compose.runOnUiThread { nav.navigate(RezeptNeu) }
        compose.onNodeWithText("Neues Rezept").assertExists()
        compose.runOnUiThread { nav.navigate(RezeptBearbeiten("r1")) }
        compose.onNodeWithText("Rezept r1 bearbeiten").assertExists()
    }

    /** Hellster minus dunkelster Bildpunkt: verblasst ein Text, sinkt er. */
    private fun ImageBitmap.kontrast(): Float {
        val punkte = toPixelMap()
        val helligkeiten = (0 until punkte.width).flatMap { x -> (0 until punkte.height).map { y -> punkte[x, y].luminance() } }
        return helligkeiten.max() - helligkeiten.min()
    }
}
