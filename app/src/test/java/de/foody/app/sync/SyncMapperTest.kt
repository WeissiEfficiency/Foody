package de.foody.app.sync

import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.RecipeIngredientEntity
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncMapperTest {
    private fun assertValid(type: RecordType, payload: Any) {
        val record = SyncRecord(id = "x", type = type, updatedAt = 1, payload = SyncMapper.toJson(payload))
        assertNull(PayloadValidator.validate(record))
    }

    private val ingredient = IngredientEntity(
        id = "i1", canonicalName = "Milch", category = "Milch", densityGPerMl = BigDecimal("1.03"),
        pieceWeightG = null, nutrientBasis = NutrientBasis.PER_100_ML, energyKj = BigDecimal("1460"),
        protein = BigDecimal("3.4"), carbs = BigDecimal("4.8"), fat = BigDecimal("0.15"),
        fiber = BigDecimal("0"), sugar = BigDecimal("4.8"), salt = BigDecimal("0.1"),
        nutrientSource = "BLS", createdAt = 10, updatedAt = 20, version = 3,
    )

    @Test fun ingredientRoundTrip() {
        val p = SyncMapper.ingredient(ingredient)
        assertEquals("1460", p.energyKj)
        assertEquals("PER_100_ML", p.basis)
        assertEquals("0.15", p.fat)
        assertEquals("Milch", p.name)
        assertValid(RecordType.INGREDIENT, p)
        val back = SyncMapper.ingredient("i1", p, 20, ingredient)
        assertEquals(ingredient, back)
        val fresh = SyncMapper.ingredient("i1", p, 20, null)
        assertEquals(ingredient.copy(createdAt = 20, version = 1), fresh)
    }

    @Test fun recipeRoundTripKeepsLocalImage() {
        val r = RecipeEntity(
            id = "r1", name = "Suppe", defaultServings = 4, prepMinutes = 10, cookMinutes = 20,
            imageUri = "file:/x.jpg", notes = "n", tags = "a,b", archivedAt = null, createdAt = 5, updatedAt = 6,
            version = 2, favorite = true, sourceUrl = "http://u", rating = 4,
        )
        val lines = listOf(
            RecipeIngredientEntity("l1", "r1", "i1", BigDecimal("1.50"), MeasureUnit.TABLESPOON, 0, "gehackt", true),
            RecipeIngredientEntity("l2", "r1", "i1", BigDecimal("200"), MeasureUnit.GRAM, 1, null, false),
        )
        val steps = listOf(InstructionStepEntity("s1", "r1", 0, "Kochen"), InstructionStepEntity("s2", "r1", 1, "Essen"))
        val p = SyncMapper.recipe(r, lines, steps)
        assertNull(p.photo)
        assertEquals(10, p.prep)
        assertValid(RecordType.RECIPE, p)
        val parts = SyncMapper.recipe("r1", p, 6, r)
        assertEquals(r, parts.recipe)
        assertEquals("file:/x.jpg", parts.recipe.imageUri)
        assertEquals(lines, parts.lines)
        assertEquals(steps, parts.steps)
    }

    @Test fun shoppingItemRoundTripKeepsCheckStamp() {
        val item = ShoppingItemEntity(
            id = "si1", listId = "sl1", ingredientId = "i1", name = "Milch", amount = BigDecimal("2"),
            unit = MeasureUnit.LITER, checked = true, manual = false, category = "Milch", sortOrder = 3,
            note = "Bio", updatedAt = 99, checkedChangedAt = 1234,
        )
        val src = ShoppingItemSourceEntity(
            "so1", "si1", "m1", "l1", "Suppe", LocalDate.of(2026, 10, 5), BigDecimal("0.5"), MeasureUnit.LITER,
        )
        val p = SyncMapper.shoppingItem(item, listOf(src))
        assertEquals(1234, p.checkedChangedAt)
        assertEquals("2026-10-05", p.sources.single().date)
        assertValid(RecordType.SHOPPING_ITEM, p)
        val parts = SyncMapper.shoppingItem("si1", p, 99, item)
        assertEquals(item, parts.item)
        assertEquals(listOf(src), parts.sources)
    }

    @Test fun datesAndUnitsUseWireFormats() {
        val slot = MealSlotEntity("m1", LocalDate.of(2026, 10, 5), "DINNER", "r1", 2, 77, 1, 2)
        val sp = SyncMapper.mealSlot(slot)
        assertEquals("2026-10-05", sp.date)
        assertValid(RecordType.MEAL_SLOT, sp)
        assertEquals(slot, SyncMapper.mealSlot("m1", sp, 2, slot))

        val pantry = PantryItemEntity("p1", "i1", BigDecimal("1.5"), MeasureUnit.TABLESPOON, LocalDate.of(2027, 1, 2), 5)
        val pp = SyncMapper.pantryItem(pantry)
        assertEquals("TABLESPOON", pp.unit)
        assertEquals("2027-01-02", pp.bestBefore)
        assertValid(RecordType.PANTRY_ITEM, pp)
        assertEquals(pantry, SyncMapper.pantryItem("p1", pp, 5, pantry))

        val list = ShoppingListEntity("sl1", "Woche", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), 2, 1, 2)
        val lp = SyncMapper.shoppingList(list)
        assertEquals(2, lp.version)
        assertEquals("2026-10-11", lp.end)
        assertValid(RecordType.SHOPPING_LIST, lp)
        assertEquals(list, SyncMapper.shoppingList("sl1", lp, 2, list))
    }

    @Test fun newEntityFromRemoteUsesUpdatedAtAsCreatedAt() {
        val p = SyncMapper.shoppingList(ShoppingListEntity("sl1", "W", null, null, 1, 1, 1))
        assertEquals(500, SyncMapper.shoppingList("sl1", p, 500, null).createdAt)
        val mp = SyncMapper.mealSlot(MealSlotEntity("m1", LocalDate.of(2026, 1, 1), "LUNCH", "r", 1, null, 1, 1))
        assertEquals(500, SyncMapper.mealSlot("m1", mp, 500, null).createdAt)
        val rp = SyncMapper.recipe(
            RecipeEntity("r", "R", 1, null, null, null, null, "", null, 1, 1), emptyList(), emptyList(),
        )
        val parts = SyncMapper.recipe("r", rp, 500, null)
        assertEquals(500, parts.recipe.createdAt)
        assertNull(parts.recipe.imageUri)
    }

    @Test fun toJsonRejectsUnknownType() {
        assertFailsWith<IllegalArgumentException> { SyncMapper.toJson("nope") }
    }
}
