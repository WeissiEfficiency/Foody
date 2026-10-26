package de.foody.feature.zutaten

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import kotlin.test.assertTrue

/** Echte Datenbank und echtes Repository; ViewModels außerhalb der Komposition anlegen. */
internal class ZutatenTestUmgebung {
    val kontext: Context = RuntimeEnvironment.getApplication()
    val db: FoodyDatenbank = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
    val repo = ZutatenRepository(db) { 10L }
    val meldungen = ZutatenMeldungen()

    fun liste() = ZutatenListeViewModel(repo, meldungen)

    fun editor() = ZutatEditorViewModel(repo, meldungen)

    fun zutat(id: String, name: String, aendern: (ZutatEntity) -> ZutatEntity = { it }): ZutatEntity = runBlocking {
        aendern(ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)).also { db.zutatDao().upsert(it) }
    }

    /** Rezept [name] mit je einer Zeile auf die [zutaten]. */
    fun rezept(id: String, name: String, vararg zutaten: String) = runBlocking {
        db.rezeptDao().upsert(
            RezeptEntity(
                id = id, name = name, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        zutaten.forEachIndexed { i, z ->
            db.rezeptZutatDao().upsert(RezeptZutatEntity("$id-$i", id, z, BigDecimal.ONE, MeasureUnit.GRAM, i, null, false))
        }
    }
}

/** Wartet (bis 10 s) auf eine Bedingung, die andere Threads erfüllen. */
internal fun warte(bedingung: () -> Boolean) = runBlocking {
    withContext(Dispatchers.Default) { withTimeout(10_000) { while (!bedingung()) delay(10) } }
}

/** Wartet, bis [text] verschwunden ist (die Abfrage lässt dabei die Hauptschleife laufen). */
fun ComposeTestRule.warteBisWeg(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.warteAufText(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.warteAufTeil(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

/**
 * Prüft, dass [knoten] mindestens [LEISTE_FREI] über dem unteren Bildschirmrand endet – darunter liegt auf dem Handy die
 * schwebende Navigationsleiste der App (wie die Snackbars der anderen Seiten).
 */
fun ComposeTestRule.pruefeUeberLeiste(knoten: androidx.compose.ui.test.SemanticsNodeInteraction) {
    val unten = knoten.fetchSemanticsNode().boundsInRoot.bottom
    val hoehe = onRoot().fetchSemanticsNode().boundsInRoot.bottom
    val frei = with(density) { LEISTE_FREI.toPx() }
    assertTrue(hoehe - unten >= frei - 0.5f, "nur ${hoehe - unten} px über dem Rand, nötig $frei px")
}

val LEISTE_FREI = 88.dp

/** Inhalt mit doppelter Schriftgröße (Systemschrift 200 %). */
@Composable
fun MitSchrift200(inhalt: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { inhalt() }
}

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
