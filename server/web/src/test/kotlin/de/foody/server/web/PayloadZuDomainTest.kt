package de.foody.server.web

import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.TagebuchPayload
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** Payloads → domain, mit denselben Regeln wie die App (`IngredientEntity.toDomain()` usw.). */
class PayloadZuDomainTest {
    private inline fun <reified T> record(id: String, type: RecordType, payload: T, rev: Long = 1, updatedAt: Long = 10) =
        SyncRecord(id = id, type = type, rev = rev, updatedAt = updatedAt, payload = Protocol.json.encodeToJsonElement(payload) as JsonObject)

    private fun bd(s: String) = BigDecimal(s)

    @Test fun zutatWieInDerApp() {
        val z = assertNotNull(PayloadZuDomain.zutat(record("dt", RecordType.INGREDIENT, IngredientPayload(
            name = "Dosentomaten", category = "Konserven", density = "1.0", basis = "PER_100_G", energyKj = "90", protein = "1.2",
        ))))
        assertEquals("Dosentomaten", z.name)
        assertEquals(NutrientBasis.PER_100_G, z.nutrients!!.basis)
        assertEquals(0, bd("90").compareTo(z.nutrients!!.values[Nutrient.ENERGY_KJ]))
        assertEquals(0, bd("1.0").compareTo(z.conversion.densityGPerMl))
        assertNotNull(z.conversion.canWeightG, "Dosengewicht aus dem IngredientCatalog wie in der App")
        assertNull(PayloadZuDomain.zutat(record("x", RecordType.INGREDIENT, IngredientPayload(name = "Salz"))).let { it?.nutrients })
    }

    @Test fun rezeptMitZeilenUndSchritten() {
        val r = assertNotNull(PayloadZuDomain.rezept(record("r", RecordType.RECIPE, RecipePayload(
            name = "Curry", servings = 2, tags = "Abend, schnell", favorite = false, mahlzeiten = "ABENDESSEN",
            lines = listOf(
                RecipePayload.Line("l2", "salz", "5", "GRAM", 1, null, true),
                RecipePayload.Line("l1", "reis", "200", "GRAM", 0, "gewaschen", false),
            ),
            steps = listOf(RecipePayload.Step("s1", 0, "Kochen")),
        ), rev = 7)))
        assertEquals(7, r.rev)
        assertEquals(listOf("l1", "l2"), r.domain.ingredients.map { it.id }, "nach sortOrder")
        assertEquals(MeasureUnit.GRAM, r.domain.ingredients.first().unit)
        assertTrue(r.domain.ingredients.last().optional)
        assertEquals(setOf(Mahlzeit.ABENDESSEN), r.einordnung.mahlzeiten)
    }

    @Test fun planEintrag() {
        val p = assertNotNull(PayloadZuDomain.planEintrag(record("p", RecordType.MEAL_SLOT, MealSlotPayload("2026-10-12", "ABENDESSEN", "r", 3))))
        assertEquals(LocalDate.of(2026, 10, 12), p.datum)
        assertEquals(3, p.portionen)
    }

    @Test fun einkaufslisteUndEintrag() {
        val l = assertNotNull(PayloadZuDomain.liste(record("L", RecordType.SHOPPING_LIST, ShoppingListPayload("Woche", version = 1), updatedAt = 99)))
        assertEquals(99, l.geaendert)
        val e = assertNotNull(PayloadZuDomain.eintrag(record("e", RecordType.SHOPPING_ITEM, ShoppingItemPayload(
            listId = "L", name = "Milch", checked = false, checkedChangedAt = 0, manual = true, sortOrder = 0, sources = emptyList(),
        ), rev = 4)))
        assertEquals("L", e.listId)
        assertEquals(4, e.rev)
    }

    @Test fun tagebuchEintrag() {
        val t = assertNotNull(PayloadZuDomain.tagebuch(record("t", RecordType.TAGEBUCH_EINTRAG, TagebuchPayload(
            datum = "2026-10-11", mahlzeit = "Abendbrot", art = "FREI", name = "Brot", energieKj = "418.4",
        ))))
        assertEquals(Mahlzeit.ABENDESSEN, t.mahlzeit, "unbekannter Freitext → Abendessen wie in der App")
        assertEquals(100, t.werte.kcal)
    }

    @Test fun unbekanntesFormatWirdUebersprungen() {
        val kaputt = SyncRecord(id = "r", type = RecordType.RECIPE, rev = 1, updatedAt = 1, payload = buildJsonObject {
            put("name", JsonPrimitive("X")); put("servings", JsonPrimitive(2)); put("tags", JsonPrimitive("")); put("favorite", JsonPrimitive(false))
            put("lines", Protocol.json.encodeToJsonElement(listOf(RecipePayload.Line("l", "i", "1", "PRISE_NEU", 0, null, false))))
            put("steps", Protocol.json.encodeToJsonElement(emptyList<RecipePayload.Step>()))
        })
        assertNull(PayloadZuDomain.rezept(kaputt))
        assertNull(PayloadZuDomain.rezept(record("z", RecordType.INGREDIENT, IngredientPayload(name = "Salz"))), "falscher Typ")
    }
}
