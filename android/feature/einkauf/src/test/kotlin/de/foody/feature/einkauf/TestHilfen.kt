package de.foody.feature.einkauf

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertTrue

/** Room im Speicher; Abfragen laufen sofort im aufrufenden Thread, damit Flows im Test ohne Warten liefern. */
fun testDatenbank(): FoodyDatenbank =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

fun ComposeTestRule.warteAufText(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** Wie [warteAufText], aber als Teiltext (Abschnittsköpfe beginnen mit einem Emoji). */
fun ComposeTestRule.warteAufTeil(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

/** Wie in :core:designsystem: zu viele Zeilen, gekürzte Zeile oder zu wenig Höhe. */
fun ComposeTestRule.pruefeNichtsAbgeschnitten() {
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
