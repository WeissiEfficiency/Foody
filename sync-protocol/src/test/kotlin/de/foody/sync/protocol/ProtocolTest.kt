package de.foody.sync.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTest {
    private val json = Protocol.json
    private val id1 = "11111111-1111-4111-8111-111111111111"
    private val id2 = "22222222-2222-4222-8222-222222222222"

    private inline fun <reified T> obj(value: T): JsonObject = json.encodeToJsonElement(value) as JsonObject

    @Test
    fun recordTypesSerializeAsWireNames() {
        assertEquals("\"meal_slot\"", json.encodeToString(RecordType.serializer(), RecordType.MEAL_SLOT))
    }

    @Test
    fun recordTypeOrderIsDependencyOrder() {
        assertEquals(
            listOf("ingredient", "recipe", "meal_slot", "pantry_item", "shopping_list", "shopping_item"),
            RecordType.entries.map { it.wire },
        )
    }

    @Test
    fun pushResponseRoundTrips() {
        val current = SyncRecord(id = id2, type = RecordType.INGREDIENT, updatedAt = 5, rev = 3, payload = buildJsonObject { put("name", "Mehl") })
        val response = PushResponse(
            listOf(
                PushResult(id1, RecordType.RECIPE, PushStatus.ACCEPTED, rev = 7),
                PushResult(id2, RecordType.INGREDIENT, PushStatus.MERGED, canonicalId = id1, current = current),
                PushResult(id1, RecordType.MEAL_SLOT, PushStatus.REJECTED, code = ErrorCode.MISSING_REFERENCE),
            ),
        )
        val text = json.encodeToString(PushResponse.serializer(), response)
        assertEquals(response, json.decodeFromString(PushResponse.serializer(), text))
        assertTrue("\"status\":\"merged\"" in text)
        assertTrue("\"code\":\"missing_reference\"" in text)
    }

    @Test
    fun everyPayloadRoundTripsThroughDecode() {
        val samples: Map<RecordType, Any> = mapOf(
            RecordType.INGREDIENT to IngredientPayload(name = "Mehl", density = "0.6", basis = "PER_100_G", energyKj = "1500"),
            RecordType.RECIPE to RecipePayload(
                name = "Brot", servings = 4, prep = 10, cook = null, photo = null, notes = "n", tags = "a,b", archivedAt = null,
                favorite = true, sourceUrl = null, rating = 4,
                lines = listOf(RecipePayload.Line(id1, id2, "500", "GRAM", 0, null, false)),
                steps = listOf(RecipePayload.Step(id2, 0, "Backen")),
            ),
            RecordType.MEAL_SLOT to MealSlotPayload("2026-10-04", "DINNER", id1, 2, null),
            RecordType.PANTRY_ITEM to PantryItemPayload(id1, "2", "KILOGRAM", "2026-12-01"),
            RecordType.SHOPPING_LIST to ShoppingListPayload("Woche", "2026-10-04", "2026-10-10", 1),
            RecordType.SHOPPING_ITEM to ShoppingItemPayload(
                listId = id1, ingredientId = id2, name = "Mehl", amount = "1", unit = "KILOGRAM", checked = false, checkedChangedAt = 0,
                manual = false, category = null, sortOrder = 1, note = null,
                sources = listOf(ShoppingItemPayload.Source(id1, id2, id1, "Brot", "2026-10-04", "1", "KILOGRAM")),
            ),
        )
        assertEquals(RecordType.entries.toSet(), samples.keys)
        for ((type, expected) in samples) {
            val encoded = when (expected) {
                is IngredientPayload -> obj(expected)
                is RecipePayload -> obj(expected)
                is MealSlotPayload -> obj(expected)
                is PantryItemPayload -> obj(expected)
                is ShoppingListPayload -> obj(expected)
                is ShoppingItemPayload -> obj(expected)
                else -> error("unerwartet")
            }
            assertEquals(expected, type.decode(encoded), type.name)
        }
    }

    @Test
    fun unknownFieldsAreIgnoredOnDecode() {
        val payload = buildJsonObject {
            put("name", "Mehl")
            put("futureField", JsonPrimitive(1))
        }
        assertEquals(IngredientPayload(name = "Mehl"), RecordType.INGREDIENT.decode(payload))
    }
}
