package de.foody.core.data.heute

import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import java.math.BigDecimal
import java.time.LocalDate

/** Kleiner Bestand für Repository-Tests: Reis (1500 kJ/100 g), Rezept r1 mit 400 g Reis für 2 Portionen. */
internal suspend fun FoodyDatenbank.bestand() {
    zutatDao().upsert(
        ZutatEntity(
            id = "z-reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G,
            energyKj = BigDecimal("1500"), protein = BigDecimal("7"), carbs = BigDecimal("78"), fat = BigDecimal("1"),
            createdAt = 1, updatedAt = 1,
        ),
    )
    rezeptDao().upsert(rezept("r1", "Reispfanne"))
    rezeptZutatDao().upsert(RezeptZutatEntity("l1", "r1", "z-reis", BigDecimal("400"), MeasureUnit.GRAM, 0, null, false))
}

internal fun rezept(id: String, name: String, archiviert: Long? = null) = RezeptEntity(
    id = id, name = name, defaultServings = 2, prepMinutes = 10, cookMinutes = 20, imageUri = null, notes = null,
    tags = "", archivedAt = archiviert, createdAt = 1, updatedAt = 1,
)

internal fun plan(id: String, datum: LocalDate, slot: String, rezeptId: String, portionen: Int = 2) =
    PlanEintragEntity(id = id, date = datum, slotType = slot, recipeId = rezeptId, servings = portionen, createdAt = 1, updatedAt = 1)

internal fun liste(id: String, angelegt: Long, geaendert: Long = angelegt) =
    EinkaufslisteEntity(id = id, name = id, createdAt = angelegt, updatedAt = geaendert)

internal fun artikel(id: String, listeId: String, name: String, erledigt: Boolean = false, reihenfolge: Int = 0) =
    EinkaufsartikelEntity(
        id = id, listId = listeId, name = name, amount = BigDecimal("500"), unit = MeasureUnit.GRAM,
        checked = erledigt, sortOrder = reihenfolge,
    )
