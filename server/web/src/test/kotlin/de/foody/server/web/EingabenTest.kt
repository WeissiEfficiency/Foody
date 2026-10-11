package de.foody.server.web

import de.foody.domain.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EingabenTest {
    /** Kleinigkeiten 7: übliche ausgeschriebene Einheiten werden erkannt, nicht nur Zeichen und interne Namen. */
    @Test fun einheitenSchreibweisen() {
        val erwartet = mapOf(
            "g" to MeasureUnit.GRAM, "GRAM" to MeasureUnit.GRAM, "Gramm" to MeasureUnit.GRAM, "gr" to MeasureUnit.GRAM,
            "Kilogramm" to MeasureUnit.KILOGRAM, "Milligramm" to MeasureUnit.MILLIGRAM,
            "Liter" to MeasureUnit.LITER, "Milliliter" to MeasureUnit.MILLILITER, "Zentiliter" to MeasureUnit.CENTILITER,
            "Stück" to MeasureUnit.PIECE, "stueck" to MeasureUnit.PIECE, "Stk" to MeasureUnit.PIECE, "St." to MeasureUnit.PIECE,
            "Esslöffel" to MeasureUnit.TABLESPOON, "EL" to MeasureUnit.TABLESPOON, "Teelöffel" to MeasureUnit.TEASPOON, "tl" to MeasureUnit.TEASPOON,
            "Packung" to MeasureUnit.PACKAGE, "Pck" to MeasureUnit.PACKAGE, "Päckchen" to MeasureUnit.PACKAGE, "Dose" to MeasureUnit.CAN, "Dosen" to MeasureUnit.CAN,
        )
        for ((text, einheit) in erwartet) assertEquals(einheit, Eingaben.einheit(" $text "), text)
        assertNull(Eingaben.einheit("Eimer"))
        assertNull(Eingaben.einheit(""))
    }
}
