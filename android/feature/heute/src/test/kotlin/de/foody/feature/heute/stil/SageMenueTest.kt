package de.foody.feature.heute.stil

import de.foody.domain.Mahlzeit
import de.foody.feature.heute.beispielZustand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SageMenueTest {
    @Test
    fun beispielTagInDerReihenfolgeDesEntwurfs() {
        val m = sageMenue(beispielZustand())
        assertEquals(
            listOf(SageZiel.KOCHEN, SageZiel.GEGESSEN, SageZiel.EINTRAGEN, SageZiel.CHRONIK, SageZiel.EINKAUF),
            m.map { it.ziel },
        )
        val kochen = m.first()
        assertEquals("ABENDMAHL BEREITEN", kochen.titel)
        assertEquals("Ofengemüse mit Feta und Kräuterquark · 540 kcal · 45 Min.", kochen.zeile)
        assertEquals("r1", kochen.rezeptId)
        assertEquals(2, kochen.portionen)
        // TalkBack liest die normalen Begriffe
        assertEquals("Kochen: Ofengemüse mit Feta und Kräuterquark", kochen.beschreibung)
    }

    @Test
    fun eintragenNenntDieErsteLeereMahlzeit() {
        val e = sageMenue(beispielZustand()).first { it.ziel == SageZiel.EINTRAGEN }
        assertEquals("Das Zwischenmahl ist noch leer.", e.zeile)
        assertEquals(Mahlzeit.SNACK, e.mahlzeit)
    }

    @Test
    fun chronikZeigtEintraegeMitKcal() {
        val c = sageMenue(beispielZustand()).first { it.ziel == SageZiel.CHRONIK }
        assertEquals("Haferflocken mit Apfel 380 · Linsensuppe 480", c.zeile)
    }

    @Test
    fun ohnePlanUndEinkaufNurEintragenUndChronik() {
        val z = beispielZustand().let { b ->
            b.copy(
                naechste = null, einkauf = null,
                tagebuch = b.tagebuch.copy(mahlzeiten = b.tagebuch.mahlzeiten.map { it.copy(eintraege = emptyList(), vorschlaege = emptyList()) }),
            )
        }
        val m = sageMenue(z)
        assertEquals(listOf(SageZiel.EINTRAGEN, SageZiel.CHRONIK), m.map { it.ziel })
        assertEquals("Das Morgenmahl ist noch leer.", m[0].zeile)
        assertEquals("Noch nichts verzeichnet.", m[1].zeile)
    }

    @Test
    fun allesEingetragenOhneMahlzeit() {
        val z = beispielZustand().let { b ->
            b.copy(tagebuch = b.tagebuch.copy(mahlzeiten = b.tagebuch.mahlzeiten.filter { it.eintraege.isNotEmpty() }))
        }
        val e = sageMenue(z).first { it.ziel == SageZiel.EINTRAGEN }
        assertNull(e.mahlzeit)
        assertTrue(e.zeile.startsWith("Jedes Mahl"))
    }
}
