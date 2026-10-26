package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Namenssuche für Tagebuch und „Neue Zutat“: roher, dann kanonischer Name, ohne Groß/klein. */
class ZutatSucheTest {
    private fun z(id: String, name: String) = ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)

    @Test
    fun rohOhneGrossKlein() {
        assertEquals("a", zutatMitNamen(listOf(z("b", "Nudeln"), z("a", "Reis")), "  reis ")?.id)
    }

    @Test
    fun umlaute() {
        assertEquals("a", zutatMitNamen(listOf(z("a", "Äpfel")), "äpfel")?.id)
    }

    @Test
    fun kanonisch() {
        val k = IngredientCatalog.canonicalName("Mehl")
        assertNotEquals("Mehl", k)
        assertEquals("w", zutatMitNamen(listOf(z("w", k)), "Mehl")?.id)
    }

    @Test
    fun rohVorKanonisch() {
        val k = IngredientCatalog.canonicalName("Mehl")
        assertEquals("m", zutatMitNamen(listOf(z("w", k), z("m", "mehl")), "Mehl")?.id)
    }

    @Test
    fun keinTreffer() {
        assertNull(zutatMitNamen(listOf(z("a", "Reis")), "Hafer"))
        assertNull(zutatMitNamen(listOf(z("a", "Reis")), "  "))
        assertNull(zutatMitNamen(emptyList(), "Reis"))
    }
}
