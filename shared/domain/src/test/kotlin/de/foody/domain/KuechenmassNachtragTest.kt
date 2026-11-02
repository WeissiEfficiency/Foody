package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Früher importierte Zeilen mit Küchenmaßen bekommen Gramm bzw. Milliliter wie ein neuer Import (Regel 30). */
class KuechenmassNachtragTest {
    private fun bd(s: String) = BigDecimal(s)

    private fun nachtrag(menge: String, einheit: MeasureUnit, hinweis: String?) =
        KuechenmassNachtrag.umstellen(bd(menge), einheit, hinweis)

    @Test fun priseNachBedarfWirdGramm() {
        val z = nachtrag("0", MeasureUnit.PIECE, "1 Prise(n)")!!
        assertEquals(0, bd("0.5").compareTo(z.menge))
        assertEquals(MeasureUnit.GRAM, z.einheit)
        assertEquals("1 Prise(n)", z.hinweis)
        assertEquals(0, bd("1").compareTo(nachtrag("0", MeasureUnit.PIECE, "2 Prisen, frisch gerieben")!!.menge))
        assertEquals(0, bd("0.5").compareTo(nachtrag("0", MeasureUnit.PIECE, "Prise")!!.menge))
    }

    @Test fun gruppeBleibtVorne() {
        val z = nachtrag("0", MeasureUnit.PIECE, "Für die Soße · 1 Prise")!!
        assertEquals(0, bd("0.5").compareTo(z.menge))
        assertEquals("Für die Soße · 1 Prise", z.hinweis)
    }

    @Test fun kuechenmassAlsStueckwort() {
        // Alter Import: „2 Msp.“ → 2 Stück mit Hinweis „Msp.“
        val msp = nachtrag("2", MeasureUnit.PIECE, "Msp.")!!
        assertEquals(0, bd("1").compareTo(msp.menge))
        assertEquals(MeasureUnit.GRAM, msp.einheit)
        assertEquals("2 Msp.", msp.hinweis)
        val tasse = nachtrag("2", MeasureUnit.PIECE, "Für den Teig · Tasse/n, gekochter abgekühlt")!!
        assertEquals(0, bd("300").compareTo(tasse.menge))
        assertEquals(MeasureUnit.MILLILITER, tasse.einheit)
        assertEquals("Für den Teig · 2 Tasse/n, gekochter abgekühlt", tasse.hinweis)
    }

    @Test fun beutelWirdPackung() {
        val z = nachtrag("1", MeasureUnit.PIECE, "Tüte/n, oder 1/2 Würfel frische Hefe")!!
        assertEquals(0, bd("1").compareTo(z.menge))
        assertEquals(MeasureUnit.PACKAGE, z.einheit)
        assertEquals("oder 1/2 Würfel frische Hefe", z.hinweis)
        assertNull(nachtrag("1", MeasureUnit.PIECE, "Beutel")!!.hinweis)
    }

    @Test fun anderesBleibt() {
        assertNull(nachtrag("0", MeasureUnit.PIECE, "etwas"), "nach Bedarf bleibt nach Bedarf")
        assertNull(nachtrag("0", MeasureUnit.PIECE, null))
        assertNull(nachtrag("2", MeasureUnit.PIECE, "große"))
        assertNull(nachtrag("1", MeasureUnit.PIECE, "Zehe/n"))
        // Schon umgestellt: nichts mehr zu tun
        assertNull(nachtrag("0.5", MeasureUnit.GRAM, "1 Prise(n)"))
        assertNull(nachtrag("1", MeasureUnit.PACKAGE, null))
    }
}
