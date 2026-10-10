package de.foody.sync.protocol

import kotlinx.serialization.SerializationException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Serverseitige Prüfung von Datensätzen nach den Protokoll-Grenzen (Länge, Zahlen, Einheiten, Datum, sichere ID-Token). */
object PayloadValidator {
    private const val MAX_NAME = 200
    private const val MAX_TEXT = 10_000
    private const val MAX_TAGS = 1_000
    private const val MAX_NUMBER_LENGTH = 40
    private val ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val UNITS = setOf(
        "MILLIGRAM", "GRAM", "KILOGRAM", "MILLILITER", "CENTILITER", "LITER", "TEASPOON", "TABLESPOON", "PIECE", "PACKAGE", "CAN",
    )
    private val BASES = setOf("PER_100_G", "PER_100_ML")
    private val MAHLZEITEN = setOf("FRUEHSTUECK", "MITTAGESSEN", "ABENDESSEN", "SNACK")
    private val TAGEBUCH_ARTEN = setOf("REZEPT", "ZUTAT", "FREI")

    /** Interner Abbruch bei der ersten Verletzung; ohne Stacktrace, da reiner Kontrollfluss. */
    private class Invalid : Exception(null, null, false, false)

    private fun fail(): Nothing = throw Invalid()
    private fun ensure(ok: Boolean) {
        if (!ok) fail()
    }

    /** `null` = gültig, sonst [ErrorCode.INVALID_PAYLOAD]. */
    fun validate(record: SyncRecord): ErrorCode? {
        if (!isValidId(record.id)) return ErrorCode.INVALID_PAYLOAD
        val payload = record.payload
        if (record.deleted) return if (payload == null) null else ErrorCode.INVALID_PAYLOAD
        if (payload == null) return ErrorCode.INVALID_PAYLOAD
        return try {
            check(record.type.decode(payload))
            null
        } catch (_: SerializationException) {
            ErrorCode.INVALID_PAYLOAD
        } catch (_: Invalid) {
            ErrorCode.INVALID_PAYLOAD
        }
    }

    /** Datensätze, die vorhanden sein müssen, bevor [record] angenommen werden kann (Abhängigkeitsregeln). */
    fun references(record: SyncRecord): List<Pair<RecordType, String>> {
        val payload = record.payload ?: return emptyList()
        return when (val decoded = record.type.decode(payload)) {
            is RecipePayload -> decoded.lines.map { RecordType.INGREDIENT to it.ingredientId }
            is MealSlotPayload -> listOf(RecordType.RECIPE to decoded.recipeId)
            is PantryItemPayload -> listOf(RecordType.INGREDIENT to decoded.ingredientId)
            is ShoppingItemPayload -> listOfNotNull(
                RecordType.SHOPPING_LIST to decoded.listId,
                decoded.ingredientId?.let { RecordType.INGREDIENT to it },
            )
            else -> emptyList()
        }
    }

    /** Eindeutigkeitsschlüssel einer Zutat (getrimmt, klein); für andere Typen `null`. */
    fun canonicalName(record: SyncRecord): String? {
        if (record.type != RecordType.INGREDIENT) return null
        val payload = record.payload ?: return null
        return (record.type.decode(payload) as IngredientPayload).name.trim().lowercase()
    }

    /** IDs sind UUIDs oder feste Startdaten-IDs (z. B. `seed-rindersteak`): 1-64 Zeichen aus `[A-Za-z0-9_-]`. */
    private fun isValidId(text: String) = ID_REGEX.matches(text)

    private fun check(payload: Any) {
        when (payload) {
            is IngredientPayload -> {
                name(payload.name)
                optText(payload.category, MAX_NAME)
                optText(payload.source, MAX_NAME)
                ensure(payload.basis == null || payload.basis in BASES)
                with(payload) {
                    listOf(density, pieceWeight, energyKj, protein, carbs, fat, fiber, sugar, salt).forEach { optNumber(it) }
                }
            }
            is RecipePayload -> {
                name(payload.name)
                ensure(payload.servings >= 1)
                ensure((payload.prep ?: 0) >= 0 && (payload.cook ?: 0) >= 0)
                ensure(payload.photo == null || PhotoHash.isValid(payload.photo))
                optText(payload.notes, MAX_TEXT)
                optText(payload.sourceUrl, MAX_TEXT)
                ensure(payload.tags.length <= MAX_TAGS)
                ensure(payload.rating == null || payload.rating in 1..5)
                payload.lines.forEach { line ->
                    ensure(isValidId(line.id) && isValidId(line.ingredientId))
                    number(line.amount)
                    ensure(line.unit in UNITS)
                    optText(line.note, MAX_TEXT)
                }
                ensure(payload.lines.map { it.id }.toSet().size == payload.lines.size)
                ensure(payload.steps.map { it.id }.toSet().size == payload.steps.size)
                payload.steps.forEach { step ->
                    ensure(isValidId(step.id))
                    ensure(step.text.length <= MAX_TEXT)
                }
            }
            is MealSlotPayload -> {
                date(payload.date)
                ensure(payload.slotType.isNotBlank() && payload.slotType.length <= MAX_NAME)
                ensure(isValidId(payload.recipeId))
                ensure(payload.servings >= 1)
            }
            is PantryItemPayload -> {
                ensure(isValidId(payload.ingredientId))
                number(payload.amount)
                ensure(payload.unit in UNITS)
                payload.bestBefore?.let { date(it) }
            }
            is ShoppingListPayload -> {
                name(payload.name)
                payload.start?.let { date(it) }
                payload.end?.let { date(it) }
            }
            is ShoppingItemPayload -> {
                ensure(isValidId(payload.listId))
                ensure(payload.ingredientId == null || isValidId(payload.ingredientId))
                ensure(payload.name.length <= MAX_NAME)
                optNumber(payload.amount)
                ensure(payload.unit == null || payload.unit in UNITS)
                optText(payload.category, MAX_NAME)
                optText(payload.note, MAX_TEXT)
                ensure(payload.sources.map { it.id }.toSet().size == payload.sources.size)
                payload.sources.forEach { source ->
                    ensure(isValidId(source.id) && isValidId(source.mealSlotId) && isValidId(source.recipeIngredientId))
                    ensure(source.recipeName.length <= MAX_NAME)
                    date(source.date)
                    number(source.amount)
                    ensure(source.unit in UNITS)
                }
            }
            is TagebuchPayload -> {
                date(payload.datum)
                ensure(payload.mahlzeit in MAHLZEITEN)
                ensure(payload.art in TAGEBUCH_ARTEN)
                name(payload.name)
                listOf(payload.rezeptId, payload.planEintragId, payload.zutatId).forEach { ensure(it == null || isValidId(it)) }
                ensure(payload.einheit == null || payload.einheit in UNITS)
                listOf(payload.portionen, payload.menge, payload.energieKj, payload.eiweiss, payload.kohlenhydrate, payload.fett)
                    .forEach { nonNegative(it) }
            }
            else -> fail()
        }
    }

    private fun nonNegative(text: String?) {
        if (text == null) return
        number(text)
        ensure(BigDecimal(text).signum() >= 0)
    }

    private fun name(text: String) = ensure(text.isNotBlank() && text.length <= MAX_NAME)
    private fun optText(text: String?, max: Int) = ensure(text == null || text.length <= max)
    private fun optNumber(text: String?) {
        if (text != null) number(text)
    }

    /** Wie `BackupRepository.decimal`: Länge ≤ 40, Skala −6…20, Präzision ≤ 30. */
    private fun number(text: String) {
        ensure(text.length <= MAX_NUMBER_LENGTH)
        val value = try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            fail()
        }
        ensure(value.scale() in -6..20 && value.precision() <= 30)
    }

    private fun date(text: String) {
        try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            fail()
        }
    }
}
