package de.foody.server

import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.ErrorDto
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushRequest
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

// Gemeinsame Bausteine für die Sync-Tests (Datensatz-Builder und Push-/Pull-Aufrufe).

internal inline fun <reified T> obj(value: T): JsonObject = Protocol.json.encodeToJsonElement(value) as JsonObject

internal fun ingredient(id: String, name: String = "Mehl", extra: Map<String, String> = emptyMap()) =
    SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 1,
        payload = JsonObject(obj(IngredientPayload(name = name)) + extra.mapValues { JsonPrimitive(it.value) }),
    )

internal fun recipe(id: String, ingredientIds: List<String>, amount: String = "100") = SyncRecord(
    id = id, type = RecordType.RECIPE, updatedAt = 1,
    payload = obj(
        RecipePayload(
            name = "Brot", servings = 4, tags = "", favorite = false,
            lines = ingredientIds.mapIndexed { i, ing ->
                RecipePayload.Line("line-$id-$i", ing, amount, "GRAM", i, null, false)
            },
            steps = listOf(RecipePayload.Step("step-$id", 0, "Backen")),
        ),
    ),
)

internal fun shoppingList(id: String) = SyncRecord(
    id = id, type = RecordType.SHOPPING_LIST, updatedAt = 1, payload = obj(ShoppingListPayload("Woche", null, null, 1)),
)

internal fun shoppingItem(
    id: String,
    listId: String,
    checked: Boolean,
    checkedChangedAt: Long,
    amount: String? = null,
    baseRev: Long? = null,
    extra: Map<String, String> = emptyMap(),
) = SyncRecord(
    id = id, type = RecordType.SHOPPING_ITEM, updatedAt = 1, baseRev = baseRev,
    payload = JsonObject(
        obj(
            ShoppingItemPayload(
                listId = listId, name = "x", amount = amount, checked = checked, checkedChangedAt = checkedChangedAt,
                manual = true, sortOrder = 0, sources = emptyList(),
            ),
        ) + extra.mapValues { JsonPrimitive(it.value) },
    ),
)

internal suspend fun HttpClient.push(token: String, records: List<SyncRecord>): HttpResponse =
    post("/api/v1/sync/push") {
        protocol()
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(Protocol.json.encodeToString(PushRequest.serializer(), PushRequest(records)))
    }

internal suspend fun HttpClient.pushOk(token: String, records: List<SyncRecord>): PushResponse {
    val response = push(token, records)
    assertEquals(HttpStatusCode.OK, response.status)
    return Protocol.json.decodeFromString(PushResponse.serializer(), response.bodyAsText())
}

internal suspend fun HttpClient.pullRaw(token: String, query: String): HttpResponse =
    get("/api/v1/sync/pull$query") { protocol(); bearerAuth(token) }

internal suspend fun HttpClient.pull(token: String, since: Long, limit: Int? = null): PullResponse {
    val response = pullRaw(token, "?since=$since" + (limit?.let { "&limit=$it" } ?: ""))
    assertEquals(HttpStatusCode.OK, response.status)
    return Protocol.json.decodeFromString(PullResponse.serializer(), response.bodyAsText())
}

internal suspend fun HttpResponse.errorCode(): ErrorCode =
    Protocol.json.decodeFromString(ErrorDto.serializer(), bodyAsText()).code

