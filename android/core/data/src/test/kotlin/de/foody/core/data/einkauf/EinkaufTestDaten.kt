package de.foody.core.data.einkauf

import android.content.Context
import androidx.room.Room
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.MeasureUnit
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Montag der Testwoche (KW 44/2026). */
internal val MONTAG: LocalDate = LocalDate.of(2026, 10, 26)
internal val MITTWOCH: LocalDate = MONTAG.plusDays(2)

/** Feste Uhr der Tests: Mittwoch, 28.10.2026, 12 Uhr. */
internal val JETZT: Long = MITTWOCH.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun testDatenbank(): FoodyDatenbank =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()

internal fun ignoriert(): IgnorierteSchluessel {
    val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("einkauf", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    return IgnorierteSchluessel(prefs)
}

/**
 * Erfundener Bestand: Zutaten Reis, Zwiebeln (80 g je Stück), Milch, Wasser; „Curry“ (c: 200 g Zwiebeln, 300 g Reis,
 * 500 ml Wasser) und „Reispfanne“ (p: 100 g Zwiebeln, 400 g Reis), beide für 2 Portionen.
 */
internal suspend fun FoodyDatenbank.einkaufBestand() {
    listOf(
        ZutatEntity(id = "z-reis", canonicalName = "Reis", createdAt = 1, updatedAt = 1),
        ZutatEntity(id = "z-zwiebel", canonicalName = "Zwiebeln", pieceWeightG = BigDecimal("80"), createdAt = 1, updatedAt = 1),
        ZutatEntity(id = "z-milch", canonicalName = "Milch", createdAt = 1, updatedAt = 1),
        ZutatEntity(id = "z-wasser", canonicalName = "Wasser", createdAt = 1, updatedAt = 1),
    ).forEach { zutatDao().upsert(it) }
    rezeptDao().upsert(rezept("c", "Curry"))
    rezeptDao().upsert(rezept("p", "Reispfanne"))
    zeile("c1", "c", "z-zwiebel", "200", MeasureUnit.GRAM, 0)
    zeile("c2", "c", "z-reis", "300", MeasureUnit.GRAM, 1)
    zeile("c3", "c", "z-wasser", "500", MeasureUnit.MILLILITER, 2)
    zeile("p1", "p", "z-zwiebel", "100", MeasureUnit.GRAM, 0)
    zeile("p2", "p", "z-reis", "400", MeasureUnit.GRAM, 1)
}

internal suspend fun FoodyDatenbank.zeile(id: String, rezeptId: String, zutatId: String, menge: String, einheit: MeasureUnit, nr: Int) =
    rezeptZutatDao().upsert(RezeptZutatEntity(id, rezeptId, zutatId, BigDecimal(menge), einheit, nr, null, false))

internal suspend fun FoodyDatenbank.planen(id: String, datum: LocalDate, rezeptId: String, portionen: Int = 2) =
    planEintragDao().upsert(
        PlanEintragEntity(id = id, date = datum, slotType = "ABENDESSEN", recipeId = rezeptId, servings = portionen, createdAt = 1, updatedAt = 1),
    )

internal fun rezept(id: String, name: String) = RezeptEntity(
    id = id, name = name, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null, notes = null,
    tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
)

internal fun liste(id: String, angelegt: Long, von: LocalDate? = null, bis: LocalDate? = null, name: String = id) =
    EinkaufslisteEntity(id = id, name = name, rangeStart = von, rangeEnd = bis, createdAt = angelegt, updatedAt = angelegt)

internal fun artikel(
    id: String,
    listeId: String,
    name: String,
    menge: String? = null,
    einheit: MeasureUnit? = null,
    gekauft: Boolean = false,
    manuell: Boolean = true,
    zutatId: String? = null,
    reihenfolge: Int = 0,
    gekauftUm: Long = 0,
    kategorie: String? = null,
) = EinkaufsartikelEntity(
    id = id, listId = listeId, ingredientId = zutatId, name = name, amount = menge?.let(::BigDecimal), unit = einheit,
    checked = gekauft, manual = manuell, category = kategorie, sortOrder = reihenfolge, checkedChangedAt = gekauftUm,
)

internal fun quelle(id: String, artikelId: String, rezeptName: String, datum: LocalDate, menge: String, einheit: MeasureUnit = MeasureUnit.GRAM) =
    EinkaufsartikelQuelleEntity(id, artikelId, "slot-$id", "zeile-$id", rezeptName, datum, BigDecimal(menge), einheit)
