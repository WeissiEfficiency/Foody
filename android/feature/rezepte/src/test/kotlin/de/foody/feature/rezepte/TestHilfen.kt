package de.foody.feature.rezepte

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.room.Room
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.einkauf.EinkaufRepository
import de.foody.core.data.einkauf.IgnorierteSchluessel
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertTrue

/** Room im Speicher; Abfragen laufen sofort im aufrufenden Thread, damit Flows im Test ohne Warten liefern. */
fun testDatenbank(): FoodyDatenbank =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

/** Reis (1500 kJ/100 g) und Rezept r1 „Reispfanne“ (400 g Reis, 2 Portionen, 30 Min.). */
suspend fun FoodyDatenbank.bestand() {
    zutatDao().upsert(
        ZutatEntity(
            id = "z-reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1500"),
            protein = BigDecimal("7"), carbs = BigDecimal("78"), fat = BigDecimal("1"), createdAt = 1, updatedAt = 1,
        ),
    )
    rezeptDao().upsert(
        RezeptEntity(
            id = "r1", name = "Reispfanne", defaultServings = 2, prepMinutes = 10, cookMinutes = 20, imageUri = null,
            notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
        ),
    )
    rezeptZutatDao().upsert(RezeptZutatEntity("l1", "r1", "z-reis", BigDecimal("400"), MeasureUnit.GRAM, 0, null, false))
}

suspend fun FoodyDatenbank.planen(id: String, datum: LocalDate, slot: String, portionen: Int = 2) =
    planEintragDao().upsert(PlanEintragEntity(id, datum, slot, "r1", portionen, createdAt = 1, updatedAt = 1))

suspend fun FoodyDatenbank.einkauf(vararg namen: String) {
    einkaufslisteDao().upsert(EinkaufslisteEntity(id = "l1", name = "Woche", createdAt = 1, updatedAt = 5))
    namen.forEachIndexed { i, n ->
        einkaufsartikelDao().upsert(
            EinkaufsartikelEntity(id = "a$i", listId = "l1", name = n, amount = BigDecimal("500"), unit = MeasureUnit.GRAM, sortOrder = i),
        )
    }
}

fun ComposeTestRule.warteAufText(text: String) =
    waitUntil(5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

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

/** Einkauf-Repository der Testdatenbank (eigene Prefs-Datei, damit Tests sich nicht stören). */
fun FoodyDatenbank.einkauf(): EinkaufRepository {
    val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("einkauf-test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    return EinkaufRepository(this, { 1_000L }, IgnorierteSchluessel(prefs))
}
