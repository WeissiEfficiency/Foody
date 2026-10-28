package de.foody.feature.heute

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.sync.SyncLage
import de.foody.core.sync.SyncLageQuelle
import de.foody.core.testing.ViewModelHalter
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
import kotlin.test.assertEquals

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
                HeuteRepository(db), TagebuchRepository(db) { 1L }, SyncLageQuelle { lage },
                TageszielSpeicher(RuntimeEnvironment.getApplication()), Zeitgeber { zeit },
            ),
        )
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    HeuteRoute(
                        onKochen = { id, p -> gekocht = id to p }, onRezept = {}, onEinkauf = {}, onAlleEintraege = {},
                        onEinstellungen = { einstellungen++ }, vm = vm,
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
        compose.warteAufText("Reispfanne · geplant")
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

    @Test
    fun vorschlagGegessenTraegtEin() {
        runBlocking { db.bestand(); db.planen("p1", heute, "MITTAGESSEN") }
        zeigen()
        compose.warteAufText("Gegessen?")
        compose.onNodeWithText("Gegessen?").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { db.tagebuchDao().alle().size == 1 } }
        compose.warteAufText("Noch kein Frühstück")
        compose.onNodeWithText("Gegessen?").assertDoesNotExist()
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
}
