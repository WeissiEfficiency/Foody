package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.MitSchrift200
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.glas.GlasModusKey
import de.foody.core.designsystem.pruefeNichtsAbgeschnitten
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

// Native Grafik: echte Textmessung (im Standardmodus ist Text fast 0 px breit)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GrundbausteineTest {
    @get:Rule
    val compose = createComposeRule()

    private val ziele = listOf(
        NavZiel("Heute", Icons.Outlined.LightMode),
        NavZiel("Rezepte", Icons.AutoMirrored.Outlined.MenuBook),
        NavZiel("Woche", Icons.Outlined.CalendarMonth),
        NavZiel("Einkauf", Icons.Outlined.ShoppingBag),
    )

    @Test
    fun knopfMindestens48dp() {
        compose.setContent {
            FoodyTheme {
                Column {
                    FoodyKnopf("Kochen", onKlick = {})
                    FoodyKnopf("Eintragen", onKlick = {}, art = KnopfArt.UMRISS)
                    FoodyKnopf("Alle ansehen", onKlick = {}, art = KnopfArt.TEXT)
                }
            }
        }
        for (t in listOf("Kochen", "Eintragen", "Alle ansehen")) {
            compose.onNodeWithText(t).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun knopfDeaktiviertMeldetDas() {
        compose.setContent { FoodyTheme { FoodyKnopf("Kochen", onKlick = {}, aktiv = false) } }
        compose.onNodeWithText("Kochen").assertIsNotEnabled()
    }

    @Test
    fun chipMeldetAuswahl() {
        compose.setContent {
            FoodyTheme {
                Column {
                    FoodyChip("Favoriten", ausgewaehlt = true, onKlick = {})
                    FoodyChip("vegetarisch", ausgewaehlt = false, onKlick = {})
                }
            }
        }
        compose.onNodeWithText("Favoriten").assertIsSelected()
        compose.onNodeWithText("vegetarisch").assertIsNotSelected()
        compose.onNodeWithText("vegetarisch").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun abschnittskopfIstUeberschrift() {
        compose.setContent { FoodyTheme { Abschnittskopf("Einkauf", linkText = "6 offen", onLink = {}) } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("6 offen").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun augenbraueInGrossbuchstaben() {
        compose.setContent { FoodyTheme { Augenbraue("Abendessen · 18:30") } }
        compose.onNodeWithText("ABENDESSEN · 18:30").assertExists()
    }

    @Test
    fun navigationWaehltZiel() {
        var gewaehlt = -1
        compose.setContent {
            FoodyTheme { FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = { gewaehlt = it }) }
        }
        compose.onNodeWithText("Woche", useUnmergedTree = true).performClick()
        assertEquals(2, gewaehlt)
        compose.onNodeWithText("Heute").assertIsSelected()
    }

    @Test
    fun railWaehltZiel() {
        var gewaehlt = -1
        compose.setContent {
            FoodyTheme { FoodyNavigationsRail(ziele, aktiv = 1, onWahl = { gewaehlt = it }) }
        }
        compose.onNodeWithText("Einkauf", useUnmergedTree = true).performClick()
        assertEquals(3, gewaehlt)
    }

    /** Anime: Die Leiste ist deckend weiß, nicht Glas über dem rosa Punktmuster (Immersiv und Ruhig). */
    @Test
    fun animeLeisteDeckendWeiss() {
        var variante by mutableStateOf(StilVariante.IMMERSIV)
        compose.setContent {
            FoodyTheme(stil = FoodyStil.ANIME, variante = variante, unschaerfe = true) {
                FoodyHintergrund { FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = {}) }
            }
        }
        for (v in StilVariante.entries) {
            variante = v
            compose.waitForIdle()
            // Über dem Symbol des zweiten Ziels, unter der Kante: dort liegt nur die Fläche der Leiste
            val leiste = compose.onNode(SemanticsMatcher.keyIsDefined(GlasModusKey), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val x = leiste.left + leiste.width * 3 / 8
            val y = leiste.top + with(compose.density) { 5.dp.toPx() }
            assertEquals(Color.White, compose.onRoot().captureToImage().toPixelMap()[x.toInt(), y.toInt()], v.name)
        }
    }

    @Test
    fun doppelteSchriftOhneAbschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    Column(Modifier.width(411.dp)) {
                        FoodyKnopf("Kochen", onKlick = {})
                        FoodyChip("Favoriten", ausgewaehlt = true, onKlick = {})
                        Abschnittskopf("Einkauf", linkText = "6 offen", onLink = {})
                        FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = {})
                    }
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }

    private val fuenf = listOf(
        ziele[0],
        NavZiel("Eintragen", Icons.Outlined.AddCircleOutline, aktion = true),
    ) + ziele.drop(1)

    /** Eine Aktion in der Leiste (Eintragen) ist ein Knopf, kein Reiter: nie ausgewählt, ein Tipp meldet ihre Stelle. */
    @Test
    fun aktionIstKnopf() {
        var gewaehlt = -1
        compose.setContent { FoodyTheme { FoodyNavigationsleiste(fuenf, aktiv = 1, onWahl = { gewaehlt = it }) } }
        val eintragen = compose.onNodeWithText("Eintragen")
        eintragen.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        eintragen.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        eintragen.performClick()
        assertEquals(1, gewaehlt)
    }

    /** Fünf Punkte bei 360 dp und Schrift 130 %: jedes Wort in einer Zeile, nichts abgeschnitten, auch die Stilwörter. */
    @Test
    fun fuenfPunkteBei130() {
        val bayrisch = listOf("Heid", "Eitragn", "Rezepte", "Wochn", "Eikaufn")
        val kreuzer = listOf("Brücke", "Erfassen", "Archiv", "Kurs", "Fracht")
        compose.setContent {
            FoodyTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.3f)) {
                    Column(Modifier.width(360.dp)) {
                        FoodyNavigationsleiste(fuenf, aktiv = 0, onWahl = {})
                        for (woerter in listOf(bayrisch, kreuzer)) {
                            FoodyNavigationsleiste(woerter.map { NavZiel(it, Icons.Outlined.LightMode) }, aktiv = 0, onWahl = {})
                        }
                    }
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
        for (t in fuenf.map { it.text } + bayrisch + kreuzer) {
            compose.onAllNodesWithText(t, useUnmergedTree = true).onFirst().assertExists()
        }
    }
}
