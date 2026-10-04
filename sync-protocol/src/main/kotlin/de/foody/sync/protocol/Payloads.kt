package de.foody.sync.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Feldnamen wie BackupDto in der App; Zahlen sind Strings (BigDecimal ohne Rundung).

@Serializable
data class IngredientPayload(
    val name: String,
    val category: String? = null,
    val density: String? = null,
    val pieceWeight: String? = null,
    val basis: String? = null,
    val energyKj: String? = null,
    val protein: String? = null,
    val carbs: String? = null,
    val fat: String? = null,
    val fiber: String? = null,
    val sugar: String? = null,
    val salt: String? = null,
    val source: String? = null,
)

@Serializable
data class RecipePayload(
    val name: String,
    val servings: Int,
    val prep: Int? = null,
    val cook: Int? = null,
    val photo: String? = null,
    val notes: String? = null,
    val tags: String,
    val archivedAt: Long? = null,
    val favorite: Boolean,
    val sourceUrl: String? = null,
    val rating: Int? = null,
    val lines: List<Line>,
    val steps: List<Step>,
) {
    @Serializable
    data class Line(
        val id: String,
        val ingredientId: String,
        val amount: String,
        val unit: String,
        val sortOrder: Int,
        val note: String? = null,
        val optional: Boolean,
    )

    @Serializable
    data class Step(val id: String, val position: Int, val text: String)
}

@Serializable
data class MealSlotPayload(
    val date: String,
    val slotType: String,
    val recipeId: String,
    val servings: Int,
    val cookedAt: Long? = null,
)

@Serializable
data class PantryItemPayload(
    val ingredientId: String,
    val amount: String,
    val unit: String,
    val bestBefore: String? = null,
)

@Serializable
data class ShoppingListPayload(
    val name: String,
    val start: String? = null,
    val end: String? = null,
    val version: Int,
)

@Serializable
data class ShoppingItemPayload(
    val listId: String,
    val ingredientId: String? = null,
    val name: String,
    val amount: String? = null,
    val unit: String? = null,
    val checked: Boolean,
    val checkedChangedAt: Long,
    val manual: Boolean,
    val category: String? = null,
    val sortOrder: Int,
    val note: String? = null,
    val sources: List<Source>,
) {
    @Serializable
    data class Source(
        val id: String,
        val mealSlotId: String,
        val recipeIngredientId: String,
        val recipeName: String,
        val date: String,
        val amount: String,
        val unit: String,
    )
}

/** Dekodiert [payload] in den zum Typ passenden Payload-Typ; wirft [SerializationException] bei Formfehlern. */
fun RecordType.decode(payload: JsonObject): Any {
    val json = Protocol.json
    return when (this) {
        RecordType.INGREDIENT -> json.decodeFromJsonElement(IngredientPayload.serializer(), payload)
        RecordType.RECIPE -> json.decodeFromJsonElement(RecipePayload.serializer(), payload)
        RecordType.MEAL_SLOT -> json.decodeFromJsonElement(MealSlotPayload.serializer(), payload)
        RecordType.PANTRY_ITEM -> json.decodeFromJsonElement(PantryItemPayload.serializer(), payload)
        RecordType.SHOPPING_LIST -> json.decodeFromJsonElement(ShoppingListPayload.serializer(), payload)
        RecordType.SHOPPING_ITEM -> json.decodeFromJsonElement(ShoppingItemPayload.serializer(), payload)
    }
}
