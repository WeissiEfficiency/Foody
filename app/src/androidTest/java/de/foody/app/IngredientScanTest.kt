package de.foody.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.IngredientEntity
import de.foody.app.scan.OpenFoodFacts
import de.foody.app.scan.Packung
import de.foody.app.scan.PackungScan
import de.foody.app.scan.ProduktSuche
import de.foody.app.scan.ScanStatus
import de.foody.app.scan.StrichcodeStatus
import de.foody.app.ui.ingredients.IngredientDialog
import de.foody.app.ui.theme.FoodyTheme
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals

/** Zutaten-Dialog: Scan füllt die Felder vor, markiert sie und merkt sich Strichcode und Quelle. */
@RunWith(AndroidJUnit4::class)
class IngredientScanTest {
    @get:Rule val compose = createComposeRule()

    private val code = "4006040002031"
    private val packung = Packung(
        "Naturjoghurt", NutrientBasis.PER_100_G,
        mapOf(Nutrient.ENERGY_KJ to BigDecimal(1234), Nutrient.FAT_G to BigDecimal("3.5")), code, OpenFoodFacts.QUELLE,
    )
    private val scan = PackungScan(
        leser = { StrichcodeStatus.Gelesen(code) },
        tabelle = { ScanStatus.Fehler },
        suche = { ProduktSuche.Antwort.Gefunden(packung) },
        katalog = { null },
        play = { true },
        online = { true },
    )

    @Test fun strichcodeFuelltFelderUndMerktSichCode() {
        var gespeichert: IngredientEntity? = null
        compose.setContent {
            FoodyTheme { IngredientDialog(initial = null, scan = scan, onDismiss = {}, onSave = { gespeichert = it }, onDelete = null, onMerge = null, onImKatalog = {}) }
        }
        compose.onNodeWithText("Von Packung scannen").performScrollTo().performClick()
        compose.onNodeWithText("Strichcode scannen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Von der Packung übernommen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Naturjoghurt")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("1234")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("3,5")).assertExists()
        compose.onNodeWithText("Speichern").performClick()
        val e = gespeichert!!
        assertEquals(code, e.barcode)
        assertEquals(OpenFoodFacts.QUELLE, e.nutrientSource)
        assertEquals(0, BigDecimal(1234).compareTo(e.energyKj))
        assertEquals("Naturjoghurt", e.canonicalName)
    }
}
