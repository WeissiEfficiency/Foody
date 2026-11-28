package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteScreen
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** „Heute“ im Anime-Stil (Immersiv): „HEUTE!“ mit Reisbällchen statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteAnimeTest {
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

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.ANIME, variante = variante, unschaerfe = false) {
                HeuteScreen(beispielZustand(), aktionen)
            }
        }
    }

    @Test
    fun immersivZeigtSzene() {
        zeigen()
        compose.onNodeWithText("HEUTE!").assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
        compose.onNodeWithText("Heute Abend gibt's Ofengemüse! Noch 1.140 kcal frei – das passt perfekt.").assertExists()
        compose.onNodeWithContentDescription("860 von 2.000 kcal").assertExists()
        compose.onNodeWithContentDescription("Frühstück: Haferflocken mit Apfel, 380 kcal").assertExists()
    }

    @Test
    fun ruhigZeigtSzeneSchlicht() {
        zeigen(StilVariante.RUHIG)
        // Wie im Entwurf: dieselbe Szene, aber „Heute“ statt „HEUTE!“ und ohne Foody das Reisbällchen
        compose.onNodeWithText("HEUTE!").assertDoesNotExist()
        compose.onNodeWithText("Heute").assertExists()
        compose.onNodeWithText("Heute Abend gibt's Ofengemüse! Noch 1.140 kcal frei – das passt perfekt.").assertDoesNotExist()
        compose.onNodeWithContentDescription("860 von 2.000 kcal").assertExists()
        compose.onNodeWithContentDescription("Frühstück: Haferflocken mit Apfel, 380 kcal").assertExists()
    }

    @Test
    fun alleWege() {
        zeigen()
        compose.onNodeWithContentDescription("Kochen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tagebuch öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Eintragen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        assertEquals(listOf("kochen r1 2", "rezept r1", "alle", "eintragen null", "einkauf l1", "einstellungen"), ausgeloest)
    }

    /** Ohne Tagesziel keine leeren Segmente, sondern „Tagesziel festlegen ▸“; das führt in die Einstellungen. */
    @Test
    fun ohneZielHinweis() {
        val z = beispielZustand()
        compose.setContent {
            FoodyTheme(stil = FoodyStil.ANIME, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                HeuteScreen(z.copy(tagebuch = z.tagebuch.copy(ziel = null)), aktionen)
            }
        }
        compose.onNodeWithContentDescription("860 kcal").assertExists()
        compose.onNodeWithText("Tagesziel festlegen ▸").performScrollTo().performClick()
        assertEquals(listOf("einstellungen"), ausgeloest)
    }

    @Test
    fun texte() {
        assertEquals("Sa · 24.10.", animeDatum(LocalDate.of(2026, 10, 24)))
        assertEquals("Ofengemüse", kurzname("Ofengemüse mit Feta und Kräuterquark"))
        assertEquals("Linsensuppe", kurzname("Linsensuppe"))
        assertEquals(4.3f, segmente(860, 2000), 0.001f)
        assertEquals(10f, segmente(2500, 2000))
        assertEquals(0f, segmente(860, null))
        // Döner 350 g: 46 g Eiweiß (184 kcal), 63 g KH (252), 28 g Fett (252) → Grenzen bei 27 % und 63 %
        val (e, k) = makroGrenzen(46, 63, 28)!!
        assertEquals(0.267f, e, 0.001f)
        assertEquals(0.634f, k, 0.001f)
        assertEquals(null, makroGrenzen(0, 0, 0))
        val z = beispielZustand()
        assertEquals(
            "Heute Abend gibt's Ofengemüse! Heute war's schon reichlich – morgen geht's weiter!",
            sprechblase(z.copy(tagebuch = z.tagebuch.copy(summe = z.tagebuch.summe.copy(kcal = 2100)))),
        )
        assertEquals(
            "Noch nichts geplant – trag ein, was du isst! Schon 860 kcal heute!",
            sprechblase(z.copy(naechste = null, tagebuch = z.tagebuch.copy(ziel = null))),
        )
    }
}
