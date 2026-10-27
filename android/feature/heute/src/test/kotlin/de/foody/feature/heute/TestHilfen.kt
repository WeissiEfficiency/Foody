package de.foody.feature.heute

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
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ProduktSuche
import de.foody.core.scan.ScanStatus
import de.foody.core.scan.StrichcodeStatus
import de.foody.core.scan.ZutatTreffer
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
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

/** Haferflocken je 100 g aus Open Food Facts: 1502 kJ (359 kcal), Eiweiß 13,5, KH 58,7, Zucker 1,2, Fett 7. */
fun haferflocken(name: String? = "Haferflocken", basis: NutrientBasis? = NutrientBasis.PER_100_G) = Packungswerte(
    name = name,
    basis = basis,
    werte = mapOf(
        Nutrient.ENERGY_KJ to BigDecimal("1502"), Nutrient.PROTEIN_G to BigDecimal("13.5"), Nutrient.CARBS_G to BigDecimal("58.7"),
        Nutrient.SUGAR_G to BigDecimal("1.2"), Nutrient.FAT_G to BigDecimal("7"),
    ),
    strichcode = "4000000000000",
    quelle = "Open Food Facts",
)

/**
 * Scan ohne Play-Dienste: Der Strichcode ist immer gelesen; trägt ihn [imKatalog], kommt „im Katalog“, sonst [packung]
 * (ohne: unbekannt).
 */
fun testScan(packung: Packungswerte? = null, imKatalog: ZutatTreffer? = null) = PackungScan(
    leser = { StrichcodeStatus.Gelesen("4000000000000") },
    tabelle = { ScanStatus.Fehler },
    suche = { packung?.let { ProduktSuche.Antwort.Gefunden(it) } ?: ProduktSuche.Antwort.Unbekannt },
    katalog = { imKatalog },
    play = { true },
    online = { true },
)
