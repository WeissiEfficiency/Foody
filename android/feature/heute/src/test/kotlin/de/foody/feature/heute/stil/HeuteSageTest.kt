package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.MitSchrift
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.tagebuch.SofortStart
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** „Heute“ in der Düsteren Sage (Immersiv): Szene statt normaler Ansicht, Menü und Hauptknopf lösen dieselben Aktionen aus. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteSageTest {
    @get:Rule
    val compose = createComposeRule()

    private val ausgeloest = mutableListOf<String>()
    private val aktionen = HeuteAktionen(
        onKochen = { id, p -> ausgeloest += "kochen $id $p" },
        onRezept = { ausgeloest += "rezept $it" },
        onEinkauf = { ausgeloest += "einkauf $it" },
        onAlleEintraege = { ausgeloest += "alle" },
        onEinstellungen = { ausgeloest += "einstellungen" },
        onEintragen = { ausgeloest += "eintragen ${it?.name}" },
        onSofort = { ausgeloest += "sofort $it" },
        onGegessen = { ausgeloest += "gegessen $it" },
        onNichtGegessen = { ausgeloest += "nicht ${it.planEintragId}" },
    )

    /** Mit „Foto“ (Modell da); ein Test schaltet es ab. */
    private var zustand by mutableStateOf(beispielZustand())

    /** Animationen aus: Flamme und Glut ruhen, der Test wartet nicht auf sie. */
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV, schrift: Float = 1f) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.SAGE, variante = variante, unschaerfe = false) { MitSchrift(schrift) { HeuteScreen(zustand, aktionen) } }
        }
    }

    @Test
    fun immersivZeigtSzene() {
        zeigen()
        compose.onNodeWithText("RAST AM FEUER").assertExists()
        // Vitalität liest TalkBack als einen Satz
        compose.onNodeWithContentDescription("860 von 2.000 kcal: Eiweiß 42 g, Kohlenhydrate 96 g, Fett 31 g").assertExists()
    }

    /** Ruhig wie im Entwurf: die Szene schlicht, statt des Lagerfeuers der Kopf „HEUTE“, das Menü bleibt. */
    @Test
    fun ruhigZeigtSzeneSchlicht() {
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("RAST AM FEUER").assertDoesNotExist()
        compose.onNodeWithText("HEUTE").assertExists()
        compose.onNodeWithText("AM FEUER KOCHEN").performScrollTo().performClick()
        assertEquals(listOf("kochen r1 2"), ausgeloest)
    }

    @Test
    fun hauptknopfKochtDieNaechsteMahlzeit() {
        zeigen()
        compose.onNodeWithText("AM FEUER KOCHEN").performScrollTo().performClick()
        assertEquals(listOf("kochen r1 2"), ausgeloest)
    }

    @Test
    fun menueWaehltUndHauptknopfFolgt() {
        zeigen()
        compose.onNodeWithText("MAHL EINTRAGEN").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eintragen: Snack. Das Zwischenmahl", substring = true).assertIsSelected()
        compose.onNodeWithText("EINTRAGEN").performScrollTo().performClick()
        compose.onNodeWithText("CHRONIK DES TAGES").performScrollTo().performClick()
        compose.onNodeWithText("CHRONIK ÖFFNEN").performScrollTo().performClick()
        compose.onNodeWithText("BEIM HÄNDLER").performScrollTo().performClick()
        compose.onNodeWithText("ZUM HÄNDLER").performScrollTo().performClick()
        assertEquals(listOf("eintragen ${Mahlzeit.SNACK.name}", "alle", "einkauf l1"), ausgeloest)
    }

    @Test
    fun verzehrtFragtWieGegessen() {
        zeigen()
        compose.onNodeWithText("ABENDMAHL VERZEHRT?").performScrollTo().performClick()
        compose.onNodeWithText("ALS VERZEHRT EINTRAGEN").performScrollTo().performClick()
        compose.onNodeWithText("Ja").performClick()
        assertEquals(listOf("gegessen p1"), ausgeloest)
    }

    @Test
    fun nebenwegeUndEinstellungen() {
        zeigen()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("REZEPT ANSEHEN").performScrollTo().performClick()
        assertEquals(listOf("einstellungen", "rezept r1"), ausgeloest)
    }

    /** „+ SCANNEN“ und „+ FOTO“ starten Eintragen gleich mit Scanner bzw. Kamera; „+ FOTO“ nur mit Modell auf dem Gerät. */
    @Test
    fun scannenUndFotoGetrennt() {
        zeigen()
        compose.onNodeWithContentDescription("Scannen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Foto").performScrollTo().performClick()
        assertEquals(listOf("sofort ${SofortStart.SCANNEN}", "sofort ${SofortStart.FOTO}"), ausgeloest)
        compose.onNodeWithText("+ SCANNEN").assertExists()
        compose.onNodeWithText("+ FOTO").assertExists()
        zustand = zustand.copy(fotoMoeglich = false)
        compose.onNodeWithContentDescription("Scannen").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Foto").assertDoesNotExist()
        compose.onNodeWithText("+ FOTO").assertDoesNotExist()
    }

    /** Schmales Handy mit 130 % Schrift: Die Knöpfe (und alles andere) passen. */
    @Test
    @Config(qualifiers = "w360dp-h1600dp")
    fun szenenKnoepfeBei130() {
        zeigen(schrift = 1.3f)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun titelHeisstFuerTalkBackHeute() {
        zeigen()
        compose.onNodeWithContentDescription("Heute").assertExists()
    }
}
