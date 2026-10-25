package de.foody.feature.woche

import de.foody.core.data.rezepte.RezeptKarte
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import kotlin.test.Test
import kotlin.test.assertEquals

class RezeptAuswahlTest {
    private fun karte(id: String, name: String, mahlzeiten: Set<Mahlzeit>, zutaten: List<String> = emptyList()) = RezeptKarte(
        id = id, name = name, bild = null, kcalJePortion = null, eiweissJePortion = null, zeitMinuten = null, favorit = false,
        gekocht = 0, diaeten = emptySet(), einordnung = Einordnung(mahlzeiten, setOf(Gang.HAUPTSPEISE), false, false),
        tags = emptyList(), zutatNamen = zutaten, archiviert = false, angelegt = 1,
    )

    private val karten = listOf(
        karte("a", "Apfelstrudel", setOf(Mahlzeit.SNACK)),
        karte("b", "Bohnencurry", setOf(Mahlzeit.ABENDESSEN), listOf("Kidneybohnen", "Reis")),
        karte("c", "Chili", setOf(Mahlzeit.ABENDESSEN, Mahlzeit.MITTAGESSEN)),
    )

    @Test
    fun passendeZuerst() {
        assertEquals(listOf("b", "c", "a"), auswahlSortiert(karten, "", Mahlzeit.ABENDESSEN).map { it.id })
        assertEquals(listOf("a", "b", "c"), auswahlSortiert(karten, "", null).map { it.id })
    }

    @Test
    fun sucheInZutaten() {
        assertEquals(listOf("b"), auswahlSortiert(karten, "reis", Mahlzeit.ABENDESSEN).map { it.id })
        assertEquals(listOf("c"), auswahlSortiert(karten, "CHI", null).map { it.id })
    }
}
