package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.MitSchrift200
import de.foody.core.designsystem.abgeschnitteneTexte
import de.foody.core.designsystem.pruefeNichtsAbgeschnitten
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class RezeptkarteTest {
    @get:Rule
    val compose = createComposeRule()

    private val langerTitel = "Rinderrouladen-Schmorgericht mit Rotkohl und Klößen und Rahmsoße"

    @Test
    fun grosseKarteZeigtAlles() {
        var gekocht = 0
        compose.setContent {
            FoodyTheme {
                RezeptkarteGross(
                    titel = "Senf-Krusten-Braten",
                    meta = "4 Portionen · 658 kcal · 95 Min.",
                    onKlick = {},
                    augenbraue = "Abendessen · 18:30",
                    aktion = KartenAktion("Kochen") { gekocht++ },
                )
            }
        }
        compose.onNodeWithText("ABENDESSEN · 18:30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Senf-Krusten-Braten", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4 Portionen · 658 kcal · 95 Min.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Kochen").performClick()
        assertEquals(1, gekocht)
    }

    @Test
    fun langerTitelGekuerztAberVollVorgelesen() {
        compose.setContent {
            FoodyTheme {
                RezeptkarteGross(titel = langerTitel, meta = "6 Portionen · 812 kcal", onKlick = {}, modifier = Modifier.width(300.dp))
            }
        }
        assertTrue(langerTitel in compose.abgeschnitteneTexte(), "Titel sollte sichtbar gekürzt sein")
        compose.onNode(
            SemanticsMatcher("Beschreibung mit vollem Titel") { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { langerTitel in it }
            },
        ).assertExists()
    }

    @Test
    fun ohneBildPlatzhalter() {
        compose.setContent { FoodyTheme { RezeptkarteGross(titel = "Shakshuka", meta = "2 Portionen", onKlick = {}) } }
        compose.onNodeWithTag(PLATZHALTER_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun rasterKarteFavoritVorgelesen() {
        compose.setContent {
            FoodyTheme {
                RezeptkarteRaster(titel = "Kürbissuppe", onKlick = {}, favorit = true, zeit = "25 Min.", kcal = "320 kcal", modifier = Modifier.width(180.dp))
            }
        }
        compose.onNode(
            SemanticsMatcher("Beschreibung mit Favorit") { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { "Favorit" in it && "Kürbissuppe" in it }
            },
        ).assertExists()
    }

    @Test
    fun platzhalterSymbolUeberDerGlasleiste() {
        compose.setContent {
            FoodyTheme {
                RezeptkarteGross(
                    titel = "Senf-Krusten-Braten",
                    meta = "4 Portionen · 658 kcal · 95 Min.",
                    onKlick = {},
                    augenbraue = "Abendessen · 18:30",
                    aktion = KartenAktion("Kochen") {},
                    modifier = Modifier.width(379.dp),
                )
            }
        }
        val symbol = compose.onNodeWithTag(PLATZHALTER_SYMBOL_TAG, useUnmergedTree = true).getBoundsInRoot()
        val leiste = compose.onNodeWithTag(GLASLEISTE_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue(symbol.bottom <= leiste.top - 8.dp, "Symbol (${symbol.bottom}) ragt in die Glas-Leiste (${leiste.top})")
    }

    @Test
    fun beiGrosserSchriftKeinSymbolHinterDemText() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    RezeptkarteGross("Senf-Krusten-Braten", "4 Portionen", onKlick = {}, augenbraue = "Abendessen", modifier = Modifier.width(379.dp))
                }
            }
        }
        compose.onNodeWithTag(PLATZHALTER_SYMBOL_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(PLATZHALTER_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun abzeichenUeberlappenNichtBeiGrosserSchrift() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    RezeptkarteRaster(titel = "Spaghetti Carbonara", onKlick = {}, zeit = "20 Min.", kcal = "640 kcal", modifier = Modifier.width(170.dp))
                }
            }
        }
        val zeit = compose.onNodeWithText("20 Min.", useUnmergedTree = true).getBoundsInRoot()
        val kcal = compose.onNodeWithText("640 kcal", useUnmergedTree = true).getBoundsInRoot()
        val ueberlappen = zeit.left < kcal.right && kcal.left < zeit.right && zeit.top < kcal.bottom && kcal.top < zeit.bottom
        assertTrue(!ueberlappen, "Abzeichen überlappen: $zeit / $kcal")
    }

    @Test
    fun doppelteSchriftWaechstStattAbzuschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    RezeptkarteGross(
                        titel = "Senf-Krusten-Braten",
                        meta = "4 Portionen · 658 kcal · 95 Min.",
                        onKlick = {},
                        augenbraue = "Abendessen",
                        aktion = KartenAktion("Kochen") {},
                        modifier = Modifier.width(360.dp).testTag("karte"),
                    )
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }
}
