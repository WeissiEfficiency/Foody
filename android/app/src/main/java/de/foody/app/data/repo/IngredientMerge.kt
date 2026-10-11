package de.foody.app.data.repo

import de.foody.app.data.db.IngredientDao
import de.foody.app.data.db.IngredientEntity

/**
 * Führt [from] in [into] zusammen: Rezeptzeilen, Vorrat und Einkaufseinträge zeigen danach auf [into], [from] wird
 * gelöscht. Fehlende Nährwerte/Umrechnungsdaten des Ziels werden aus der Quelle übernommen; [into] erhält [now] als
 * Änderungszeit. Innerhalb einer Transaktion aufrufen.
 */
suspend fun mergeIngredient(dao: IngredientDao, from: IngredientEntity, into: IngredientEntity, now: Long) {
    dao.repointRecipeLines(from.id, into.id)
    dao.repointPantry(from.id, into.id)
    dao.repointShoppingItems(from.id, into.id)
    dao.delete(from.id)
    dao.upsert(into.fillFrom(from).copy(updatedAt = now))
}

/** Übernimmt Nährwerte und Umrechnungsdaten aus [other], wo dieser Eintrag keine hat. */
fun IngredientEntity.fillFrom(other: IngredientEntity): IngredientEntity {
    val withNutrients = if (nutrientBasis == null && other.nutrientBasis != null) {
        copy(
            nutrientBasis = other.nutrientBasis, energyKj = other.energyKj, protein = other.protein, carbs = other.carbs,
            fat = other.fat, fiber = other.fiber, sugar = other.sugar, salt = other.salt, nutrientSource = other.nutrientSource,
        )
    } else {
        this
    }
    return withNutrients.copy(
        category = category ?: other.category,
        densityGPerMl = densityGPerMl ?: other.densityGPerMl,
        pieceWeightG = pieceWeightG ?: other.pieceWeightG,
        barcode = barcode ?: other.barcode,
    )
}
