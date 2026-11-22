package de.foody.feature.heute.stil

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.domain.Mahlzeit
import de.foody.domain.Summe
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.tagebuch.TagAbschnitt
import de.foody.feature.heute.tagebuch.TagZustand
import de.foody.feature.heute.tagebuch.TagebuchTagInhalt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regel 24: fehlen einem Eintrag die Energiewerte, ist die Tagessumme nur eine Untergrenze. Jede Stelle, die sie auf
 * „Heute“ oder im Tagebuch zeigt oder vorliest, schreibt dann „≥ 860“ – in allen Stilen.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class TagessummeMindestensTest {
    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private val offen = Summe(kcal = 860, eiweiss = 42, kohlenhydrate = 96, fett = 31, vollstaendig = false)
    private val aktionen = HeuteAktionen({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})

    /** Alle Texte und Beschreibungen, auch unter zusammengefassten Knoten. */
    private fun texte(): List<String> = compose.onAllNodes(SemanticsMatcher("alle") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { n ->
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }

    private fun pruefe(stil: FoodyStil, wo: String, inhalt: @Composable () -> Unit) {
        compose.setContent { FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV, unschaerfe = false) { inhalt() } }
        val alle = texte()
        assertTrue(alle.any { "≥ 860" in it }, "$stil $wo: keine Stelle mit „≥ 860“ in $alle")
        val ohne = alle.filter { OHNE_ZEICHEN.containsMatchIn(it) }
        assertEquals(emptyList(), ohne, "$stil $wo: Tagessumme ohne „≥“")
    }

    private fun heute(stil: FoodyStil) = pruefe(stil, "Heute") {
        val z = beispielZustand()
        HeuteScreen(z.copy(tagebuch = z.tagebuch.copy(summe = offen)), aktionen)
    }

    private fun tagebuch(stil: FoodyStil) = pruefe(stil, "Tagebuch") {
        val z = TagZustand(heute, offen, 2000, Mahlzeit.entries.map { TagAbschnitt(it, emptyList()) })
        when (stil) {
            FoodyStil.MILCHGLAS -> TagebuchTagInhalt(heute, z, {}, {}, {}, {}, {})
            FoodyStil.SAGE -> TagebuchSage(heute, heute, z, {}, {}, {}, {}, {})
            else -> TagebuchStil(stil, heute, heute, z, {}, {}, {}, {}, {})
        }
    }

    @Test fun heuteMilchglas() = heute(FoodyStil.MILCHGLAS)

    @Test fun heuteSage() = heute(FoodyStil.SAGE)

    @Test fun heuteAnime() = heute(FoodyStil.ANIME)

    @Test fun heuteEdo() = heute(FoodyStil.EDO)

    @Test fun heuteBayrisch() = heute(FoodyStil.BAYRISCH)

    @Test fun heuteGrauen() = heute(FoodyStil.GRAUEN)

    @Test fun heuteKreuzer() = heute(FoodyStil.KREUZER)

    @Test fun tagebuchMilchglas() = tagebuch(FoodyStil.MILCHGLAS)

    @Test fun tagebuchSage() = tagebuch(FoodyStil.SAGE)

    @Test fun tagebuchAnime() = tagebuch(FoodyStil.ANIME)

    @Test fun tagebuchEdo() = tagebuch(FoodyStil.EDO)

    @Test fun tagebuchBayrisch() = tagebuch(FoodyStil.BAYRISCH)

    @Test fun tagebuchGrauen() = tagebuch(FoodyStil.GRAUEN)

    @Test fun tagebuchKreuzer() = tagebuch(FoodyStil.KREUZER)

    private companion object {
        /** „860“ als eigene Zahl, ohne „≥ “ davor. */
        val OHNE_ZEICHEN = Regex("""(?<!≥ )(?<![\d.])860(?![\d.])""")
    }
}
