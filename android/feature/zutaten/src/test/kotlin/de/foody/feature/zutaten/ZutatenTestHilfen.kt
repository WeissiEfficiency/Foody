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
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.core.scan.PackungScan
import de.foody.core.scan.ProduktSuche
import de.foody.core.scan.ScanStatus
import de.foody.core.scan.StrichcodeStatus
import de.foody.core.scan.ZutatTreffer
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/** Echte Datenbank und echtes Repository; ViewModels außerhalb der Komposition anlegen. */
internal class ZutatenTestUmgebung {
    val kontext: Context = RuntimeEnvironment.getApplication()
    val db: FoodyDatenbank = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
    val repo = ZutatenRepository(db) { 10L }
    val meldungen = ZutatenMeldungen()
    val scan = TestScan()
    val uebergabe = ZutatUebergabe()

    /** Hält die ViewModels, damit [schliessen] ihre Flows beendet, bevor die Datenbank zugeht. */
    private val viewModels = ViewModelHalter()

    fun liste() = viewModels.behalten(ZutatenListeViewModel(SavedStateHandle(), repo, meldungen, scan.scan, uebergabe))

    fun editor() = viewModels.behalten(ZutatEditorViewModel(SavedStateHandle(), repo, meldungen, scan.scan, { scan.online }, uebergabe))

    /** Für `@After`: erst die ViewModels beenden, dann die Datenbank schließen ([ViewModelHalter]). */
    fun schliessen() {
        viewModels.schliessen()
        db.close()
    }

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

/** Strichcode, den [TestScan] immer liest. */
const val TEST_CODE = "4000000000000"

/**
 * Scan ohne Play-Dienste: Der Strichcode [TEST_CODE] ist immer gelesen; trägt ihn [imKatalog], kommt „im Katalog“,
 * sonst fragt er Open Food Facts ([packung], ohne: unbekannt). [online] steht für die Einstellung „Online-Produktsuche“,
 * [anfragen] zählt die Anfragen an Open Food Facts.
 */
internal class TestScan {
    @Volatile var packung: Packungswerte? = null
    @Volatile var imKatalog: ZutatTreffer? = null
    @Volatile var online: Boolean = true
    val anfragen = AtomicInteger()

    val scan = PackungScan(
        leser = { StrichcodeStatus.Gelesen(TEST_CODE) },
        tabelle = { ScanStatus.Fehler },
        suche = {
            anfragen.incrementAndGet()
            packung?.let { ProduktSuche.Antwort.Gefunden(it) } ?: ProduktSuche.Antwort.Unbekannt
        },
        katalog = { imKatalog },
        play = { true },
        online = { online },
    )
}

/** Haferflocken je 100 g aus Open Food Facts: 1502 kJ (359 kcal), Eiweiß 13,5, KH 58,7, Zucker 1,2, Fett 7. */
fun haferflocken(name: String? = "Haferflocken", basis: NutrientBasis? = NutrientBasis.PER_100_G, kj: String = "1502") = Packungswerte(
    name = name,
    basis = basis,
    werte = mapOf(
        Nutrient.ENERGY_KJ to BigDecimal(kj), Nutrient.PROTEIN_G to BigDecimal("13.5"), Nutrient.CARBS_G to BigDecimal("58.7"),
        Nutrient.SUGAR_G to BigDecimal("1.2"), Nutrient.FAT_G to BigDecimal("7"),
    ),
    strichcode = TEST_CODE,
    quelle = "Open Food Facts",
)
