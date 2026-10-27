package de.foody.core.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scan-Knopf mit Fake-Funktionen statt Play-Diensten (Kamera und Galerie bleiben außen vor). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class PackungScanKnopfTest {
    @get:Rule
    val compose = createComposeRule()

    private val code = "4006040002031"
    private val packung = Packungswerte("Joghurt", NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(276)), code, OpenFoodFacts.QUELLE)

    private var ausgang: ScanAusgang = ScanAusgang.Gefunden(packung)
    private val packungen = mutableListOf<Packungswerte>()
    private val imKatalog = mutableListOf<ScanAusgang.ImKatalog>()
    private var vorgeladen = 0

    private fun zeigen(verfuegbar: Boolean = true, schrift200: Boolean = false) {
        compose.setContent {
            FoodyTheme {
                val inhalt = @Composable {
                    PackungScanKnopf(
                        verfuegbar = verfuegbar,
                        vorladen = { vorgeladen++ },
                        strichcode = { ausgang },
                        foto = { _, _ -> ausgang },
                        onPackung = { packungen += it },
                        onImKatalog = { imKatalog += it },
                    )
                }
                if (schrift200) {
                    val d = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { inhalt() }
                } else {
                    inhalt()
                }
            }
        }
    }

    private fun strichcodeScannen() {
        compose.onNodeWithText("Packung scannen").performClick()
        compose.onNodeWithText("Strichcode").performClick()
        compose.waitForIdle()
    }

    @Test
    fun menueZeigtDreiWege() {
        zeigen()
        compose.onNodeWithText("Packung scannen").assertIsEnabled().performClick()
        compose.onNodeWithText("Strichcode").assertExists()
        compose.onNodeWithText("Foto der Nährwerttabelle").assertExists()
        compose.onNodeWithText("Aus der Galerie").assertExists()
        assertEquals(1, vorgeladen)
    }

    @Test
    fun ohnePlayDiensteAusgegraut() {
        zeigen(verfuegbar = false)
        compose.onNode(hasText("Packung scannen") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Scannen braucht die Google-Play-Dienste").assertExists()
    }

    @Test
    fun gefundenRuftOnPackung() {
        zeigen()
        strichcodeScannen()
        assertEquals(listOf(packung), packungen)
        assertTrue(imKatalog.isEmpty())
    }

    @Test
    fun imKatalogRuftOnImKatalog() {
        ausgang = ScanAusgang.ImKatalog("z", "Joghurt", code)
        zeigen()
        strichcodeScannen()
        assertEquals(listOf(ScanAusgang.ImKatalog("z", "Joghurt", code)), imKatalog)
        assertTrue(packungen.isEmpty())
    }

    @Test
    fun nichtGefundenZeigtFotoKnoepfe() {
        ausgang = ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT)
        zeigen()
        strichcodeScannen()
        compose.onNodeWithText("Produkt nicht gefunden – Foto der Nährwerttabelle?").assertExists()
        // Menü ist zu: die Foto-Wege stehen jetzt als Textknöpfe unter der Meldung
        compose.onNodeWithText("Foto der Nährwerttabelle").assertExists()
        compose.onNodeWithText("Aus der Galerie").assertExists()
        assertTrue(packungen.isEmpty())
    }

    @Test
    fun abgebrochenZeigtNichts() {
        ausgang = ScanAusgang.Abgebrochen
        zeigen()
        strichcodeScannen()
        assertEquals(0, compose.onAllNodesWithText("Foto der Nährwerttabelle").fetchSemanticsNodes().size)
        compose.onNodeWithText("Packung scannen").assertIsEnabled()
    }

    @Test
    fun schrift200() {
        ausgang = ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE)
        zeigen(schrift200 = true)
        strichcodeScannen()
        compose.onNodeWithText("Keine Verbindung – Foto der Nährwerttabelle?").assertExists()
        compose.pruefeNichtsAbgeschnitten()
    }
}

/** Wie in :core:designsystem: zu viele Zeilen, gekürzte Zeile oder zu wenig Höhe. */
private fun ComposeTestRule.pruefeNichtsAbgeschnitten() {
    val abgeschnitten = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node ->
            val ergebnisse = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(ergebnisse)
            ergebnisse.firstOrNull()?.takeIf {
                it.multiParagraph.didExceedMaxLines || (0 until it.lineCount).any(it::isLineEllipsized) ||
                    it.multiParagraph.height > it.size.height + 0.5f
            }?.layoutInput?.text?.text
        }
    assertTrue(abgeschnitten.isEmpty(), "abgeschnitten: $abgeschnitten")
}
