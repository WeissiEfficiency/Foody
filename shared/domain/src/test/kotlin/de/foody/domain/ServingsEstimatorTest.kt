package de.foody.domain

import de.foody.domain.ServingsEstimator.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServingsEstimatorTest {
    @Test fun recognizesKindFromName() {
        assertEquals(Kind.BAKED, ServingsEstimator.kindOf("Saftiger Zitronenkuchen"))
        assertEquals(Kind.BAKED, ServingsEstimator.kindOf("Einfaches Weizenbrot"))
        // „Brotsoße“ ist kein Gebäck – entscheidend ist das Wortende
        assertEquals(Kind.MAIN, ServingsEstimator.kindOf("Fränkischer Schweinebraten mit dunkler Brotsoße"))
        assertEquals(Kind.DESSERT, ServingsEstimator.kindOf("Tiramisu klassisch"))
        assertEquals(Kind.SOUP, ServingsEstimator.kindOf("Omas Kartoffelsuppe"))
    }

    @Test fun bigRoastIsNotFourServings() {
        // 2 kg Krustenbraten mit Knödeln: ≈ 3,5 kg feste Zutaten, ≈ 6700 kcal → gedeckelt bei 8
        assertEquals(8, ServingsEstimator.estimate("Bayrischer Krustenbraten", 3500.0, 6700.0))
    }

    @Test fun dryIngredientsCountByEnergy() {
        // 500 g Nudeln roh + Soße wiegen wenig, reichen aber für mehrere
        assertEquals(4, ServingsEstimator.estimate("Nudeln mit Pesto", 800.0, 2800.0))
    }

    @Test fun meatInPiecesDecides() {
        assertEquals(4, ServingsEstimator.estimate("Rindersteaks", 800.0, 1070.0, meatPieces = 4))
        // Gilt nur für Hauptgerichte
        assertEquals(17, ServingsEstimator.estimate("Zitronenkuchen", 1700.0, 6900.0, meatPieces = 3))
    }

    @Test fun cakeBecomesSlices() {
        assertEquals(17, ServingsEstimator.estimate("Saftiger Zitronenkuchen", 1700.0, 6900.0))
    }

    @Test fun tooLittleKnownKeepsDefault() {
        assertNull(ServingsEstimator.estimate("Salat", 120.0, 150.0))
    }
}
