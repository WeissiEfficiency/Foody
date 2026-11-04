package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlsSucheTest {
    private fun l(code: String, name: String) = BlsLebensmittel(code, name, mapOf(Nutrient.ENERGY_KJ to BigDecimal("1000")))

    private val alle = listOf(
        l("B511000", "Weizenbrötchen"),
        l("B516400", "Weizenbrötchen mit Mohn"),
        l("B8A2000", "Brötchen glutenfrei"),
        l("B723000", "Laugengebäck mit Hagelsalz"),
        l("B723500", "Laugengebäck mit Käse"),
        l("D064000", "Salzbrezeln/Salzstangen (Laugendauergebäck)"),
        l("X061410", "Laugenbrezel mit Butter"),
        l("W233000", "Fleischkäse einfach, fein/Original Bayerischer Leberkäse fein"),
        l("W256000", "Leberkäse fettreduziert"),
        l("U025100", "Schwein Frikadelle, roh"),
        l("Y036122", "Frikadellen gebraten"),
        l("W224032", "Münchner Weißwurst gargezogen"),
        l("G311100", "Blumenkohl roh"),
        l("E606032", "Semmelknödel gekocht"),
    )

    private fun codes(eingabe: String) = BlsSuche.suchen(alle, eingabe).map { it.lebensmittel.code }

    @Test fun regionalerNameFindetDenBevorzugtenEintragZuerst() {
        assertEquals("B723000", codes("Breze").first())
        assertEquals("W233000", codes("Leberkas").first())
        assertEquals("Y036122", codes("Fleischpflanzerl").first())
        assertEquals("B511000", codes("semmel").first())
        assertEquals("W224032", codes("Weisswurst").first())
    }

    @Test fun angefangenesWortGiltSchonFuerRegionaleNamen() {
        // Beim Tippen: „brez“ soll die Breze oben haben, nicht ein Wort, das „brez“ nur enthält
        assertEquals("B723000", codes("brez").first())
        assertEquals("Y036122", codes("Fleischpfl").first())
        assertEquals("B511000", codes("Sem").first())
        // Zwei Zeichen sind zu wenig dafür
        assertTrue("B723000" !in codes("br"))
    }

    @Test fun derOriginalbegriffSuchtWeiterMit() {
        // „Semmel“ findet Brötchen und trotzdem den Semmelknödel; „Brezel“ auch die Salzbrezeln
        assertTrue("E606032" in codes("Semmel"))
        assertTrue("D064000" in codes("Brezel"))
    }

    @Test fun bevorzugterTrefferBringtNameUndStueckgewichtMit() {
        val breze = BlsSuche.suchen(alle, "brezn").first()
        assertEquals("Breze", breze.vorschlagName)
        assertEquals(BigDecimal("80"), breze.stueckG)
        // Ohne regionalen Namen: BLS-Name, kein Stückgewicht
        val mohn = BlsSuche.suchen(alle, "Mohn").single()
        assertEquals("Weizenbrötchen mit Mohn", mohn.vorschlagName)
        assertNull(mohn.stueckG)
    }

    @Test fun zusammengesetzterNameLiefertBeideTeile() {
        val treffer = codes("Leberkassemmel")
        assertEquals("W233000", treffer.first())
        assertTrue("B511000" in treffer)
        assertEquals(listOf("Y036122", "B511000"), codes("Fleischpflanzerlsemmel").filter { it in setOf("Y036122", "B511000") })
        assertTrue("B511000" in codes("Leberkäsesemmel"))
    }

    @Test fun mehrereWoerterMuessenAlleVorkommen() {
        assertEquals(listOf("X061410"), codes("Breze mit Butter"))
        assertEquals(listOf("X061410"), codes("Butterbrezn"))
        assertEquals(listOf("G311100"), codes("Karfiol"))
    }

    @Test fun namenDieMitDemWortBeginnenKommenVorTeilwoertern() {
        // „Brötchen glutenfrei“ beginnt mit dem Suchwort, „Weizenbrötchen mit Mohn“ enthält es nur
        val treffer = codes("Brötchen")
        assertTrue(treffer.indexOf("B8A2000") < treffer.indexOf("B516400"))
    }

    @Test fun zuKurzOderLeerFindetNichts() {
        assertEquals(emptyList(), codes(""))
        assertEquals(emptyList(), codes(" b "))
        assertEquals(emptyList(), codes("Drachenfrucht"))
    }

    @Test fun hoechstensMaxTreffer() {
        assertEquals(1, BlsSuche.suchen(alle, "Brötchen", max = 1).size)
    }
}
