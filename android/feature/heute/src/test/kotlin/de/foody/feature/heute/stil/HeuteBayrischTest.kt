package de.foody.feature.heute.stil

import android.provider.Settings
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
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** „Heute“ im Bayrischen Stil (Immersiv): der Maibaum statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteBayrischTest {
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

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV, schrift200: Boolean = false) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.BAYRISCH, variante = variante, unschaerfe = false) {
                if (schrift200) MitSchrift200 { HeuteScreen(beispielZustand(), aktionen) } else HeuteScreen(beispielZustand(), aktionen)
            }
        }
    }

    @Test
    fun immersivZeigtMaibaum() {
        zeigen()
        compose.onNodeWithText("Heid").assertExists()
        compose.onNodeWithText("Griaß di!").assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
        compose.onNodeWithContentDescription("Maibaum: 860 von 2.000 kcal", substring = true).assertExists()
        compose.onNodeWithText("Heid auf d'Nacht").assertExists()
    }

    @Test
    fun ruhigZeigtNormaleAnsicht() {
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("Griaß di!").assertDoesNotExist()
    }

    @Test
    fun alleWege() {
        zeigen()
        compose.onNodeWithContentDescription("Kochen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tagebuch, 860 von 2.000 kcal").performScrollTo().performClick()
        compose.onNodeWithText("+ Brotzeit eitragn").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Scannen oder Foto").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        assertEquals(
            listOf("kochen r1 2", "rezept r1", "alle", "eintragen ${Mahlzeit.SNACK.name}", "eintragen null", "einkauf l1", "einstellungen"),
            ausgeloest,
        )
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun texte() {
        assertEquals("Passt scho – no 1.140 frei", bilanzSatz(860, 2000))
        assertEquals("Obacht – 300 drüber", bilanzSatz(2300, 2000))
        assertEquals("860 kcal heid", bilanzSatz(860, null))
        assertEquals("Heid auf d'Nacht", essenKopf("ABENDESSEN", morgen = false))
        assertEquals("Morgn in da Friah", essenKopf("FRUEHSTUECK", morgen = true))
        val t = beispielZustand().tagebuch
        assertEquals("Haferflocken mit Apfel · 380", schildText(t.mahlzeiten[0]))
        assertEquals("no nix", schildText(MahlzeitAbschnitt(Mahlzeit.SNACK, emptyList(), emptyList())))
        assertEquals("Ofengemüse mit Feta und Kräuterquark · 540", schildText(t.mahlzeiten[3]))
        assertEquals(
            "860 von 2.000 kcal. Frühstück: Haferflocken mit Apfel, 380 kcal. Mittagessen: Linsensuppe, 480 kcal. " +
                "Snack: noch nichts. Abendessen: Ofengemüse mit Feta und Kräuterquark, geplant",
            maibaumBeschreibung(t),
        )
    }
}
