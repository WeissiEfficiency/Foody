package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals

/** „Heute“ im Kosmischen Grauen (Immersiv): „Das Auge“ statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteGrauenTest {
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
        onGegessen = { ausgeloest += "gegessen $it" },
        onNichtGegessen = { ausgeloest += "nicht ${it.planEintragId}" },
    )

    /** Animationen aus: Schweben und Blinzeln ruhen, der Test wartet nicht auf sie. */
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV, schrift200: Boolean = false) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = variante, unschaerfe = false) {
                if (schrift200) MitSchrift200 { HeuteScreen(beispielZustand(), aktionen) } else HeuteScreen(beispielZustand(), aktionen)
            }
        }
    }

    @Test
    fun immersivZeigtDasAuge() {
        zeigen()
        compose.onNodeWithText("Das Auge").assertExists()
        compose.onNodeWithText("Das Ritual des Abends").assertExists()
        // Das Auge liest TalkBack als nüchternen Satz
        compose.onNodeWithContentDescription("860 von 2.000 kcal: Eiweiß 42 g, Kohlenhydrate 96 g, Fett 31 g").assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
    }

    @Test
    fun ruhigZeigtNormaleAnsicht() {
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("Das Auge").assertDoesNotExist()
        compose.onNodeWithText("Heute").assertExists()
    }

    @Test
    fun ritualKochtUndTitelOeffnetRezept() {
        zeigen()
        compose.onNodeWithText("Das Ritual beginnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        assertEquals(listOf("kochen r1 2", "rezept r1"), ausgeloest)
    }

    @Test
    fun protokollNotierenUndAlleEintraege() {
        zeigen()
        compose.onNodeWithText("Die Zwischenmahlzeit fehlt noch …").performScrollTo()
        compose.onNodeWithContentDescription("Snack eintragen").performClick()
        compose.onNodeWithContentDescription("Tagebuch, alle Einträge").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        assertEquals(listOf("eintragen ${Mahlzeit.SNACK.name}", "alle", "einkauf l1", "einstellungen"), ausgeloest)
    }

    @Test
    fun eintragLiestMahlzeitUndKcal() {
        zeigen()
        compose.onNodeWithContentDescription("Frühstück: Haferflocken mit Apfel, 380 kcal").assertExists()
    }

    @Test
    fun vorgesehenFragtGegessen() {
        zeigen()
        compose.onNodeWithContentDescription("Abendessen Ofengemüse mit Feta und Kräuterquark: gegessen?").performScrollTo().performClick()
        compose.onNodeWithText("Ja").performClick()
        assertEquals(listOf("gegessen p1"), ausgeloest)
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
        // Der Titel bleibt einzeilig, das Datum rückt darunter
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Das Auge").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(1, layout.single().lineCount)
    }

    @Test
    fun texte() {
        assertEquals("Sa · 24. Oktober", grauenDatum(LocalDate.of(2026, 10, 24)))
        assertEquals("Es hat 860 von 2.000 kcal gesehen.", augenSatz(860, 2000))
        assertEquals("Es hat 860 kcal gesehen.", augenSatz(860, null))
        assertEquals("Es hat 2.300 von 2.000 kcal gesehen. Mehr, als es wollte.", augenSatz(2300, 2000))
        assertEquals("Das Ritual des Abends", ritualName("ABENDESSEN", morgen = false))
        assertEquals("Das Ritual des Morgens · morgen", ritualName("FRUEHSTUECK", morgen = true))
        assertEquals("Brunch", ritualName("Brunch", morgen = false))
        assertEquals("Die Zwischenmahlzeit fehlt noch …", Mahlzeit.SNACK.fehltNoch())
    }
}
