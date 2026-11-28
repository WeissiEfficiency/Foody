package de.foody.feature.einstellungen

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class RasterSpaltenTest {
    @Test
    fun farbenAufDemHandyInEinerReihe() {
        // 361 dp Handy ohne Seitenränder und Karte: 297 dp
        assertEquals(4, rasterSpalten(4, 64.dp, 297.dp, 0.9f, gleicheReihen = true))
        assertEquals(4, rasterSpalten(4, 64.dp, 297.dp, 1f, gleicheReihen = true))
    }

    @Test
    fun grosseSchriftLieberZweiMalZwei() {
        assertEquals(2, rasterSpalten(4, 64.dp, 297.dp, 1.3f, gleicheReihen = true))
        assertEquals(3, rasterSpalten(4, 64.dp, 297.dp, 1.3f, gleicheReihen = false))
    }

    @Test
    fun stileWieBisher() {
        assertEquals(3, rasterSpalten(7, 88.dp, 297.dp, 1f, gleicheReihen = false))
        assertEquals(1, rasterSpalten(7, 88.dp, 50.dp, 1f, gleicheReihen = false))
    }
}
