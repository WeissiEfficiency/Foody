package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Anweisungen ans Bild-Sprach-Modell: Auswahlliste mit dem heutigen Plan vorne (Regel 29). */
class FotoAnweisungTest {
    private fun kontext(vararg namen: String, plan: Set<String> = emptySet()) = FotoKontext(
        rezepte = namen.mapIndexed { i, name -> FotoKontext.Eintrag("r$i", name) },
        planRezepte = plan,
        zutaten = emptyList(),
    )

    /** Die Namen der Liste in ihrer Reihenfolge, wie sie in der Anweisung stehen. */
    private fun liste(anweisung: String?): List<String> = Regex(""""((?:[^"\\]|\\.)*)"""")
        .findAll(anweisung!!.substringAfter("List: "))
        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
        .toList()

    @Test fun planZuerstDannDieUebrigenInIhrerReihenfolge() {
        val a = FotoAnweisung.auswahl(kontext("Linsencurry", "Gulasch", "Käsespätzle", "Pfannkuchen", plan = setOf("r2", "r0")))
        assertEquals(listOf("Linsencurry", "Käsespätzle", "Gulasch", "Pfannkuchen"), liste(a))
    }

    @Test fun gleicheNamenNurEinmal() {
        val a = FotoAnweisung.auswahl(kontext("Käsespätzle", "Gulasch", " käsespätzle ", plan = setOf("r2")))
        assertEquals(listOf("käsespätzle", "Gulasch"), liste(a))
    }

    @Test fun hoechstensZweihundertNamenDerPlanBleibtDrin() {
        val namen = (1..300).map { "Rezept $it" }.toTypedArray()
        val a = FotoAnweisung.auswahl(kontext(*namen, plan = setOf("r299")))
        val l = liste(a)
        assertEquals(FotoAnweisung.HOECHSTENS, l.size)
        assertEquals("Rezept 300", l.first())
        assertEquals("Rezept 199", l.last())
    }

    @Test fun ohneRezepteKeineAuswahl() {
        assertNull(FotoAnweisung.auswahl(kontext()))
    }

    @Test fun anfuehrungszeichenWerdenMaskiert() {
        val a = FotoAnweisung.auswahl(kontext("Omas \"bester\" Kuchen", "A\\B"))
        assertEquals(listOf("Omas \"bester\" Kuchen", "A\\B"), liste(a))
    }

    @Test fun antwortFormUndKeinsDavon() {
        val a = FotoAnweisung.auswahl(kontext("Linsencurry"))!!
        assertTrue("\"none\"" in a)
        assertTrue("""{"choice": string, "portions": number}""" in a)
    }

    @Test fun gerichtIstDieTrainingsanweisung() {
        assertTrue(FotoAnweisung.GERICHT.startsWith("As a food-analyzer AI"))
    }
}
