package de.foody.feature.kochen

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
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.kochen.KochenRepository
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.testing.ViewModelHalter
import de.foody.feature.kochen.timer.KochTimerSpeicher
import de.foody.domain.MeasureUnit
import kotlin.coroutines.EmptyCoroutineContext
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertTrue

/** Room im Speicher; Abfragen laufen sofort im aufrufenden Thread, damit Flows im Test ohne Warten liefern. */
fun testDatenbank(): FoodyDatenbank =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

/** 25.10.2026, 12:00 UTC – „heute“ der Tests. */
val TEST_UHR: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)
val TEST_HEUTE: LocalDate = LocalDate.of(2026, 10, 25)

/** Rezept r1 „Reistopf“ für 2 Portionen: 400 g Reis, 2 Zwiebeln; drei Schritte, der zweite mit „20 Minuten“. */
suspend fun FoodyDatenbank.reistopf(schritte: List<String> = listOf("150 g Reis waschen.", "Reis und Zwiebeln 20 Minuten garen.", "Abschmecken.")) {
    zutatDao().upsert(ZutatEntity(id = "z-reis", canonicalName = "Reis", createdAt = 1, updatedAt = 1))
    zutatDao().upsert(ZutatEntity(id = "z-zwiebel", canonicalName = "Zwiebel", createdAt = 1, updatedAt = 1))
    rezeptDao().upsert(
        RezeptEntity(
            id = "r1", name = "Reistopf", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
            notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
        ),
    )
    rezeptZutatDao().upsert(RezeptZutatEntity("l1", "r1", "z-reis", BigDecimal("400"), MeasureUnit.GRAM, 0, null, false))
    rezeptZutatDao().upsert(RezeptZutatEntity("l2", "r1", "z-zwiebel", BigDecimal("2"), MeasureUnit.PIECE, 1, null, false))
    schritte.forEachIndexed { i, s -> arbeitsschrittDao().upsert(ArbeitsschrittEntity("s$i", "r1", i, s)) }
}

suspend fun FoodyDatenbank.planHeute(id: String = "p1") =
    planEintragDao().upsert(PlanEintragEntity(id, TEST_HEUTE, "DINNER", "r1", 2, createdAt = 1, updatedAt = 1))

/** Frische Speicher (leere Prefs) und ein ViewModel; außerhalb der Komposition anlegen. */
class KochTestUmgebung(val db: FoodyDatenbank, val clock: Clock = TEST_UHR) {
    private val app = RuntimeEnvironment.getApplication()
    val sitzungen = KochSitzungSpeicher(prefs("test_kochen_sitzungen"), clock)
    val timer = KochTimerSpeicher(prefs("test_kochen_timer"), clock)
    // Ohne eigenen Rechen-Thread: der Test-Dispatcher wartet sonst nicht auf das Rezept
    val repo = RezeptRepository(db, FotoAblage(app), EmptyCoroutineContext) { clock.millis() }

    /** Hält die ViewModels; `viewModels.schliessen()` gehört in `@After` vor `db.close()` ([ViewModelHalter]). */
    val viewModels = ViewModelHalter()

    /** [gespeichert]: SavedStateHandle wie nach einem Prozessende; sonst ein neues. */
    fun vm(gespeichert: SavedStateHandle = SavedStateHandle()) = viewModels.behalten(
        KochenViewModel(gespeichert, repo, KochenRepository(db) { clock.millis() }, sitzungen, timer, clock, FotoAblage(app)),
    )

    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)

    companion object {
        /** Leert die Test-Prefs (sonst bleibt eine Sitzung aus dem vorigen Test). */
        fun leeren() = listOf("test_kochen_sitzungen", "test_kochen_timer").forEach {
            RuntimeEnvironment.getApplication().getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
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
