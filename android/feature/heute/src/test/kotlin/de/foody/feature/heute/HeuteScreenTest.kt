package de.foody.feature.heute

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.NichtGegessenSpeicher
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.sync.SyncLage
import de.foody.core.sync.SyncLageQuelle
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.SofortStart
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class HeuteScreenTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private val zeit = MutableStateFlow(LocalDateTime.of(heute, LocalTime.of(17, 30)))
    private val lage = MutableStateFlow(SyncLage(aktiv = false, laeuft = false, letzterFehler = null))
    private lateinit var db: FoodyDatenbank

    private var gekocht: Pair<String, Int>? = null
    private var einstellungen = 0

    /** Was „Eintragen“ geöffnet hätte (in der App: die Seite Eintragen). */
    private val eintragen = mutableListOf<EintragenStart>()

    /** Offene Anfrage eines Widgets; „Heute“ nimmt sie an und setzt sie zurück. */
    private var anfrage by mutableStateOf<Schnellstart?>(null)

    @Before
    fun setUp() {
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(schrift200: Boolean = false) {
        val vm = halter.behalten(
            HeuteViewModel(
                HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, SyncLageQuelle { lage },
                TageszielSpeicher(RuntimeEnvironment.getApplication()), Zeitgeber { zeit },
                NichtGegessenSpeicher(RuntimeEnvironment.getApplication()),
            ),
        )
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    HeuteRoute(
                        onKochen = { id, p -> gekocht = id to p }, onRezept = {}, onEinkauf = {}, onAlleEintraege = {},
                        onEinstellungen = { einstellungen++ }, onEintragen = { eintragen += it }, vm = vm,
                        schnellstart = anfrage, onSchnellstartAngenommen = { anfrage = null },
                    )
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        compose.warteAufText("Heute")
    }

    @Test
    fun geplantZeigtKarteUndKochen() {
        runBlocking { db.bestand(); db.planen("p1", heute, "ABENDESSEN") }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("2 Portionen · 717 kcal je Portion · 30 Min.").assertExists()
        compose.onNodeWithText("Kochen").performScrollTo().performClick()
        assertEquals("r1" to 2, gekocht)
    }

    @Test
    fun nichtsGeplantZeigtInspiration() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Ansehen")
        compose.onNodeWithText("Reispfanne").assertExists()
    }

    @Test
    fun geplantKeineInspiration() {
        // Frühstück ist um 17:30 vorbei: keine Karte, aber der Plan existiert – also auch keine Inspiration
        runBlocking { db.bestand(); db.planen("p1", heute, "FRUEHSTUECK") }
        zeigen()
        compose.warteAufText("FRÜHSTÜCK · GEPLANT")
        compose.onNodeWithText("Reispfanne").assertExists()
        compose.onNodeWithText("Ansehen").assertDoesNotExist()
        compose.onNodeWithText("Kochen").assertDoesNotExist()
    }

    @Test
    fun leererEinkaufOhneBereich() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("Einkauf").assertDoesNotExist()
    }

    @Test
    fun offenerEinkaufZeigtAnzahlUndKacheln() {
        runBlocking { db.bestand(); db.einkauf("Tomaten", "Paprika", "Zwiebeln") }
        zeigen()
        compose.warteAufText("3 offen")
        compose.onNodeWithText("Einkauf").assertExists()
        compose.onNodeWithText("Paprika").assertExists()
    }

    @Test
    fun leereDatenbankZeigtVerbinden() {
        zeigen()
        compose.warteAufText("Noch keine Rezepte")
        compose.onNodeWithText("Server verbinden").performScrollTo().performClick()
        assertEquals(1, einstellungen)
    }

    /** „Gegessen?“ fragt nach; der Knopf allein trägt noch nichts ein. */
    private fun gegessenFragen() {
        compose.warteAufText("Gegessen?")
        compose.onNodeWithText("Gegessen?").performScrollTo().performClick()
        compose.warteAufText("„Reispfanne“ als Mittagessen eintragen? Bei „Nein“ verschwindet der Vorschlag für heute.")
        assertEquals(0, runBlocking { db.tagebuchDao().alle().size })
    }

    @Test
    fun vorschlagGegessenJaTraegtEin() {
        runBlocking { db.bestand(); db.planen("p1", heute, "MITTAGESSEN") }
        zeigen()
        gegessenFragen()
        compose.onNodeWithText("Ja").performClick()
        compose.waitUntil(5_000) { runBlocking { db.tagebuchDao().alle().size == 1 } }
        compose.warteAufText("Noch kein Frühstück")
        compose.onAllNodesWithText("Gegessen?").assertCountEquals(0)
    }

    @Test
    fun vorschlagGegessenNeinBlendetAus() {
        runBlocking { db.bestand(); db.planen("p1", heute, "MITTAGESSEN") }
        zeigen()
        gegessenFragen()
        compose.onNodeWithText("Nein").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Gegessen?").fetchSemanticsNodes().isEmpty() }
        // Nichts eingetragen, die Mahlzeit steht wieder leer da
        compose.warteAufText("Noch kein Mittagessen")
        assertEquals(0, runBlocking { db.tagebuchDao().alle().size })
    }

    @Test
    fun vorschlagGegessenDanebenAendertNichts() {
        runBlocking { db.bestand(); db.planen("p1", heute, "MITTAGESSEN") }
        zeigen()
        gegessenFragen()
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Gegessen?").fetchSemanticsNodes().size == 1 }
        assertEquals(0, runBlocking { db.tagebuchDao().alle().size })
    }

    /** „Eintragen“ in der Zeile „Noch kein Snack“ öffnet Eintragen für den Tag mit dieser Mahlzeit. */
    @Test
    fun eintragenAusZeile() {
        runBlocking { db.bestand() }
        zeigen()
        compose.warteAufText("Noch kein Snack")
        compose.onAllNodesWithText("Eintragen")[2].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(EintragenStart(heute, Mahlzeit.SNACK)), eintragen) }
    }

    /** Widgets: „Scannen“ und „KI-Foto“ öffnen Eintragen gleich mit Scanner bzw. Kamera, „Schnell eintragen“ mit Erfassen. */
    @Test
    fun schnellstartOeffnetEintragen() {
        zeigen()
        for ((art, sofort) in listOf(Schnellstart.Art.SCANNEN to SofortStart.SCANNEN, Schnellstart.Art.FOTO to SofortStart.FOTO, Schnellstart.Art.EINTRAGEN to null)) {
            compose.runOnIdle { anfrage = Schnellstart(art, "widget-$art") }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull(anfrage, "angenommen")
                val start = eintragen.last()
                assertEquals(sofort, start.sofort, "$art")
                assertNull(start.mahlzeit, "nach Uhrzeit")
            }
        }
        compose.runOnIdle { assertEquals(3, eintragen.size) }
    }

    @Test
    fun syncFehlerImKopf() {
        lage.value = SyncLage(aktiv = true, laeuft = false, letzterFehler = "anmeldung_abgelaufen")
        zeigen()
        compose.warteAufText("Anmeldung abgelaufen")
    }

    @Test
    fun mitternachtWechseltDatum() {
        zeit.value = LocalDateTime.of(heute, LocalTime.of(23, 59))
        zeigen()
        compose.warteAufText("SAMSTAG, 24. OKTOBER")
        zeit.value = LocalDateTime.of(heute.plusDays(1), LocalTime.MIDNIGHT)
        compose.warteAufText("SONNTAG, 25. OKTOBER")
    }

    @Test
    fun schrift200OhneAbschneiden() {
        runBlocking { db.bestand(); db.planen("p1", heute, "ABENDESSEN"); db.einkauf("Tomaten", "Paprika") }
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    /** Mit Sync-Status („Wartet auf Netz“) bleibt der Titel bei 200 % einzeilig: der Status rückt unter den Titel (V2, vorher „He-ute“). */
    @Test
    fun schrift200TitelMitSyncStatusEinzeilig() {
        lage.value = SyncLage(aktiv = true, laeuft = false, letzterFehler = "offline")
        runBlocking { db.bestand() }
        zeigen(schrift200 = true)
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Heute").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(1, layout.single().lineCount)
    }
}
