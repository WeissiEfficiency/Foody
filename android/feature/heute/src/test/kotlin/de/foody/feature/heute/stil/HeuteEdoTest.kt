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
import java.time.LocalDate
import kotlin.test.assertEquals

/** „Heute“ im Edo-Stil (Immersiv): Rollbild und Speisefolge statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteEdoTest {
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
            FoodyTheme(stil = FoodyStil.EDO, variante = variante, unschaerfe = false) {
                if (schrift200) MitSchrift200 { HeuteScreen(beispielZustand(), aktionen) } else HeuteScreen(beispielZustand(), aktionen)
            }
        }
    }

    @Test
    fun immersivZeigtRollbild() {
        zeigen()
        compose.onNodeWithContentDescription("Heute").assertExists()
        compose.onNodeWithText("HEUTE ABEND · 24. OKTOBER").assertExists()
        compose.onNodeWithText("Speisefolge ›").assertExists()
        compose.onNodeWithContentDescription("Frühstück: Haferflocken mit Apfel, 380 kcal, gegessen").assertExists()
    }

    @Test
    fun ruhigZeigtNormaleAnsicht() {
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("Speisefolge ›").assertDoesNotExist()
    }

    @Test
    fun alleWege() {
        zeigen()
        compose.onNodeWithContentDescription("Kochen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tagebuch, alle Einträge").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Snack eintragen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Abendessen: Ofengemüse mit Feta und Kräuterquark, geplant, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Scannen oder Foto").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performScrollTo().performClick()
        assertEquals(
            listOf("kochen r1 2", "rezept r1", "alle", "eintragen ${Mahlzeit.SNACK.name}", "rezept r1", "einkauf l1", "eintragen null", "einstellungen"),
            ausgeloest,
        )
    }

    @Test
    fun stempelFragtGegessen() {
        zeigen()
        compose.onNodeWithContentDescription("Abendessen: als gegessen abstempeln").performScrollTo().performClick()
        compose.onNodeWithText("Ja").performClick()
        assertEquals(listOf("gegessen p1"), ausgeloest)
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun texte() {
        val tag = LocalDate.of(2026, 10, 24)
        assertEquals("HEUTE ABEND · 24. OKTOBER", edoKopf("ABENDESSEN", morgen = false, datum = tag))
        assertEquals("MORGEN FRÜH · 25. OKTOBER", edoKopf("FRUEHSTUECK", morgen = true, datum = tag.plusDays(1)))
        val t = beispielZustand().tagebuch
        assertEquals("Haferflocken mit Apfel", folgeText(t.mahlzeiten[0]))
        assertEquals("Ofengemüse, geplant", folgeText(t.mahlzeiten[3]))
        assertEquals("＋ Eintragen", folgeText(MahlzeitAbschnitt(Mahlzeit.SNACK, emptyList(), emptyList())))
    }
}
