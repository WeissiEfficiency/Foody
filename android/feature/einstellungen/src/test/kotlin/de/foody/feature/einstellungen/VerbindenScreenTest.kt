package de.foody.feature.einstellungen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class VerbindenScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val konto = ErsatzKonto()
    private var zurueck = 0

    private fun zeigen() {
        val vm = VerbindenViewModel(konto)
        compose.setContent { FoodyTheme { VerbindenRoute(onZurueck = { zurueck++ }, vm = vm) } }
    }

    private fun ausfuellen(passwort: String = "geheim-123") {
        compose.onNodeWithText("Server-Adresse").performTextInput("https://foody.example")
        compose.onNodeWithText("Benutzername").performTextInput("Weissi")
        compose.onNodeWithText("Passwort").performTextInput(passwort)
        compose.onNodeWithText("Anmelden").performClick()
        compose.waitForIdle()
    }

    @Test
    fun anmeldenOhneLokaleDatenAktiviertDirekt() {
        zeigen()
        ausfuellen()
        assertEquals(listOf("anmelden:https://foody.example:Weissi", "aktivieren:ZUSAMMENFUEHREN"), konto.aufrufe)
        compose.onNodeWithText("https://foody.example", substring = true).assertExists()
    }

    @Test
    fun mitLokalenDatenFragtZusammenfuehrenOderErsetzen() {
        konto.lokaleDaten = true
        zeigen()
        ausfuellen()
        compose.onNodeWithText("Zusammenführen").assertExists()
        compose.onNodeWithText("Zutaten bleiben", substring = true).assertExists()
        assertEquals(1, konto.aufrufe.size, "noch nicht aktiviert")
        compose.onNodeWithText("Ersetzen").performClick()
        compose.waitForIdle()
        assertEquals("aktivieren:ERSETZEN", konto.aufrufe.last())
    }

    @Test
    fun falschesPasswortZeigtText() {
        konto.anmeldeErgebnis = Ergebnis.Fehler("falsche_zugangsdaten")
        zeigen()
        ausfuellen()
        compose.onNodeWithText("Benutzername oder Passwort falsch.").assertExists()
        assertFalse(konto.aufrufe.any { it.startsWith("aktivieren") })
    }

    @Test
    fun offlineZeigtTextFelderBleiben() {
        konto.anmeldeErgebnis = Ergebnis.Fehler("offline")
        zeigen()
        ausfuellen()
        compose.onNodeWithText("Keine Verbindung zum Server.").assertExists()
        compose.onNodeWithText("https://foody.example").assertExists()
        compose.onNodeWithText("Weissi").assertExists()
        compose.onNodeWithText(Verbinden.LAEUFT, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Anmelden").assertExists()
    }

    @Test
    fun verbundenZeigtStatusUndJetzt() {
        konto.stand.value = VerbindungsZustand(true, "https://foody.example", System.currentTimeMillis() - 5 * 60_000, "offline")
        zeigen()
        compose.onNodeWithText("vor 5 Minuten", substring = true).assertExists()
        compose.onNodeWithText("Keine Verbindung zum Server.").assertExists()
        compose.onNodeWithText("Jetzt synchronisieren").performClick()
        assertEquals(listOf("jetzt"), konto.aufrufe)
    }

    @Test
    fun trennenFragtNach() {
        konto.stand.value = VerbindungsZustand(true, "https://foody.example", null, null)
        zeigen()
        compose.onNodeWithText("Trennen").performClick()
        compose.onNodeWithText("Sync trennen?").assertExists()
        assertTrue(konto.aufrufe.isEmpty(), "erst nach Bestätigung")
        compose.onNodeWithText(Verbinden.TRENNEN_BESTAETIGEN).performClick()
        compose.waitForIdle()
        assertEquals(listOf("trennen"), konto.aufrufe)
        compose.onNodeWithText("Anmelden").assertExists()
    }

    @Test
    fun passwortNieImText() {
        konto.anmeldeErgebnis = Ergebnis.Fehler("falsche_zugangsdaten")
        zeigen()
        ausfuellen(passwort = "Sehr-Geheim-42")
        val texte = compose.onNode(isRoot()).fetchSemanticsNode().let { wurzel ->
            buildList {
                fun sammeln(n: androidx.compose.ui.semantics.SemanticsNode) {
                    // Das Passwortfeld selbst darf seinen Inhalt halten
                    if (n.config.getOrNull(SemanticsProperties.Password) == null) {
                        n.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                        n.config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
                        n.config.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
                    }
                    n.children.forEach(::sammeln)
                }
                sammeln(wurzel)
            }
        }
        assertTrue(texte.isNotEmpty())
        assertFalse(texte.any { "Sehr-Geheim-42" in it }, "Passwort sichtbar in: $texte")
    }

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tabletSchmal() {
        zeigen()
        val b = compose.onNodeWithTag(Verbinden.INHALT).getUnclippedBoundsInRoot()
        assertTrue(b.width <= FoodyBreiten.inhalt, "zu breit: ${b.width}")
    }

    private fun neuAnmelden() {
        konto.stand.value = VerbindungsZustand(true, "https://foody.example", null, "anmeldung_abgelaufen")
        zeigen()
        compose.onNodeWithText("Neu anmelden").performClick()
        compose.onNodeWithText("Benutzername").performTextInput("Weissi")
        compose.onNodeWithText("Passwort").performTextInput("geheim-123")
        compose.onNodeWithText("Anmelden").performClick()
        compose.waitForIdle()
    }

    @Test
    fun neuAnmeldenGleichesKontoAktiviertOhneFrage() {
        konto.wiederanmeldung = true
        konto.lokaleDaten = true
        neuAnmelden()
        assertEquals("aktivieren:ZUSAMMENFUEHREN", konto.aufrufe.last())
    }

    @Test
    fun neuAnmeldenAnderesKontoVerlangtTrennen() {
        konto.wiederanmeldung = false
        konto.lokaleDaten = true
        neuAnmelden()
        assertFalse(konto.aufrufe.any { it.startsWith("aktivieren") }, "nichts zusammenführen: ${konto.aufrufe}")
        compose.onNodeWithText(anmeldeFehlerText(Verbinden.ERST_TRENNEN)).assertExists()
    }
}
