package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.NutrientBasis
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Editor-Entwurf: Texte aus der Zutat und die Prüfregeln (nur geänderte Felder). */
class ZutatEntwurfTest {
    private val leer = ZutatEntwurf.aus(ZutatEntity(id = "z", canonicalName = "Reis", createdAt = 1, updatedAt = 1))

    private fun fehler(nachher: ZutatEntwurf) = pruefen(leer, nachher)

    @Test
    fun ausStartwerten() {
        val e = ZutatEntwurf.aus(
            ZutatEntity(
                id = "seed-reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1502"),
                protein = BigDecimal("7.00"), densityGPerMl = BigDecimal("0.85"), createdAt = 1, updatedAt = 1,
            ),
        )
        assertEquals("359", e.kcal)
        assertEquals("7", e.eiweiss)
        assertEquals("0,85", e.dichte)
        assertEquals("", e.strichcode)
        assertEquals("", e.salz)
        assertEquals(NutrientBasis.PER_100_G, e.basis)
    }

    @Test
    fun nurGeaenderteGeprueft() {
        val alt = leer.copy(kcal = "5000")
        assertTrue(pruefen(alt, alt).isEmpty())
        assertEquals(mapOf(ZutatFeld.KCAL to "0 bis 1.000"), pruefen(alt, alt.copy(kcal = "1200")))
    }

    @Test
    fun nameRegeln() {
        assertEquals("Name fehlt", fehler(leer.copy(name = "  "))[ZutatFeld.NAME])
        assertEquals("Höchstens 200 Zeichen", fehler(leer.copy(name = "x".repeat(201)))[ZutatFeld.NAME])
        assertTrue(fehler(leer.copy(name = "x".repeat(200))).isEmpty())
    }

    @Test
    fun kcalRegeln() {
        for (t in listOf("-1", "1e3", "abc", "1000,1")) assertEquals("0 bis 1.000", fehler(leer.copy(kcal = t))[ZutatFeld.KCAL], t)
        for (t in listOf("0", "1000", "359,4")) assertTrue(fehler(leer.copy(kcal = t)).isEmpty(), t)
    }

    @Test
    fun naehrstoffRegeln() {
        assertEquals("0 bis 100", fehler(leer.copy(eiweiss = "100,5"))[ZutatFeld.EIWEISS])
        assertEquals("0 bis 100", fehler(leer.copy(salz = "-0,1"))[ZutatFeld.SALZ])
        assertTrue(fehler(leer.copy(eiweiss = "1,5", kohlenhydrate = "1.5", zucker = "0", fett = "100", ballaststoffe = "3")).isEmpty())
    }

    @Test
    fun dichteRegeln() {
        for (t in listOf("0", "5,1")) assertEquals("Mehr als 0, höchstens 5", fehler(leer.copy(dichte = t))[ZutatFeld.DICHTE], t)
        assertTrue(fehler(leer.copy(dichte = "5")).isEmpty())
    }

    @Test
    fun stueckgewichtRegeln() {
        assertTrue(fehler(leer.copy(stueckgewicht = "10000")).isEmpty())
        for (t in listOf("10001", "0")) {
            assertEquals("Mehr als 0, höchstens 10.000", fehler(leer.copy(stueckgewicht = t))[ZutatFeld.STUECKGEWICHT], t)
        }
    }

    @Test
    fun strichcodeRegeln() {
        for (t in listOf("1234567", "123456789012345", "4001234A")) {
            assertEquals("8 bis 14 Ziffern", fehler(leer.copy(strichcode = t))[ZutatFeld.STRICHCODE], t)
        }
        assertTrue(fehler(leer.copy(strichcode = "12345678")).isEmpty())
        assertTrue(pruefen(leer.copy(strichcode = "12345678"), leer).isEmpty())
    }

    @Test
    fun leerIstKeinWert() {
        assertTrue(pruefen(leer.copy(kcal = "100", dichte = "1"), leer).isEmpty())
        assertEquals(null, zahlAus(" "))
        assertEquals(BigDecimal("1.5"), zahlAus(" 1,5 "))
    }

    @Test
    fun nameBereinigtFasstZusammen() = assertEquals("Rote Zwiebel", nameBereinigt("  Rote   Zwiebel "))
}
