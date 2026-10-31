package de.foody.feature.einstellungen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Darstellung
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.SyncProblemMitName
import de.foody.core.designsystem.FoodyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EinstellungenScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: EinstellungenTestUmgebung
    private var server = 0
    private var zutatenGeoeffnet = 0
    private lateinit var zip: File
    private lateinit var json: File

    @Before
    fun setUp() {
        u = EinstellungenTestUmgebung()
        zip = File(u.kontext.cacheDir, "test.zip").also { it.delete() }
        json = File(u.kontext.cacheDir, "v1.json").also { it.writeText(V1_SICHERUNG) }
        runBlocking {
            u.db.rezeptDao().upsert(
                RezeptEntity(
                    id = "r1", name = "Reispfanne", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                    notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
                ),
            )
        }
    }

    @After
    fun tearDown() = u.schliessen()

    /** Dateidialoge durch feste Dateien ersetzt: „erstellen“ schreibt nach [zip], „öffnen“ wählt [json]. */
    private val dateien: @Composable ((Uri) -> Unit, (Uri) -> Unit) -> Dateiwahl = { erstellt, gewaehlt ->
        remember { Dateiwahl(erstellen = { erstellt(Uri.fromFile(zip)) }, oeffnen = { gewaehlt(Uri.fromFile(json)) }) }
    }

    private fun zeigen(schrift200: Boolean = false): EinstellungenViewModel {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme {
                val inhalt = @Composable { EinstellungenRoute(onServer = { server++ }, onZutaten = { zutatenGeoeffnet++ }, onZurueck = {}, vm = vm, dateiwahl = dateien) }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
        return vm
    }

    private fun knopf(text: String) = compose.onNodeWithText(text).performScrollTo()

    @Test
    fun zutatenZeile() {
        runBlocking {
            for (n in listOf("Reis", "Salz", "Zucker")) {
                u.db.zutatDao().upsert(de.foody.core.data.db.ZutatEntity(id = n, canonicalName = n, createdAt = 1, updatedAt = 1))
            }
        }
        zeigen()
        compose.onNodeWithText("ZUTATEN").assertExists()
        compose.warteAufText("3")
        compose.onNodeWithText("Zutatenkatalog").performScrollTo().performClick()
        assertEquals(1, zutatenGeoeffnet)
    }

    @Test
    fun onlineSchalter() {
        zeigen()
        compose.warteAufText("Online-Produktsuche")
        compose.onNodeWithText("Open Food Facts; gesendet wird nur die Produktnummer", useUnmergedTree = true).assertExists()
        val schalter = compose.onNodeWithText("Online-Produktsuche").performScrollTo()
        schalter.assertIsOn()
        schalter.performClick()
        compose.waitUntil(5_000) { !u.scan.onlineSuche.value }
        schalter.assertIsOff()
        schalter.performClick()
        compose.waitUntil(5_000) { u.scan.onlineSuche.value }
        schalter.assertIsOn()
    }

    @Test
    fun abschnitteSichtbar() {
        zeigen()
        compose.warteAufText("KONTO")
        for (a in listOf("DARSTELLUNG", "TAGESZIEL", "SICHERUNG", "DATEN", "HINWEISE")) compose.onNodeWithText(a).performScrollTo()
    }

    @Test
    fun kontoNichtVerbunden() {
        zeigen()
        compose.warteAufText("Nicht verbunden")
        compose.onNodeWithText("Server verbinden").performClick()
        assertEquals(1, server)
    }

    @Test
    fun kontoVerbunden() {
        u.verbinden()
        zeigen()
        compose.warteAufText("Verbunden mit http://10.0.2.2:8081")
    }

    @Test
    fun kontoZeigtNichtUebertragene() {
        u.konto.stand.value = VerbindungsZustand(
            verbunden = true, adresse = "http://10.0.2.2:8081", letzterSync = null, letzterFehler = null,
            nichtUebertragen = (1..7).map { SyncProblemMitName("recipe", "r$it", "photo_unsyncable", it.toLong(), "Rezept $it") },
        )
        zeigen()
        compose.warteAufText("7 Einträge nicht vollständig übertragen")
        compose.onNodeWithText("Rezept „Rezept 1“: ohne Foto übertragen (zu groß oder kein JPEG)").assertExists()
        compose.onNodeWithText("… und 2 weitere").assertExists()
    }

    @Test
    fun chipsSetzenDarstellung() {
        zeigen()
        compose.warteAufText("Dunkel")
        compose.onNodeWithText("Dunkel").performClick()
        compose.waitUntil(5_000) { runBlocking { u.darstellung.darstellung().first() } == Darstellung.DUNKEL }
    }

    @Test
    fun tageszielZeigtGespeichertenWert() {
        u.ziel.setzen(2000)
        zeigen()
        compose.warteAufText("TAGESZIEL")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().any {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text == "2000"
            }
        }
    }

    @Test
    fun tageszielFehlerAmFeld() {
        zeigen()
        compose.warteAufText("TAGESZIEL")
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("99")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.warteAufText("500 bis 10.000 kcal")
        assertNull(runBlocking { u.ziel.tagesziel().first() })
        compose.onNode(hasSetTextAction()).performTextReplacement("2000")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { runBlocking { u.ziel.tagesziel().first() } == 2000 }
    }

    /** Beim Tippen wird nichts gespeichert, erst mit „Fertig“ (E3: keine Zwischenstände wie „500“ auf dem Weg zu „5000“). */
    @Test
    fun tageszielErstMitFertigGespeichert() {
        zeigen()
        compose.warteAufText("TAGESZIEL")
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("500")
        compose.waitForIdle()
        assertNull(runBlocking { u.ziel.tagesziel().first() })
        compose.onNode(hasSetTextAction()).performTextInput("0")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { runBlocking { u.ziel.tagesziel().first() } == 5000 }
    }

    @Test
    fun tageszielFehlerErstNachFertig() {
        // Beim Tippen von „2000“ blinkt nach „2“, „20“, „200“ kein Fehler – erst „Fertig“ zeigt ihn
        zeigen()
        compose.warteAufText("TAGESZIEL")
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("2")
        compose.waitForIdle()
        compose.onNodeWithText("500 bis 10.000 kcal").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.warteAufText("500 bis 10.000 kcal")
    }

    @Test
    fun erstellenMeldet() {
        zeigen()
        compose.warteAufText("Sicherung erstellen")
        knopf("Sicherung erstellen").performClick()
        compose.warteAufText("Sicherung gespeichert")
    }

    @Test
    fun wiederherstellenMitRueckfrage() {
        zeigen()
        compose.warteAufText("Wiederherstellen")
        knopf("Wiederherstellen").performClick()
        compose.warteAufText("Alle Daten ersetzen?")
        compose.onNodeWithText("Ersetzen").performClick()
        compose.warteAufText("1 Rezept, 1 Zutat, 0 Fotos wiederhergestellt · Vorrat übersprungen: 2")
    }

    @Test
    fun loeschenMitRueckfrage() {
        zeigen()
        compose.warteAufText("Alle Daten löschen")
        knopf("Alle Daten löschen").performClick()
        compose.warteAufText(
            "Alle Rezepte, Pläne, Einkaufslisten, Tagebuch und Fotos auf diesem Gerät löschen? Der Startkatalog wird neu eingespielt.",
        )
        compose.onNodeWithText("Löschen").performClick()
        compose.warteAufText("Alle Daten gelöscht")
        assertNull(runBlocking { u.db.rezeptDao().nachId("r1") })
    }

    @Test
    fun gesperrtBeiSync() {
        u.verbinden()
        zeigen()
        compose.warteAufText("Verbunden mit http://10.0.2.2:8081")
        knopf("Wiederherstellen").assertIsNotEnabled()
        knopf("Alle Daten löschen").assertIsNotEnabled()
        // Unter beiden gesperrten Knöpfen
        assertEquals(2, compose.onAllNodesWithText(ERST_TRENNEN).fetchSemanticsNodes().size)
        knopf("Sicherung erstellen").assertIsEnabled()
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.warteAufText("KONTO")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun ueberstehtDrehen() {
        val vm = u.vm()
        val tester = StateRestorationTester(compose)
        tester.setContent { FoodyTheme { EinstellungenRoute({}, {}, {}, vm = vm, dateiwahl = dateien) } }
        compose.warteAufText("TAGESZIEL")
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("99")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.warteAufText("500 bis 10.000 kcal")
        tester.emulateSavedInstanceStateRestore()
        compose.warteAufText("500 bis 10.000 kcal")
    }
}
