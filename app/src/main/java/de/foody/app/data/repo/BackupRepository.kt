package de.foody.app.data.repo

import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.domain.TagebuchArt
import de.foody.app.data.RecipePhotoStore
import androidx.core.net.toUri
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.FoodyDatabase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sicherung aller lokalen Daten. Zahlen als Strings (verlustfrei), Datumswerte als ISO-8601.
 * Seit Fotos gesichert werden, ist die Datei ein ZIP mit `backup.json` und `photos/<n>.jpg`; ein Rezeptbild
 * zeigt darin auf [PHOTO_SCHEME]`<n>.jpg`. Reine JSON-Dateien älterer Versionen lassen sich weiter einlesen.
 */
@Serializable
data class BackupDto(
    val formatVersion: Int = 1,
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val recipeIngredients: List<RecipeIngredient>,
    val steps: List<Step>,
    val mealSlots: List<Slot>,
    val pantry: List<Pantry>,
    val shoppingLists: List<ShopList>,
    val shoppingItems: List<ShopItem>,
    val shoppingSources: List<ShopSource>,
    // Seit DB v8; Standardwert hält ältere Sicherungen lesbar
    val tagebuch: List<Tagebuch> = emptyList(),
) {
    @Serializable data class Ingredient(
        val id: String, val name: String, val category: String?, val density: String?, val pieceWeight: String?,
        val basis: String?, val energyKj: String?, val protein: String?, val carbs: String?, val fat: String?,
        val fiber: String?, val sugar: String?, val salt: String?, val source: String?, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class Recipe(
        val id: String, val name: String, val servings: Int, val prep: Int?, val cook: Int?, val imageUri: String?,
        val notes: String?, val tags: String, val archivedAt: Long?, val createdAt: Long, val updatedAt: Long,
        // Seit DB v2; Standardwerte halten ältere Sicherungen lesbar
        val favorite: Boolean = false, val sourceUrl: String? = null,
        // Seit DB v3
        val rating: Int? = null,
        // Seit DB v7
        val mahlzeiten: String? = null, val gaenge: String? = null,
    )
    @Serializable data class RecipeIngredient(
        val id: String, val recipeId: String, val ingredientId: String, val amount: String, val unit: String,
        val sortOrder: Int, val note: String?, val optional: Boolean,
    )
    @Serializable data class Step(val id: String, val recipeId: String, val position: Int, val text: String)
    @Serializable data class Slot(
        val id: String, val date: String, val slotType: String, val recipeId: String, val servings: Int,
        val cookedAt: Long?, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class Pantry(
        val id: String, val ingredientId: String, val amount: String, val unit: String, val bestBefore: String?, val updatedAt: Long,
    )
    @Serializable data class ShopList(
        val id: String, val name: String, val start: String?, val end: String?, val version: Int, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class ShopItem(
        val id: String, val listId: String, val ingredientId: String?, val name: String, val amount: String?, val unit: String?,
        val checked: Boolean, val manual: Boolean, val category: String?, val sortOrder: Int,
        // Seit DB v3
        val note: String? = null,
    )
    @Serializable data class ShopSource(
        val id: String, val itemId: String, val mealSlotId: String, val recipeIngredientId: String, val recipeName: String,
        val date: String, val amount: String, val unit: String,
    )
    @Serializable data class Tagebuch(
        val id: String, val datum: String, val mahlzeit: String, val art: String, val name: String,
        val rezeptId: String? = null, val planEintragId: String? = null, val portionen: String? = null,
        val zutatId: String? = null, val menge: String? = null, val einheit: String? = null,
        val energieKj: String? = null, val eiweiss: String? = null, val kohlenhydrate: String? = null, val fett: String? = null,
        val vollstaendig: Boolean = true, val createdAt: Long, val updatedAt: Long,
    )
}

@Singleton
class BackupRepository @Inject constructor(
    private val db: FoodyDatabase,
    @param:ApplicationContext private val context: Context,
    private val photos: RecipePhotoStore,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Schreibt Daten und alle lesbaren Rezeptbilder (eigene Fotos wie Galeriebilder) in ein ZIP. */
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val dto = buildDto()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Datei nicht beschreibbar")
        ZipOutputStream(out.buffered()).use { zip ->
            val packed = mutableMapOf<String, String>()
            dto.recipes.mapNotNull { it.imageUri }.distinct().filter(photos::isAllowedImage).forEach { image ->
                val input = runCatching { context.contentResolver.openInputStream(image.toUri()) }.getOrNull() ?: return@forEach
                val name = "${packed.size + 1}.jpg"
                input.use { zip.putNextEntry(ZipEntry(PHOTO_DIR + name)); it.copyTo(zip); zip.closeEntry() }
                packed[image] = PHOTO_SCHEME + name
            }
            val withPhotos = dto.copy(recipes = dto.recipes.map { r -> r.copy(imageUri = r.imageUri?.let { packed[it] ?: it }) })
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(json.encodeToString(BackupDto.serializer(), withPhotos).toByteArray())
            zip.closeEntry()
        }
    }

    /**
     * Ersetzt alle lokalen Daten atomar durch den Inhalt der Datei (ZIP oder ältere reine JSON-Sicherung).
     * Fotos landen erst im App-Speicher, dann folgt die Datenbank; scheitert sie, werden die Fotos wieder entfernt.
     */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val staged = mutableMapOf<String, File>()
        try {
            val text = context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                input.mark(4)
                val zipped = input.read() == 'P'.code && input.read() == 'K'.code
                input.reset()
                if (zipped) readZip(input, staged) else input.readCapped(MAX_JSON_BYTES).decodeToString()
            } ?: error("Datei nicht lesbar")
            val dto = json.decodeFromString(BackupDto.serializer(), text)
            require(dto.formatVersion == 1) { "Unbekanntes Format ${dto.formatVersion}" }
            val resolved = dto.copy(recipes = dto.recipes.map { r -> r.copy(imageUri = r.imageUri?.let { resolvePhoto(it, staged) }) })
            db.withTransaction {
                db.maintenanceDao().clearAll()
                restore(resolved)
            }
        } catch (e: Exception) {
            staged.values.forEach { it.delete() }
            throw e
        }
        photos.pruneUnused()
    }

    suspend fun deleteAll() {
        db.withTransaction { db.maintenanceDao().clearAll() }
        photos.pruneUnused()
    }

    /**
     * Liest `backup.json` und legt Fotos als neue Dateien im Fotoordner ab. Nur flache Namen unter `photos/`
     * werden angenommen (kein „../“ – Zip-Slip), Größen sind begrenzt (keine Zip-Bombe).
     */
    private fun readZip(input: InputStream, staged: MutableMap<String, File>): String {
        var text: String? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                when {
                    name == DATA_ENTRY -> text = zip.readCapped(MAX_JSON_BYTES).decodeToString()
                    name.startsWith(PHOTO_DIR) && PHOTO_NAME.matches(name.removePrefix(PHOTO_DIR)) && name.removePrefix(PHOTO_DIR) !in staged -> {
                        val file = photos.newPhotoFile()
                        staged[name.removePrefix(PHOTO_DIR)] = file
                        file.outputStream().use { out -> total += zip.copyCapped(out, MAX_PHOTO_BYTES) }
                        check(total <= MAX_TOTAL_BYTES) { "Sicherung zu groß" }
                    }
                }
            }
        }
        return text ?: error("Keine backup.json in der Sicherung")
    }

    private fun resolvePhoto(image: String, staged: Map<String, File>): String? {
        if (!image.startsWith(PHOTO_SCHEME)) return image.takeIf(photos::isAllowedImage)
        return staged[image.removePrefix(PHOTO_SCHEME)]?.let(photos::storedUri)
    }

    private companion object {
        const val DATA_ENTRY = "backup.json"
        const val PHOTO_DIR = "photos/"
        const val PHOTO_SCHEME = "foody-backup-photo:"
        val PHOTO_NAME = Regex("[0-9]{1,6}\\.jpg")
        const val MAX_JSON_BYTES = 64L shl 20
        const val MAX_PHOTO_BYTES = 32L shl 20
        const val MAX_TOTAL_BYTES = 2L shl 30
    }

    private suspend fun buildDto(): BackupDto {
        val i = db.ingredientDao(); val r = db.recipeDao(); val m = db.mealPlanDao(); val p = db.pantryDao(); val s = db.shoppingDao()
        fun BigDecimal?.s() = this?.toPlainString()
        return BackupDto(
            ingredients = i.getAll().map {
                BackupDto.Ingredient(it.id, it.canonicalName, it.category, it.densityGPerMl.s(), it.pieceWeightG.s(), it.nutrientBasis?.name,
                    it.energyKj.s(), it.protein.s(), it.carbs.s(), it.fat.s(), it.fiber.s(), it.sugar.s(), it.salt.s(), it.nutrientSource, it.createdAt, it.updatedAt)
            },
            recipes = r.getAll().map {
                BackupDto.Recipe(it.id, it.name, it.defaultServings, it.prepMinutes, it.cookMinutes, it.imageUri, it.notes, it.tags, it.archivedAt, it.createdAt, it.updatedAt, it.favorite, it.sourceUrl, it.rating, it.mahlzeiten, it.gaenge)
            },
            recipeIngredients = r.getAllIngredients().map {
                BackupDto.RecipeIngredient(it.id, it.recipeId, it.ingredientId, it.amount.toPlainString(), it.unit.name, it.sortOrder, it.preparationNote, it.optional)
            },
            steps = r.getAllSteps().map { BackupDto.Step(it.id, it.recipeId, it.position, it.text) },
            mealSlots = m.getAll().map { BackupDto.Slot(it.id, it.date.toString(), it.slotType, it.recipeId, it.servings, it.cookedAt, it.createdAt, it.updatedAt) },
            pantry = p.getAll().map { BackupDto.Pantry(it.id, it.ingredientId, it.amount.toPlainString(), it.unit.name, it.bestBeforeDate?.toString(), it.updatedAt) },
            shoppingLists = s.getLists().map { BackupDto.ShopList(it.id, it.name, it.rangeStart?.toString(), it.rangeEnd?.toString(), it.generationVersion, it.createdAt, it.updatedAt) },
            shoppingItems = s.getAllItems().map {
                BackupDto.ShopItem(it.id, it.listId, it.ingredientId, it.name, it.amount.s(), it.unit?.name, it.checked, it.manual, it.category, it.sortOrder, it.note)
            },
            shoppingSources = s.getAllSources().map {
                BackupDto.ShopSource(it.id, it.shoppingItemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, it.date.toString(), it.contributedAmount.toPlainString(), it.unit.name)
            },
            tagebuch = db.tagebuchDao().getAll().map {
                BackupDto.Tagebuch(
                    it.id, it.datum.toString(), it.mahlzeit, it.art.name, it.name, it.rezeptId, it.planEintragId, it.portionen?.toPlainString(),
                    it.zutatId, it.menge?.toPlainString(), it.einheit?.name, it.energieKj?.toPlainString(), it.eiweiss?.toPlainString(),
                    it.kohlenhydrate?.toPlainString(), it.fett?.toPlainString(), it.vollstaendig, it.createdAt, it.updatedAt,
                )
            },
        )
    }

    private suspend fun restore(d: BackupDto) {
        fun String?.bd() = this?.let(::decimal)
        d.ingredients.forEach {
            db.ingredientDao().upsert(
                IngredientEntity(
                    id = it.id, canonicalName = it.name, category = it.category,
                    densityGPerMl = it.density.bd(), pieceWeightG = it.pieceWeight.bd(), nutrientBasis = it.basis?.let(NutrientBasis::valueOf),
                    energyKj = it.energyKj.bd(), protein = it.protein.bd(), carbs = it.carbs.bd(), fat = it.fat.bd(),
                    fiber = it.fiber.bd(), sugar = it.sugar.bd(), salt = it.salt.bd(), nutrientSource = it.source,
                    createdAt = it.createdAt, updatedAt = it.updatedAt,
                ),
            )
        }
        d.recipes.forEach {
            db.recipeDao().upsert(
                RecipeEntity(
                    id = it.id, name = it.name, defaultServings = it.servings, prepMinutes = it.prep, cookMinutes = it.cook,
                    imageUri = it.imageUri, notes = it.notes, tags = it.tags, archivedAt = it.archivedAt,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, favorite = it.favorite, sourceUrl = it.sourceUrl,
                    rating = it.rating, mahlzeiten = it.mahlzeiten, gaenge = it.gaenge,
                ),
            )
        }
        db.recipeDao().insertIngredients(d.recipeIngredients.map {
            RecipeIngredientEntity(it.id, it.recipeId, it.ingredientId, decimal(it.amount), MeasureUnit.valueOf(it.unit), it.sortOrder, it.note, it.optional)
        })
        db.recipeDao().insertSteps(d.steps.map { InstructionStepEntity(it.id, it.recipeId, it.position, it.text) })
        d.mealSlots.forEach {
            db.mealPlanDao().upsert(
                MealSlotEntity(
                    id = it.id, date = LocalDate.parse(it.date), slotType = Mahlzeit.ausText(it.slotType)?.name ?: it.slotType, recipeId = it.recipeId, servings = it.servings,
                    cookedAt = it.cookedAt, createdAt = it.createdAt, updatedAt = it.updatedAt,
                ),
            )
        }
        d.pantry.forEach {
            db.pantryDao().upsert(PantryItemEntity(it.id, it.ingredientId, decimal(it.amount), MeasureUnit.valueOf(it.unit), it.bestBefore?.let(LocalDate::parse), it.updatedAt))
        }
        d.shoppingLists.forEach {
            db.shoppingDao().upsertList(
                ShoppingListEntity(
                    id = it.id, name = it.name, rangeStart = it.start?.let(LocalDate::parse), rangeEnd = it.end?.let(LocalDate::parse),
                    generationVersion = it.version, createdAt = it.createdAt, updatedAt = it.updatedAt,
                ),
            )
        }
        db.shoppingDao().insertItems(d.shoppingItems.map {
            ShoppingItemEntity(
                id = it.id, listId = it.listId, ingredientId = it.ingredientId, name = it.name, amount = it.amount.bd(),
                unit = it.unit?.let(MeasureUnit::valueOf), checked = it.checked, manual = it.manual, category = it.category, sortOrder = it.sortOrder,
                note = it.note,
            )
        })
        db.shoppingDao().insertSources(d.shoppingSources.map {
            ShoppingItemSourceEntity(it.id, it.itemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, LocalDate.parse(it.date), decimal(it.amount), MeasureUnit.valueOf(it.unit))
        })
        d.tagebuch.forEach {
            db.tagebuchDao().upsert(
                TagebuchEintragEntity(
                    id = it.id, datum = LocalDate.parse(it.datum), mahlzeit = Mahlzeit.ausText(it.mahlzeit)?.name ?: it.mahlzeit,
                    art = TagebuchArt.valueOf(it.art), name = it.name, rezeptId = it.rezeptId, planEintragId = it.planEintragId,
                    portionen = it.portionen.bd(), zutatId = it.zutatId, menge = it.menge.bd(), einheit = it.einheit?.let(MeasureUnit::valueOf),
                    energieKj = it.energieKj.bd(), eiweiss = it.eiweiss.bd(), kohlenhydrate = it.kohlenhydrate.bd(), fett = it.fett.bd(),
                    vollstaendig = it.vollstaendig, createdAt = it.createdAt, updatedAt = it.updatedAt,
                ),
            )
        }
    }
}

/** Liest höchstens [limit] Bytes; mehr gilt als beschädigte oder bösartige Datei. */
private fun InputStream.readCapped(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    copyCapped(out, limit)
    return out.toByteArray()
}

private fun InputStream.copyCapped(out: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return copied
        copied += n
        check(copied <= limit) { "Eintrag zu groß" }
        out.write(buffer, 0, n)
    }
}

/**
 * Zahl aus einer Sicherung. Begrenzt Länge und Exponent: „1E999999999“ wäre ein gültiges BigDecimal,
 * würde beim Formatieren oder Umrechnen aber Speicher und Zeit fressen.
 */
internal fun decimal(text: String): BigDecimal {
    require(text.length <= 40) { "Zahl zu lang" }
    return BigDecimal(text).also { require(it.scale() in -6..20 && it.precision() <= 30) { "Zahl außerhalb des Bereichs" } }
}
