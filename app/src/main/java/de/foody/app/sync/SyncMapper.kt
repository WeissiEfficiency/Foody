package de.foody.app.sync

import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.sync.protocol.TagebuchPayload
import de.foody.domain.TagebuchArt
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.RecipeIngredientEntity
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.time.LocalDate

/** Zerlegtes Rezept: Kopfdatensatz samt Zeilen und Schritten. */
data class RecipeParts(
    val recipe: RecipeEntity,
    val lines: List<RecipeIngredientEntity>,
    val steps: List<InstructionStepEntity>,
    /** Hash eines Fotos, das der Server-Datensatz hat, lokal aber noch fehlt (-> `sync_photo_wanted`); sonst `null`. */
    val wantedPhoto: String? = null,
)

/** Zerlegter Einkaufsartikel: Artikel samt Herkunftszeilen. */
data class ShoppingItemParts(val item: ShoppingItemEntity, val sources: List<ShoppingItemSourceEntity>)

/**
 * Abbildung zwischen Room-Entities und Sync-Payloads (Wire-Formate wie `BackupRepository`:
 * Zahlen als `toPlainString()`, Datumswerte ISO-8601, Enums über `name`).
 *
 * `version` und `createdAt` sind rein lokal: Sie stammen beim Anwenden aus `existing`,
 * sonst `version = 1` und `createdAt = updatedAt`. Felder ohne Payload-Gegenstück
 * bleiben aus `existing` erhalten; das Rezeptfoto `imageUri` folgt der Foto-Regel an `recipe(...)`.
 */
object SyncMapper {
    private fun BigDecimal.wire() = toPlainString()

    // --- Zutat ---

    fun ingredient(e: IngredientEntity) = IngredientPayload(
        name = e.canonicalName,
        category = e.category,
        density = e.densityGPerMl?.wire(),
        pieceWeight = e.pieceWeightG?.wire(),
        basis = e.nutrientBasis?.name,
        energyKj = e.energyKj?.wire(),
        protein = e.protein?.wire(),
        carbs = e.carbs?.wire(),
        fat = e.fat?.wire(),
        fiber = e.fiber?.wire(),
        sugar = e.sugar?.wire(),
        salt = e.salt?.wire(),
        source = e.nutrientSource,
        barcode = e.barcode,
    )

    fun ingredient(id: String, p: IngredientPayload, updatedAt: Long, existing: IngredientEntity?) = IngredientEntity(
        id = id,
        canonicalName = p.name,
        category = p.category,
        densityGPerMl = p.density?.let(::BigDecimal),
        pieceWeightG = p.pieceWeight?.let(::BigDecimal),
        nutrientBasis = p.basis?.let { NutrientBasis.valueOf(it) },
        energyKj = p.energyKj?.let(::BigDecimal),
        protein = p.protein?.let(::BigDecimal),
        carbs = p.carbs?.let(::BigDecimal),
        fat = p.fat?.let(::BigDecimal),
        fiber = p.fiber?.let(::BigDecimal),
        sugar = p.sugar?.let(::BigDecimal),
        salt = p.salt?.let(::BigDecimal),
        nutrientSource = p.source,
        barcode = p.barcode,
        createdAt = existing?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
        version = existing?.version ?: 1,
    )

    // --- Rezept ---

    /** [photo] ist der Hash des eigenen Fotos (`PhotoIndex.hashOf`), `null` ohne eigenes Foto. */
    fun recipe(
        r: RecipeEntity,
        lines: List<RecipeIngredientEntity>,
        steps: List<InstructionStepEntity>,
        photo: String?,
    ) = RecipePayload(
        name = r.name,
        servings = r.defaultServings,
        prep = r.prepMinutes,
        cook = r.cookMinutes,
        photo = photo,
        notes = r.notes,
        tags = r.tags,
        archivedAt = r.archivedAt,
        favorite = r.favorite,
        sourceUrl = r.sourceUrl,
        rating = r.rating,
        mahlzeiten = r.mahlzeiten,
        gaenge = r.gaenge,
        lines = lines.map {
            RecipePayload.Line(
                id = it.id,
                ingredientId = it.ingredientId,
                amount = it.amount.wire(),
                unit = it.unit.name,
                sortOrder = it.sortOrder,
                note = it.preparationNote,
                optional = it.optional,
            )
        },
        steps = steps.map { RecipePayload.Step(id = it.id, position = it.position, text = it.text) },
    )

    /**
     * Foto-Regel (rein; die Nachschlagen übernimmt der Aufrufer): [knownPhotoUri] ist der lokale Link zu `p.photo`,
     * falls diese Datei hier schon vorliegt; [existingIsOwnPhoto] sagt, ob `existing.imageUri` ein eigenes Foto ist.
     * - `p.photo == null`: ein fremder Link (`content:`) bleibt, ein eigenes Foto wird entfernt.
     * - Foto bekannt: `imageUri = knownPhotoUri`.
     * - Foto unbekannt: `imageUri` bleibt, [RecipeParts.wantedPhoto] = `p.photo`.
     */
    fun recipe(
        id: String,
        p: RecipePayload,
        updatedAt: Long,
        existing: RecipeEntity?,
        knownPhotoUri: String? = null,
        existingIsOwnPhoto: Boolean = false,
    ): RecipeParts {
        val keep = existing?.imageUri
        val imageUri = when {
            p.photo == null -> if (existingIsOwnPhoto) null else keep
            knownPhotoUri != null -> knownPhotoUri
            else -> keep
        }
        val wanted = p.photo?.takeIf { knownPhotoUri == null }
        return recipeParts(id, p, updatedAt, existing, imageUri, wanted)
    }

    private fun recipeParts(
        id: String,
        p: RecipePayload,
        updatedAt: Long,
        existing: RecipeEntity?,
        imageUri: String?,
        wantedPhoto: String?,
    ) = RecipeParts(
        recipe = RecipeEntity(
            id = id,
            name = p.name,
            defaultServings = p.servings,
            prepMinutes = p.prep,
            cookMinutes = p.cook,
            imageUri = imageUri,
            notes = p.notes,
            tags = p.tags,
            archivedAt = p.archivedAt,
            createdAt = existing?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
            version = existing?.version ?: 1,
            favorite = p.favorite,
            sourceUrl = p.sourceUrl,
            rating = p.rating,
            mahlzeiten = p.mahlzeiten,
            gaenge = p.gaenge,
        ),
        lines = p.lines.map {
            RecipeIngredientEntity(
                id = it.id,
                recipeId = id,
                ingredientId = it.ingredientId,
                amount = BigDecimal(it.amount),
                unit = MeasureUnit.valueOf(it.unit),
                sortOrder = it.sortOrder,
                preparationNote = it.note,
                optional = it.optional,
            )
        },
        steps = p.steps.map { InstructionStepEntity(id = it.id, recipeId = id, position = it.position, text = it.text) },
        wantedPhoto = wantedPhoto,
    )

    // --- Essensplan ---

    fun mealSlot(e: MealSlotEntity) = MealSlotPayload(
        date = e.date.toString(),
        slotType = e.slotType,
        recipeId = e.recipeId,
        servings = e.servings,
        cookedAt = e.cookedAt,
    )

    fun mealSlot(id: String, p: MealSlotPayload, updatedAt: Long, existing: MealSlotEntity?) = MealSlotEntity(
        id = id,
        date = LocalDate.parse(p.date),
        // Ältere Geräte senden noch Freitext („Abendessen“): auf den festen Schlüssel abbilden
        slotType = Mahlzeit.ausText(p.slotType)?.name ?: p.slotType,
        recipeId = p.recipeId,
        servings = p.servings,
        cookedAt = p.cookedAt,
        createdAt = existing?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
    )

    // --- Vorrat ---

    fun pantryItem(e: PantryItemEntity) = PantryItemPayload(
        ingredientId = e.ingredientId,
        amount = e.amount.wire(),
        unit = e.unit.name,
        bestBefore = e.bestBeforeDate?.toString(),
    )

    @Suppress("UNUSED_PARAMETER")
    fun pantryItem(id: String, p: PantryItemPayload, updatedAt: Long, existing: PantryItemEntity?) = PantryItemEntity(
        id = id,
        ingredientId = p.ingredientId,
        amount = BigDecimal(p.amount),
        unit = MeasureUnit.valueOf(p.unit),
        bestBeforeDate = p.bestBefore?.let(LocalDate::parse),
        updatedAt = updatedAt,
    )

    // --- Einkaufsliste ---

    fun shoppingList(e: ShoppingListEntity) = ShoppingListPayload(
        name = e.name,
        start = e.rangeStart?.toString(),
        end = e.rangeEnd?.toString(),
        version = e.generationVersion,
    )

    fun shoppingList(id: String, p: ShoppingListPayload, updatedAt: Long, existing: ShoppingListEntity?) =
        ShoppingListEntity(
            id = id,
            name = p.name,
            rangeStart = p.start?.let(LocalDate::parse),
            rangeEnd = p.end?.let(LocalDate::parse),
            generationVersion = p.version,
            createdAt = existing?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
        )

    // --- Einkaufsartikel ---

    fun shoppingItem(i: ShoppingItemEntity, sources: List<ShoppingItemSourceEntity>) = ShoppingItemPayload(
        listId = i.listId,
        ingredientId = i.ingredientId,
        name = i.name,
        amount = i.amount?.wire(),
        unit = i.unit?.name,
        checked = i.checked,
        checkedChangedAt = i.checkedChangedAt,
        manual = i.manual,
        category = i.category,
        sortOrder = i.sortOrder,
        note = i.note,
        sources = sources.map {
            ShoppingItemPayload.Source(
                id = it.id,
                mealSlotId = it.mealSlotId,
                recipeIngredientId = it.recipeIngredientId,
                recipeName = it.recipeName,
                date = it.date.toString(),
                amount = it.contributedAmount.wire(),
                unit = it.unit.name,
            )
        },
    )

    @Suppress("UNUSED_PARAMETER")
    fun shoppingItem(id: String, p: ShoppingItemPayload, updatedAt: Long, existing: ShoppingItemEntity?) =
        ShoppingItemParts(
            item = ShoppingItemEntity(
                id = id,
                listId = p.listId,
                ingredientId = p.ingredientId,
                name = p.name,
                amount = p.amount?.let(::BigDecimal),
                unit = p.unit?.let { MeasureUnit.valueOf(it) },
                checked = p.checked,
                manual = p.manual,
                category = p.category,
                sortOrder = p.sortOrder,
                note = p.note,
                updatedAt = updatedAt,
                checkedChangedAt = p.checkedChangedAt,
            ),
            sources = p.sources.map {
                ShoppingItemSourceEntity(
                    id = it.id,
                    shoppingItemId = id,
                    mealSlotId = it.mealSlotId,
                    recipeIngredientId = it.recipeIngredientId,
                    recipeName = it.recipeName,
                    date = LocalDate.parse(it.date),
                    contributedAmount = BigDecimal(it.amount),
                    unit = MeasureUnit.valueOf(it.unit),
                )
            },
        )

    /** Serialisiert ein Payload über [Protocol.json]; unbekannte Typen sind ein Programmierfehler. */
    fun toJson(payload: Any): JsonObject {
        val json = Protocol.json
        val element = when (payload) {
            is IngredientPayload -> json.encodeToJsonElement(IngredientPayload.serializer(), payload)
            is RecipePayload -> json.encodeToJsonElement(RecipePayload.serializer(), payload)
            is MealSlotPayload -> json.encodeToJsonElement(MealSlotPayload.serializer(), payload)
            is PantryItemPayload -> json.encodeToJsonElement(PantryItemPayload.serializer(), payload)
            is ShoppingListPayload -> json.encodeToJsonElement(ShoppingListPayload.serializer(), payload)
            is ShoppingItemPayload -> json.encodeToJsonElement(ShoppingItemPayload.serializer(), payload)
            is TagebuchPayload -> json.encodeToJsonElement(TagebuchPayload.serializer(), payload)
            else -> throw IllegalArgumentException("Unbekannter Payload-Typ: ${payload::class.simpleName}")
        }
        return element as JsonObject
    }

    // --- Tagebuch ---

    fun tagebuch(e: TagebuchEintragEntity) = TagebuchPayload(
        datum = e.datum.toString(),
        mahlzeit = e.mahlzeit,
        art = e.art.name,
        name = e.name,
        rezeptId = e.rezeptId,
        planEintragId = e.planEintragId,
        portionen = e.portionen?.toPlainString(),
        zutatId = e.zutatId,
        menge = e.menge?.toPlainString(),
        einheit = e.einheit?.name,
        energieKj = e.energieKj?.toPlainString(),
        eiweiss = e.eiweiss?.toPlainString(),
        kohlenhydrate = e.kohlenhydrate?.toPlainString(),
        fett = e.fett?.toPlainString(),
        vollstaendig = e.vollstaendig,
    )

    fun tagebuch(id: String, p: TagebuchPayload, updatedAt: Long, existing: TagebuchEintragEntity?) = TagebuchEintragEntity(
        id = id,
        datum = LocalDate.parse(p.datum),
        mahlzeit = p.mahlzeit,
        art = TagebuchArt.valueOf(p.art),
        name = p.name,
        rezeptId = p.rezeptId,
        planEintragId = p.planEintragId,
        portionen = p.portionen?.let(::BigDecimal),
        zutatId = p.zutatId,
        menge = p.menge?.let(::BigDecimal),
        einheit = p.einheit?.let(MeasureUnit::valueOf),
        energieKj = p.energieKj?.let(::BigDecimal),
        eiweiss = p.eiweiss?.let(::BigDecimal),
        kohlenhydrate = p.kohlenhydrate?.let(::BigDecimal),
        fett = p.fett?.let(::BigDecimal),
        vollstaendig = p.vollstaendig,
        createdAt = existing?.createdAt ?: updatedAt,
        updatedAt = updatedAt,
    )
}
