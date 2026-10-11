package de.foody.server.web

import de.foody.domain.ConversionInfo
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Ingredient
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Naehrwerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutrientProfile
import de.foody.domain.Recipe
import de.foody.domain.RecipeIngredient
import de.foody.domain.RezeptEinordnung
import de.foody.domain.TagebuchArt
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.TagebuchPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.decode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException

internal data class WebRezept(val id: String, val rev: Long, val payload: RecipePayload, val domain: Recipe, val einordnung: Einordnung)

internal data class WebPlanEintrag(
    val id: String,
    val rev: Long,
    val datum: LocalDate,
    val mahlzeit: String,
    val rezeptId: String,
    val portionen: Int,
    val gekochtAm: Long? = null,
)

/** [geaendert] = `SyncRecord.updatedAt`; die neueste Liste hat den größten Wert. [von]/[bis] nur bei Listen aus dem Plan. */
internal data class WebEinkaufsliste(
    val id: String,
    val name: String,
    val geaendert: Long,
    val rev: Long = 0,
    val von: LocalDate? = null,
    val bis: LocalDate? = null,
)

internal data class WebVorrat(val id: String, val rev: Long, val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit)

internal data class WebEintrag(val id: String, val rev: Long, val listId: String, val payload: ShoppingItemPayload)

internal data class WebTagebuch(
    val id: String,
    val datum: LocalDate,
    val mahlzeit: Mahlzeit,
    val name: String,
    val werte: Naehrwerte,
    val rev: Long = 0,
    val art: TagebuchArt = TagebuchArt.FREI,
    val rezeptId: String? = null,
    val zutatId: String? = null,
    val portionen: BigDecimal? = null,
    val menge: BigDecimal? = null,
    val einheit: MeasureUnit? = null,
    val planEintragId: String? = null,
)

/** Zutat mit Roh-Payload – für das Formular und zum Schreiben auf dem gespeicherten Stand. */
internal data class WebZutat(val id: String, val rev: Long, val payload: IngredientPayload)

/**
 * Sync-Payloads → `domain`-Typen, mit denselben Regeln wie die App (`Mappers.kt`): So rechnet die Web-Oberfläche mit
 * denselben Zahlen. `null` bei falschem Typ oder unlesbarem Payload (z. B. Format einer neueren App) – der Datensatz
 * wird dann übersprungen statt die Seite scheitern zu lassen.
 */
internal object PayloadZuDomain {
    fun zutat(r: SyncRecord): Ingredient? = lesen(r, RecordType.INGREDIENT) { p: IngredientPayload ->
        Ingredient(
            id = r.id,
            name = p.name,
            category = p.category,
            // Packungs- und Dosengewichte sind Faustwerte je Zutat aus dem Katalog (wie in der App)
            conversion = ConversionInfo(
                p.density?.let(::BigDecimal), p.pieceWeight?.let(::BigDecimal),
                packageWeightG = IngredientCatalog.packageWeightG(p.name),
                canWeightG = IngredientCatalog.canWeightG(p.name),
            ),
            nutrients = p.basis?.let { basis ->
                NutrientProfile(
                    basis = NutrientBasis.valueOf(basis),
                    values = mapOf(
                        Nutrient.ENERGY_KJ to p.energyKj?.let(::BigDecimal),
                        Nutrient.PROTEIN_G to p.protein?.let(::BigDecimal),
                        Nutrient.CARBS_G to p.carbs?.let(::BigDecimal),
                        Nutrient.FAT_G to p.fat?.let(::BigDecimal),
                        Nutrient.FIBER_G to p.fiber?.let(::BigDecimal),
                        Nutrient.SUGAR_G to p.sugar?.let(::BigDecimal),
                        Nutrient.SALT_G to p.salt?.let(::BigDecimal),
                    ),
                    source = p.source ?: "",
                )
            },
        )
    }

    fun rezept(r: SyncRecord): WebRezept? = lesen(r, RecordType.RECIPE) { p: RecipePayload ->
        val zeilen = p.lines.sortedBy { it.sortOrder }.map {
            RecipeIngredient(it.id, it.ingredientId, BigDecimal(it.amount), MeasureUnit.valueOf(it.unit), it.optional, it.note)
        }
        val tags = p.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        WebRezept(
            id = r.id,
            rev = r.rev ?: 0,
            payload = p,
            domain = Recipe(r.id, p.name, p.servings, zeilen),
            einordnung = RezeptEinordnung.einordnen(p.name, tags, Mahlzeit.mengeAus(p.mahlzeiten), Gang.mengeAus(p.gaenge)),
        )
    }

    fun planEintrag(r: SyncRecord): WebPlanEintrag? = lesen(r, RecordType.MEAL_SLOT) { p: MealSlotPayload ->
        WebPlanEintrag(r.id, r.rev ?: 0, LocalDate.parse(p.date), p.slotType, p.recipeId, p.servings, p.cookedAt)
    }

    fun zutatRoh(r: SyncRecord): WebZutat? = lesen(r, RecordType.INGREDIENT) { p: IngredientPayload -> WebZutat(r.id, r.rev ?: 0, p) }

    fun liste(r: SyncRecord): WebEinkaufsliste? = lesen(r, RecordType.SHOPPING_LIST) { p: ShoppingListPayload ->
        WebEinkaufsliste(r.id, p.name, r.updatedAt, r.rev ?: 0, p.start?.let(LocalDate::parse), p.end?.let(LocalDate::parse))
    }

    fun vorrat(r: SyncRecord): WebVorrat? = lesen(r, RecordType.PANTRY_ITEM) { p: PantryItemPayload ->
        WebVorrat(r.id, r.rev ?: 0, p.ingredientId, BigDecimal(p.amount), MeasureUnit.valueOf(p.unit))
    }

    fun eintrag(r: SyncRecord): WebEintrag? = lesen(r, RecordType.SHOPPING_ITEM) { p: ShoppingItemPayload ->
        WebEintrag(r.id, r.rev ?: 0, p.listId, p)
    }

    fun tagebuch(r: SyncRecord): WebTagebuch? = lesen(r, RecordType.TAGEBUCH_EINTRAG) { p: TagebuchPayload ->
        WebTagebuch(
            id = r.id,
            datum = LocalDate.parse(p.datum),
            // Unbekannter Freitext gilt wie in der App als Abendessen
            mahlzeit = Mahlzeit.ausText(p.mahlzeit) ?: Mahlzeit.ABENDESSEN,
            name = p.name,
            werte = Naehrwerte(
                p.energieKj?.let(::BigDecimal), p.eiweiss?.let(::BigDecimal), p.kohlenhydrate?.let(::BigDecimal),
                p.fett?.let(::BigDecimal), p.vollstaendig,
            ),
            rev = r.rev ?: 0,
            // Unbekannte Art (neuere App) gilt als frei: dann wird beim Ändern nichts skaliert
            art = TagebuchArt.entries.firstOrNull { it.name == p.art } ?: TagebuchArt.FREI,
            rezeptId = p.rezeptId,
            zutatId = p.zutatId,
            portionen = p.portionen?.let(::BigDecimal),
            menge = p.menge?.let(::BigDecimal),
            einheit = p.einheit?.let { e -> MeasureUnit.entries.firstOrNull { it.name == e } },
            planEintragId = p.planEintragId,
        )
    }

    private inline fun <reified P, T> lesen(r: SyncRecord, typ: RecordType, umwandeln: (P) -> T): T? {
        if (r.type != typ || r.deleted) return null
        val payload = r.payload ?: return null
        return try {
            umwandeln(typ.decode(payload) as P)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null // u. a. unbekannte Enum-Namen, NumberFormatException
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
