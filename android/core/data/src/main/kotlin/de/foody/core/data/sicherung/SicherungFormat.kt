package de.foody.core.data.sicherung

import kotlinx.serialization.Serializable

/**
 * Inhalt von `backup.json` im Format 3 (`"format": 3`). Die Feldnamen stammen aus der v1 (`BackupDto`); Sicherungen der
 * v1 selbst (`formatVersion`, mit Vorrat) liest die App seit dem Ende des Rückwegs nicht mehr (C8, Tag `v1-final`).
 * Zahlen stehen als Strings (verlustfrei), Datumswerte als ISO-8601, Aufzählungen über den Namen.
 *
 * Fotos liegen in der ZIP unter `photos/<n>.jpg`; ein Rezeptbild zeigt darauf als [FOTO_SCHEMA]`<n>.jpg`.
 */
@Serializable
internal data class SicherungDaten(
    /** Formatnummer ([FORMAT]). */
    val format: Int? = null,
    val ingredients: List<Zutat> = emptyList(),
    val recipes: List<Rezept> = emptyList(),
    val recipeIngredients: List<RezeptZutat> = emptyList(),
    val steps: List<Schritt> = emptyList(),
    val mealSlots: List<PlanEintrag> = emptyList(),
    val shoppingLists: List<Einkaufsliste> = emptyList(),
    val shoppingItems: List<Einkaufsartikel> = emptyList(),
    val shoppingSources: List<Quelle> = emptyList(),
    val tagebuch: List<Tagebuch> = emptyList(),
) {
    @Serializable
    data class Zutat(
        val id: String, val name: String, val category: String? = null, val density: String? = null,
        val pieceWeight: String? = null, val basis: String? = null, val energyKj: String? = null,
        val protein: String? = null, val carbs: String? = null, val fat: String? = null, val fiber: String? = null,
        val sugar: String? = null, val salt: String? = null, val source: String? = null,
        val createdAt: Long, val updatedAt: Long, val barcode: String? = null,
    )

    @Serializable
    data class Rezept(
        val id: String, val name: String, val servings: Int, val prep: Int? = null, val cook: Int? = null,
        val imageUri: String? = null, val notes: String? = null, val tags: String, val archivedAt: Long? = null,
        val createdAt: Long, val updatedAt: Long, val favorite: Boolean = false, val sourceUrl: String? = null,
        val rating: Int? = null, val mahlzeiten: String? = null, val gaenge: String? = null,
    )

    @Serializable
    data class RezeptZutat(
        val id: String, val recipeId: String, val ingredientId: String, val amount: String, val unit: String,
        val sortOrder: Int, val note: String? = null, val optional: Boolean,
    )

    @Serializable
    data class Schritt(val id: String, val recipeId: String, val position: Int, val text: String)

    @Serializable
    data class PlanEintrag(
        val id: String, val date: String, val slotType: String, val recipeId: String, val servings: Int,
        val cookedAt: Long? = null, val createdAt: Long, val updatedAt: Long,
    )

    @Serializable
    data class Einkaufsliste(
        val id: String, val name: String, val start: String? = null, val end: String? = null, val version: Int,
        val createdAt: Long, val updatedAt: Long,
    )

    @Serializable
    data class Einkaufsartikel(
        val id: String, val listId: String, val ingredientId: String? = null, val name: String,
        val amount: String? = null, val unit: String? = null, val checked: Boolean, val manual: Boolean,
        val category: String? = null, val sortOrder: Int, val note: String? = null,
        /** Fehlen sie, setzt der Einfüge-Trigger `updatedAt` auf jetzt. */
        val updatedAt: Long = 0, val checkedChangedAt: Long = 0,
    )

    @Serializable
    data class Quelle(
        val id: String, val itemId: String, val mealSlotId: String, val recipeIngredientId: String,
        val recipeName: String, val date: String, val amount: String, val unit: String,
    )

    @Serializable
    data class Tagebuch(
        val id: String, val datum: String, val mahlzeit: String, val art: String, val name: String,
        val rezeptId: String? = null, val planEintragId: String? = null, val portionen: String? = null,
        val zutatId: String? = null, val menge: String? = null, val einheit: String? = null,
        val energieKj: String? = null, val eiweiss: String? = null, val kohlenhydrate: String? = null,
        val fett: String? = null, val vollstaendig: Boolean = true, val createdAt: Long, val updatedAt: Long,
    )

    companion object {
        /** Formatnummer, die die V3 schreibt und liest. */
        const val FORMAT = 3

        /** Eintrag mit den Daten in der ZIP. */
        const val DATEN_EINTRAG = "backup.json"

        /** Ordner der Fotos in der ZIP. */
        const val FOTO_ORDNER = "photos/"

        /** Verweis eines Rezepts auf ein Foto in der ZIP (wie v1). */
        const val FOTO_SCHEMA = "foody-backup-photo:"

        /** Erlaubte Fotonamen in der ZIP (wie v1). */
        val FOTO_NAME = Regex("[0-9]{1,6}\\.jpg")

        // Größengrenzen wie in der v1 (keine Zip-Bombe)
        const val MAX_JSON_BYTES = 64L shl 20
        const val MAX_FOTO_BYTES = 32L shl 20
        const val MAX_GESAMT_BYTES = 2L shl 30
    }
}
