package de.foody.server

import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

class ConflictTest {
    private fun mealSlot(id: String, recipeId: String, servings: Int, baseRev: Long?) = SyncRecord(
        id = id, type = RecordType.MEAL_SLOT, updatedAt = 1, baseRev = baseRev,
        payload = obj(MealSlotPayload("2026-10-05", "DINNER", recipeId, servings)),
    )

    private fun deletion(of: SyncRecord, baseRev: Long? = null) =
        SyncRecord(of.id, of.type, deleted = true, updatedAt = 2, baseRev = baseRev)

    @Test
    fun checkedSurvivesConcurrentAmountChange() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(shoppingList("l1"), shoppingItem("s1", "l1", checked = false, checkedChangedAt = 100)))
        val a = client.pushOk(token, listOf(shoppingItem("s1", "l1", true, 200, baseRev = 2))).results.single()
        assertEquals(PushStatus.ACCEPTED, a.status)
        val b = client.pushOk(token, listOf(shoppingItem("s1", "l1", false, 100, amount = "3", baseRev = 2))).results.single()
        assertEquals(PushStatus.MERGED, b.status)
        val payload = client.pull(token, 0).records.single { it.id == "s1" }.payload!!
        assertEquals(JsonPrimitive("3"), payload["amount"])
        assertEquals(JsonPrimitive(true), payload["checked"])
        assertEquals(JsonPrimitive(200), payload["checkedChangedAt"])
        val current = b.current!!
        assertEquals(b.rev, current.rev)
        assertEquals(payload, current.payload)
        assertNull(current.baseRev)
    }

    @Test
    fun laterUncheckWins() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(shoppingList("l1"), shoppingItem("s1", "l1", false, 100)))
        client.pushOk(token, listOf(shoppingItem("s1", "l1", true, 200, baseRev = 2)))
        val b = client.pushOk(token, listOf(shoppingItem("s1", "l1", false, 300, baseRev = 2))).results.single()
        assertEquals(PushStatus.MERGED, b.status)
        val payload = client.pull(token, 0).records.single { it.id == "s1" }.payload!!
        assertEquals(JsonPrimitive(false), payload["checked"])
        assertEquals(JsonPrimitive(300), payload["checkedChangedAt"])
    }

    @Test
    fun checkedTieGoesToChecked() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(shoppingList("l1"), shoppingItem("s1", "l1", false, 100)))
        client.pushOk(token, listOf(shoppingItem("s1", "l1", true, 200, baseRev = 2)))
        val b = client.pushOk(token, listOf(shoppingItem("s1", "l1", false, 200, baseRev = 2))).results.single()
        assertEquals(PushStatus.MERGED, b.status)
        val payload = client.pull(token, 0).records.single { it.id == "s1" }.payload!!
        assertEquals(JsonPrimitive(true), payload["checked"])
    }

    @Test
    fun deleteWinsOverStaleEdit() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val recipe = recipe("r1", listOf("i1"))
        client.pushOk(token, listOf(ingredient("i1"), recipe))
        client.pushOk(token, listOf(deletion(recipe, baseRev = 2)))
        val b = client.pushOk(token, listOf(recipe.copy(baseRev = 2))).results.single()
        assertEquals(PushStatus.MERGED, b.status)
        assertEquals(3L, b.rev)
        assertTrue(b.current!!.deleted)
        assertNull(b.current!!.payload)
        assertTrue(client.pull(token, 0).records.single { it.id == "r1" }.deleted)
    }

    @Test
    fun editAfterSeenDeleteRevives() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val recipe = recipe("r1", listOf("i1"))
        client.pushOk(token, listOf(ingredient("i1"), recipe))
        client.pushOk(token, listOf(deletion(recipe, baseRev = 2)))
        val b = client.pushOk(token, listOf(recipe.copy(baseRev = 3))).results.single()
        assertEquals(PushStatus.ACCEPTED, b.status)
        assertTrue(!client.pull(token, 0).records.single { it.id == "r1" }.deleted)
    }

    @Test
    fun duplicateIngredientNameMergesToOlderId() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("a", "Zwiebel")))
        val r = client.pushOk(token, listOf(ingredient("b", " zwiebel"))).results.single()
        assertEquals(PushStatus.MERGED, r.status)
        assertEquals("a", r.canonicalId)
        assertEquals(1L, r.rev)
        assertEquals("a", r.current!!.id)
        assertEquals(JsonPrimitive("Zwiebel"), r.current!!.payload!!["name"])
        assertEquals(listOf("a"), client.pull(token, 0).records.map { it.id })
    }

    @Test
    fun renamingIngredientToSameNameAsItselfIsNoConflict() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("a", "Zwiebel")))
        val changed = ingredient("a", "Zwiebel", mapOf("category" to "GEMUESE")).copy(baseRev = 1)
        val r = client.pushOk(token, listOf(changed)).results.single()
        assertEquals(PushStatus.ACCEPTED, r.status)
    }

    @Test
    fun deletedIngredientNameIsFree() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val a = ingredient("a", "Zwiebel")
        client.pushOk(token, listOf(a, deletion(a)))
        val r = client.pushOk(token, listOf(ingredient("b", "Zwiebel"))).results.single()
        assertEquals(PushStatus.ACCEPTED, r.status)
    }

    @Test
    fun plainLastWriterWins() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1"), recipe("r1", listOf("i1")), mealSlot("m1", "r1", 1, null)))
        val a = client.pushOk(token, listOf(mealSlot("m1", "r1", 2, baseRev = 3))).results.single()
        val b = client.pushOk(token, listOf(mealSlot("m1", "r1", 4, baseRev = 3))).results.single()
        assertEquals(PushStatus.ACCEPTED, a.status)
        assertEquals(PushStatus.ACCEPTED, b.status)
        assertEquals(JsonPrimitive(4), client.pull(token, 0).records.single { it.id == "m1" }.payload!!["servings"])
    }

    @Test
    fun mergeKeepsUnknownFields() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(shoppingList("l1"), shoppingItem("s1", "l1", false, 100)))
        client.pushOk(token, listOf(shoppingItem("s1", "l1", true, 200, baseRev = 2)))
        val b = client.pushOk(
            token,
            listOf(shoppingItem("s1", "l1", false, 100, baseRev = 2, extra = mapOf("futureField" to "x"))),
        ).results.single()
        assertEquals(PushStatus.MERGED, b.status)
        val payload = client.pull(token, 0).records.single { it.id == "s1" }.payload!!
        assertEquals(JsonPrimitive("x"), payload["futureField"])
        assertEquals(JsonPrimitive(true), payload["checked"])
    }

    @Test
    fun missingReferenceIsRejectedEvenInConflict() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(shoppingList("l1"), shoppingItem("s1", "l1", false, 100)))
        val r = client.pushOk(token, listOf(shoppingItem("s1", "gone", false, 100, baseRev = 1))).results.single()
        assertEquals(PushStatus.REJECTED, r.status)
    }
}
