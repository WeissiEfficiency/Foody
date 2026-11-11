package de.foody.feature.rezepte.liste.stil

import de.foody.core.data.rezepte.RezeptKarte
import de.foody.domain.Einordnung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrimoireTexteTest {
    private fun karte(favorit: Boolean = false, tags: List<String> = emptyList(), zutaten: List<String> = emptyList()) = RezeptKarte(
        id = "r", name = "Ofengemüse", bild = null, kcalJePortion = null, eiweissJePortion = null, zeitMinuten = null,
        favorit = favorit, gekocht = 0, diaeten = emptySet(), einordnung = Einordnung(emptySet(), emptySet(), true, true),
        tags = tags, zutatNamen = zutaten, archiviert = false, angelegt = 0,
    )

    @Test
    fun artAusTagUndFavorit() {
        assertEquals("OFENGERICHT · FAVORIT", rezeptArt(karte(favorit = true, tags = listOf("Ofengericht", "schnell"))))
        assertEquals("FAVORIT", rezeptArt(karte(favorit = true)))
        assertEquals("", rezeptArt(karte()))
    }

    @Test
    fun zutatenSatzWieGesprochen() {
        assertNull(zutatenSatz(karte()))
        assertEquals("Mit Feta.", zutatenSatz(karte(zutaten = listOf("Feta"))))
        assertEquals("Mit Paprika, Zucchini und Feta.", zutatenSatz(karte(zutaten = listOf("Paprika", "Zucchini", "Feta", "Feta"))))
        assertEquals(
            "Mit Paprika, Zucchini, Feta, Öl und mehr.",
            zutatenSatz(karte(zutaten = listOf("Paprika", "Zucchini", "Feta", "Öl", "Salz"))),
        )
    }
}
