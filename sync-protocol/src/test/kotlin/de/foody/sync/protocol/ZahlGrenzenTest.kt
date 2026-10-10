package de.foody.sync.protocol

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Eine Quelle für Zahlgrenzen: Sync-Validator, Sicherung und Eingabefelder nutzen dieselbe Prüfung. */
class ZahlGrenzenTest {
    @Test fun gueltigeZahlen() {
        assertEquals(0, BigDecimal("12.5").compareTo(ZahlGrenzen.lesen("12.5")))
        assertTrue(ZahlGrenzen.imRahmen(BigDecimal("0.00000000000000000001")))
    }

    @Test fun zuLangOderZuExtremWirdAbgelehnt() {
        assertNull(ZahlGrenzen.lesen("1".repeat(41)))
        assertNull(ZahlGrenzen.lesen("1E999999999"))
        assertNull(ZahlGrenzen.lesen("abc"))
        assertFalse(ZahlGrenzen.imRahmen(BigDecimal("1E+7")))
        assertFalse(ZahlGrenzen.imRahmen(BigDecimal("0.000000000000000000001")))
    }
}
