package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
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

/** „Heute“ im Sternenkreuzer (Immersiv): das Tagessystem statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteKreuzerTest {
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

    /** Animationen aus: Bahn und Korona ruhen, der Test wartet nicht auf sie. */
    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV, schrift200: Boolean = false) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.KREUZER, variante = variante, unschaerfe = false) {
                if (schrift200) MitSchrift200 { HeuteScreen(beispielZustand(), aktionen) } else HeuteScreen(beispielZustand(), aktionen)
            }
        }
    }

    @Test
    fun immersivZeigtTagessystem() {
        zeigen()
        compose.onNodeWithText("Tagessystem").assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
        compose.onNodeWithContentDescription("860 von 2.000 kcal").assertExists()
        // Zu Beginn ist der geplante Planet gewählt
        compose.onNodeWithText("ORBIT 3 · ABEND").assertExists()
        compose.onNodeWithContentDescription("Abendessen: Ofengemüse mit Feta und Kräuterquark, geplant").assertIsSelected()
    }

    @Test
    fun ruhigZeigtNormaleAnsicht() {
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("Tagessystem").assertDoesNotExist()
        compose.onNodeWithText("Heute").assertExists()
    }

    @Test
    fun gelandeterPlanetOeffnetBordbuch() {
        zeigen()
        compose.onNodeWithContentDescription("Frühstück: Haferflocken mit Apfel, gegessen").performClick()
        compose.onNodeWithText("ORBIT 1 · FRÜHSTÜCK").assertExists()
        compose.onNodeWithText("380 kcal · gelandet").assertExists()
        compose.onNodeWithContentDescription("Tagebuch öffnen").performScrollTo().performClick()
        assertEquals(listOf("alle"), ausgeloest)
    }

    @Test
    fun freierPlanetErfasstProviant() {
        zeigen()
        compose.onNodeWithContentDescription("Snack: noch nichts eingetragen").performClick()
        compose.onNodeWithContentDescription("Snack eintragen").performScrollTo().performClick()
        assertEquals(listOf("eintragen ${Mahlzeit.SNACK.name}"), ausgeloest)
    }

    @Test
    fun geplanterPlanetZeigtRezeptUndFragtGegessen() {
        zeigen()
        compose.onNodeWithContentDescription("Rezept ansehen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gegessen?").performScrollTo().performClick()
        compose.onNodeWithText("Ja").performClick()
        compose.onNodeWithContentDescription("Gegessen?").performScrollTo().performClick()
        compose.onNodeWithText("Nein").performClick()
        assertEquals(listOf("rezept r1", "gegessen p1", "nicht p1"), ausgeloest)
    }

    @Test
    fun missionUndWeitereWege() {
        zeigen()
        compose.onNodeWithContentDescription("Kochen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithText("+ Eintragen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        assertEquals(listOf("kochen r1 2", "rezept r1", "eintragen null", "einkauf l1", "einstellungen"), ausgeloest)
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Tagessystem").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(1, layout.single().lineCount)
    }

    @Test
    fun texte() {
        assertEquals("SA 24.10. · LOGBUCH 297", logbuchZeile(LocalDate.of(2026, 10, 24)))
        assertEquals("43 % ERREICHT", kreuzerStatus(860, 2000))
        assertEquals("115 % · ÜBER ZIEL", kreuzerStatus(2300, 2000))
        assertEquals("860 KCAL", kreuzerStatus(860, null))
        val p = planeten(beispielZustand().tagebuch)
        assertEquals(listOf(1, 2, 3, 4), p.map { it.bahn })
        assertEquals(listOf(Planetenart.GELANDET, Planetenart.GELANDET, Planetenart.KURS, Planetenart.FREI), p.map { it.art })
        assertEquals("540 kcal · Kurs gesetzt", p[2].info)
        assertEquals(2, startWahl(p))
        assertEquals("Snack: noch nichts eingetragen", planetBeschreibung(p[3]))
    }
}
