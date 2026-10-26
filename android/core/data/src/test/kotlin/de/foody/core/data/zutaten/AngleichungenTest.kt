package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** „Namen vereinheitlichen“ wie v1 `harmonizeNames`: umbenennen, bei Kollision zusammenführen, Kategorie ergänzen. */
class AngleichungenTest {
    private val c = IngredientCatalog.canonicalName("Zwiebeln")

    private fun zutat(id: String, name: String, kategorie: String? = "Obst & Gemüse") =
        ZutatEntity(id = id, canonicalName = name, category = kategorie, createdAt = 1, updatedAt = 1)

    @Test
    fun vorbedingung() = assertNotEquals("Zwiebeln", c)

    @Test
    fun pluralWirdUmbenannt() =
        assertEquals(listOf(Angleichung.Umbenennen("a", "Zwiebeln", c)), angleichungen(listOf(zutat("a", "Zwiebeln"))))

    @Test
    fun kollisionWirdZusammengefuehrt() = assertEquals(
        listOf(Angleichung.Zusammenfuehren("a", "Zwiebeln", "b", c)),
        angleichungen(listOf(zutat("a", "Zwiebeln"), zutat("b", c))),
    )

    @Test
    fun zweiSchreibweisenEineBleibt() = assertEquals(
        listOf(Angleichung.Umbenennen("a", "Zwiebeln", c), Angleichung.Zusammenfuehren("b", "zwiebeln", "a", c)),
        angleichungen(listOf(zutat("b", "zwiebeln"), zutat("a", "Zwiebeln"))),
    )

    @Test
    fun kategorieErgaenzt() {
        val kategorie = assertNotNull(IngredientCatalog.guessCategory(c))
        assertEquals(listOf(Angleichung.KategorieErgaenzen("a", c, kategorie)), angleichungen(listOf(zutat("a", c, kategorie = null))))
    }

    @Test
    fun unbekannterNameBleibt() = assertTrue(angleichungen(listOf(zutat("a", "Meine Würzmischung", kategorie = null))).isEmpty())

    @Test
    fun einheitlichLeer() = assertTrue(angleichungen(listOf(zutat("a", c), zutat("b", "Meine Würzmischung", "Öle & Gewürze"))).isEmpty())

    @Test
    fun mitgebrachteKategorieBleibt() = assertEquals(
        // „Gemüsezwiebel“ kommt vor „Zwiebel“ dran und bringt ihre Kategorie mit – nichts zu raten
        listOf(Angleichung.Zusammenfuehren("a", "Gemüsezwiebel", "b", "Zwiebel")),
        angleichungen(listOf(zutat("a", "Gemüsezwiebel", kategorie = "Eigene Abteilung"), zutat("b", "Zwiebel", kategorie = null))),
    )

    @Test
    fun angepassteZutatenZaehltZutatenNichtSchritte() = assertEquals(
        2,
        listOf(
            Angleichung.Umbenennen("a", "Zwiebeln", c),
            Angleichung.KategorieErgaenzen("a", c, "Obst & Gemüse"),
            Angleichung.Zusammenfuehren("b", "zwiebeln", "a", c),
        ).angepassteZutaten(),
    )
}
