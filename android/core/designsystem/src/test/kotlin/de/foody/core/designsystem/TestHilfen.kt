package de.foody.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlin.test.assertTrue

/** Inhalt im Foody-Theme mit doppelter Schriftgröße (Systemschrift 200 %). */
@Composable
fun MitSchrift200(inhalt: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { inhalt() }
}

/**
 * Texte, die sichtbar abgeschnitten sind: zu viele Zeilen, eine mit „…“ gekürzte Zeile oder zu wenig Höhe.
 * Nicht `hasVisualOverflow`: Compose legt den Absatz über die volle Breite, der Knoten ist aber nur so breit wie der
 * Text – das gälte dort schon als Überlauf, obwohl nichts fehlt.
 */
fun ComposeTestRule.abgeschnitteneTexte(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { node ->
            val ergebnisse = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(ergebnisse)
            ergebnisse.firstOrNull()?.takeIf { it.istAbgeschnitten() }?.layoutInput?.text?.text
        }

/** Zu viele Zeilen, eine gekürzte Zeile oder ein Absatz höher als der Platz. */
fun TextLayoutResult.istAbgeschnitten(): Boolean =
    multiParagraph.didExceedMaxLines ||
        (0 until lineCount).any { isLineEllipsized(it) } ||
        multiParagraph.height > size.height + 0.5f

fun ComposeTestRule.pruefeNichtsAbgeschnitten() {
    val abgeschnitten = abgeschnitteneTexte()
    assertTrue(abgeschnitten.isEmpty(), "abgeschnitten: $abgeschnitten")
}
