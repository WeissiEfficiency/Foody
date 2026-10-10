package de.foody.sync.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayloadValidatorTest {
    private val a = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val b = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val c = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    private inline fun <reified T> obj(value: T): JsonObject = Protocol.json.encodeToJsonElement(value) as JsonObject

    private fun ingredient(name: String = "Mehl") = IngredientPayload(name = name, density = "0.6", basis = "PER_100_G")

    private fun recipe(
        name: String = "Brot",
        stepText: String = "Backen",
        ingredientIds: List<String> = listOf(a, b),
        amount: String = "500",
        unit: String = "GRAM",
    ) = RecipePayload(
        name = name, servings = 4, prep = null, cook = null, photo = null, notes = null, tags = "", archivedAt = null,
        favorite = false, sourceUrl = null, rating = null,
        lines = ingredientIds.mapIndexed { i, ing -> RecipePayload.Line("line-$i", ing, amount, unit, i, null, false) },
        steps = listOf(RecipePayload.Step(b, 0, stepText)),
    )

    private inline fun <reified T> rec(type: RecordType, payload: T, id: String = c) =
        SyncRecord(id = id, type = type, updatedAt = 1, payload = obj(payload))

    private fun item(ingredientId: String? = null) = ShoppingItemPayload(
        listId = a, ingredientId = ingredientId, name = "x", amount = null, unit = null, checked = false, checkedChangedAt = 0,
        manual = true, category = null, sortOrder = 0, note = null, sources = emptyList(),
    )

    @Test
    fun photoMustBeNullOrLowercaseHex64() {
        fun check(photo: String?) = PayloadValidator.validate(rec(RecordType.RECIPE, recipe().copy(photo = photo)))
        val hash = "a".repeat(64)
        assertNull(check(null))
        assertNull(check(hash))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("A".repeat(64)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("a".repeat(63)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("a".repeat(65)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("../" + "a".repeat(61)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("file:///x.jpg"))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check(""))
    }

    @Test
    fun validRecordsPass() {
        val records = listOf(
            rec(RecordType.INGREDIENT, ingredient()),
            rec(RecordType.RECIPE, recipe()),
            rec(RecordType.MEAL_SLOT, MealSlotPayload("2026-10-04", "DINNER", a, 2, null)),
            rec(RecordType.PANTRY_ITEM, PantryItemPayload(a, "2", "KILOGRAM", "2026-12-01")),
            rec(RecordType.SHOPPING_LIST, ShoppingListPayload("Woche", "2026-10-04", null, 1)),
            rec(RecordType.SHOPPING_ITEM, item(ingredientId = b)),
        )
        for (r in records) assertNull(PayloadValidator.validate(r), r.type.name)
    }

    @Test
    fun duplicateChildIdsAreRejected() {
        val base = recipe()
        fun check(p: RecipePayload) = PayloadValidator.validate(rec(RecordType.RECIPE, p))
        val line = base.lines.first()
        assertEquals(ErrorCode.INVALID_PAYLOAD, check(base.copy(lines = listOf(line, line.copy(ingredientId = b)))))
        val step = base.steps.single()
        assertEquals(ErrorCode.INVALID_PAYLOAD, check(base.copy(steps = listOf(step, step.copy(position = 1)))))
        assertNull(check(base.copy(steps = listOf(step, step.copy(id = c, position = 1)))))

        val source = ShoppingItemPayload.Source(a, a, b, "Brot", "2026-10-04", "1", "GRAM")
        val shopping = item(ingredientId = b)
        fun checkItem(p: ShoppingItemPayload) = PayloadValidator.validate(rec(RecordType.SHOPPING_ITEM, p))
        assertEquals(ErrorCode.INVALID_PAYLOAD, checkItem(shopping.copy(sources = listOf(source, source.copy(amount = "2")))))
        assertNull(checkItem(shopping.copy(sources = listOf(source, source.copy(id = b)))))
    }

    @Test
    fun deletedRecordMustNotCarryPayload() {
        val r = rec(RecordType.INGREDIENT, ingredient()).copy(deleted = true)
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(r))
        assertNull(PayloadValidator.validate(r.copy(payload = null)))
    }

    @Test
    fun liveRecordNeedsPayload() {
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.INGREDIENT, ingredient()).copy(payload = null)))
    }

    @Test
    fun idMustBeSafeToken() {
        fun envelope(id: String) = PayloadValidator.validate(rec(RecordType.INGREDIENT, ingredient(), id = id))
        assertNull(envelope("seed-rindersteak"))
        assertNull(envelope(a))
        assertNull(envelope("a".repeat(64)))
        for (bad in listOf("../x", "", "a".repeat(65), "a/b", "a.b", "a b", "a\nb")) {
            assertEquals(ErrorCode.INVALID_PAYLOAD, envelope(bad), "id=" + bad)
        }
        assertNull(PayloadValidator.validate(rec(RecordType.RECIPE, recipe(ingredientIds = listOf("seed-rindersteak")))))
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.RECIPE, recipe(ingredientIds = listOf("../x")))))
    }

    @Test
    fun decimalLimits() {
        fun check(amount: String) = PayloadValidator.validate(rec(RecordType.RECIPE, recipe(amount = amount)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("1E999999999"))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("1".repeat(41)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("1".repeat(31)))
        assertEquals(ErrorCode.INVALID_PAYLOAD, check("abc"))
        assertNull(check("0.000001"))
    }

    @Test
    fun textLimits() {
        fun name(n: Int) = PayloadValidator.validate(rec(RecordType.RECIPE, recipe(name = "a".repeat(n))))
        fun step(n: Int) = PayloadValidator.validate(rec(RecordType.RECIPE, recipe(stepText = "a".repeat(n))))
        assertEquals(ErrorCode.INVALID_PAYLOAD, name(201))
        assertNull(name(200))
        assertEquals(ErrorCode.INVALID_PAYLOAD, step(10_001))
        assertNull(step(10_000))
    }

    @Test
    fun unknownUnitIsRejected() {
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.RECIPE, recipe(unit = "BUCKET"))))
        assertEquals(
            ErrorCode.INVALID_PAYLOAD,
            PayloadValidator.validate(rec(RecordType.PANTRY_ITEM, PantryItemPayload(a, "2", "KILOGRAM", "2026-13-01"))),
        )
    }

    @Test
    fun referencesFollowDependencyRules() {
        assertEquals(
            listOf(RecordType.INGREDIENT to a, RecordType.INGREDIENT to b),
            PayloadValidator.references(rec(RecordType.RECIPE, recipe(ingredientIds = listOf(a, b)))),
        )
        assertEquals(listOf(RecordType.SHOPPING_LIST to a), PayloadValidator.references(rec(RecordType.SHOPPING_ITEM, item())))
        assertEquals(
            listOf(RecordType.SHOPPING_LIST to a, RecordType.INGREDIENT to b),
            PayloadValidator.references(rec(RecordType.SHOPPING_ITEM, item(ingredientId = b))),
        )
        assertEquals(
            listOf(RecordType.RECIPE to a),
            PayloadValidator.references(rec(RecordType.MEAL_SLOT, MealSlotPayload("2026-10-04", "DINNER", a, 2, null))),
        )
        assertEquals(
            listOf(RecordType.INGREDIENT to a),
            PayloadValidator.references(rec(RecordType.PANTRY_ITEM, PantryItemPayload(a, "2", "GRAM", null))),
        )
    }

    @Test
    fun canonicalNameIsTrimmedLowercase() {
        assertEquals("zwiebel", PayloadValidator.canonicalName(rec(RecordType.INGREDIENT, ingredient(" Zwiebel "))))
        assertNull(PayloadValidator.canonicalName(rec(RecordType.RECIPE, recipe())))
    }

    private fun tagebuch(mahlzeit: String = "ABENDESSEN", energieKj: String? = "1464", art: String = "REZEPT") = TagebuchPayload(
        datum = "2026-10-10", mahlzeit = mahlzeit, art = art, name = "Curry", rezeptId = a, planEintragId = b,
        portionen = "1", energieKj = energieKj, eiweiss = "12.5",
    )

    @Test
    fun tagebuchGueltigUndUngueltig() {
        assertNull(PayloadValidator.validate(rec(RecordType.TAGEBUCH_EINTRAG, tagebuch())))
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.TAGEBUCH_EINTRAG, tagebuch(mahlzeit = "Brunch"))))
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.TAGEBUCH_EINTRAG, tagebuch(energieKj = "-1"))))
        assertEquals(ErrorCode.INVALID_PAYLOAD, PayloadValidator.validate(rec(RecordType.TAGEBUCH_EINTRAG, tagebuch(art = "X"))))
        // Lose Referenzen: Rezept und Plan-Eintrag dürfen fehlen, ohne dass der Datensatz zurückgehalten wird
        assertEquals(emptyList(), PayloadValidator.references(rec(RecordType.TAGEBUCH_EINTRAG, tagebuch())))
    }
}
