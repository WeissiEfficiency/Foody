package de.foody.feature.rezepte.liste

import de.foody.core.data.rezepte.RezeptKarte
import de.foody.domain.Diet
import de.foody.domain.Einordnung
import kotlin.test.Test
import kotlin.test.assertEquals

class ListeFilterTest {
    private fun karte(
        id: String, name: String = id, kcal: Int? = null, eiweiss: Int? = null, favorit: Boolean = false, gekocht: Int = 0,
        diaeten: Set<Diet> = emptySet(), tags: List<String> = emptyList(), zutaten: List<String> = emptyList(),
        archiviert: Boolean = false, angelegt: Long = 0,
    ) = RezeptKarte(
        id = id, name = name, bild = null, kcalJePortion = kcal, eiweissJePortion = eiweiss, zeitMinuten = null,
        favorit = favorit, gekocht = gekocht, diaeten = diaeten, einordnung = Einordnung(emptySet(), emptySet(), true, true),
        tags = tags, zutatNamen = zutaten, archiviert = archiviert, angelegt = angelegt,
    )

    private fun ids(karten: List<RezeptKarte>, f: ListeFilter) = filtern(karten, f).map { it.id }

    @Test
    fun sucheInZutaten() {
        val k = listOf(karte("a", "Pfanne", zutaten = listOf("Reis")), karte("b", "Salat", tags = listOf("Reisgericht")), karte("c", "Suppe"))
        assertEquals(listOf("a", "b"), ids(k, ListeFilter(suche = "REIS")))
    }

    @Test
    fun archivNurMitChip() {
        val k = listOf(karte("a"), karte("b", archiviert = true))
        assertEquals(listOf("a"), ids(k, ListeFilter()))
        assertEquals(listOf("b"), ids(k, ListeFilter(archiv = true)))
    }

    @Test
    fun favoritenUndOftGekocht() {
        val k = listOf(karte("a", favorit = true), karte("b", gekocht = 3), karte("c", favorit = true, gekocht = 1))
        assertEquals(listOf("a", "c"), ids(k, ListeFilter(favoriten = true)))
        assertEquals(listOf("c"), ids(k, ListeFilter(favoriten = true, oftGekocht = true)))
    }

    @Test
    fun diaetVegan() {
        val k = listOf(karte("a", diaeten = setOf(Diet.VEGETARIAN, Diet.VEGAN)), karte("b", diaeten = setOf(Diet.VEGETARIAN)))
        assertEquals(listOf("a"), ids(k, ListeFilter(diaeten = setOf(Diet.VEGAN))))
    }

    @Test
    fun sortierungKcalOhneWerteZuletzt() {
        val k = listOf(karte("a", kcal = 500), karte("b"), karte("c", kcal = 300))
        assertEquals(listOf("c", "a", "b"), ids(k, ListeFilter(sortierung = Sortierung.KCAL)))
    }

    @Test
    fun gleichstandNachName() {
        val k = listOf(karte("x", "Zander", gekocht = 2), karte("y", "Apfel", gekocht = 2), karte("z", "Birne", gekocht = 5))
        assertEquals(listOf("z", "y", "x"), ids(k, ListeFilter(sortierung = Sortierung.OFT_GEKOCHT)))
        assertEquals(listOf("y", "z", "x"), ids(k, ListeFilter()))
    }
}
