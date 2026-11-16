package de.foody.core.designsystem.token

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.edo.Edo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Kurze Wörter bleiben einzeilig, wenn sie so breit sind wie ihr Text: In Edo (Zen Kaku) brachen mit Silbentrennung
 * und Absatz-Umbruch „Al-le“ (gewählter Filter) und „Vari-ante“ um (Gerätetest 2026-11-17).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class KurzeWoerterTest {
    @get:Rule
    val compose = createComposeRule()

    private val woerter = listOf("Alle", "Variante", "Heute", "Rezepte", "Kochen", "Eintragen", "Speichern", "Weiter")

    private fun pruefen(stil: FoodyStil, variante: StilVariante = StilVariante.RUHIG, extra: List<Pair<String, TextStyle>> = emptyList()) {
        val umgebrochen = mutableListOf<String>()
        compose.setContent {
            FoodyTheme(stil = stil, variante = variante) {
                val t = MaterialTheme.typography
                val schriften = listOf(
                    "titleMedium" to t.titleMedium, "titleSmall" to t.titleSmall, "bodyLarge" to t.bodyLarge,
                    "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall, "labelLarge" to t.labelLarge,
                    "labelMedium" to t.labelMedium, "labelSmall" to t.labelSmall,
                ) + extra
                Column {
                    schriften.forEach { (name, schrift) ->
                        woerter.forEach { w -> Text(w, style = schrift, onTextLayout = { if (it.lineCount > 1) umgebrochen += "$name „$w“" }) }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(emptyList(), umgebrochen.distinct(), "$stil $variante")
    }

    @Test fun milchglas() = pruefen(FoodyStil.MILCHGLAS)

    @Test fun sage() = pruefen(FoodyStil.SAGE)

    @Test fun anime() = pruefen(FoodyStil.ANIME)

    @Test fun edo() = pruefen(FoodyStil.EDO, extra = listOf("Edo.text" to Edo.text(14f), "Edo.text fett" to Edo.text(15f, androidx.compose.ui.text.font.FontWeight.Bold)))

    @Test fun edoImmersiv() = pruefen(FoodyStil.EDO, StilVariante.IMMERSIV)

    @Test fun bayrisch() = pruefen(FoodyStil.BAYRISCH)

    @Test fun grauen() = pruefen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = pruefen(FoodyStil.KREUZER)
}
