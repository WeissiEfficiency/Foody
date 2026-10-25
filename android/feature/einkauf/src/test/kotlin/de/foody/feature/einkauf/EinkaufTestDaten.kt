package de.foody.feature.einkauf

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.einkauf.EinkaufRepository
import de.foody.core.data.einkauf.IgnorierteSchluessel
import de.foody.domain.MeasureUnit
import org.robolectric.RuntimeEnvironment
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Mittwoch, 28.10.2026 – in KW 44 (Mo 26.10.–So 1.11.). */
val MITTWOCH: LocalDate = LocalDate.of(2026, 10, 28)
val MONTAG: LocalDate = LocalDate.of(2026, 10, 26)

/** Feste Uhr am Mittwoch, 12 Uhr (UTC). */
class TestUhr(private val jetzt: Instant = MITTWOCH.atTime(12, 0).toInstant(ZoneOffset.UTC)) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = jetzt
}

/**
 * Erfundener Bestand: Zutaten Reis, Zwiebeln, Milch, Wasser; „Curry“ (c: 200 g Zwiebeln, 300 g Reis, 500 ml Wasser)
 * und „Reispfanne“ (p: 100 g Zwiebeln, 400 g Reis), beide für 2 Portionen.
 */
suspend fun FoodyDatenbank.einkaufBestand() {
    listOf(
        ZutatEntity(id = "z-reis", canonicalName = "Reis", createdAt = 1, updatedAt = 1),
        ZutatEntity(id = "z-zwiebel", canonicalName = "Zwiebeln", createdAt = 1, updatedAt = 1),
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

suspend fun FoodyDatenbank.zeile(id: String, rezeptId: String, zutatId: String, menge: String, einheit: MeasureUnit, nr: Int) =
    rezeptZutatDao().upsert(RezeptZutatEntity(id, rezeptId, zutatId, BigDecimal(menge), einheit, nr, null, false))

suspend fun FoodyDatenbank.planen(id: String, datum: LocalDate, rezeptId: String, portionen: Int = 2) =
    planEintragDao().upsert(
        PlanEintragEntity(id = id, date = datum, slotType = "ABENDESSEN", recipeId = rezeptId, servings = portionen, createdAt = 1, updatedAt = 1),
    )

fun rezept(id: String, name: String) = RezeptEntity(
    id = id, name = name, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null, notes = null,
    tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
)

fun liste(id: String, angelegt: Long, von: LocalDate? = null, bis: LocalDate? = null, name: String = id) =
    EinkaufslisteEntity(id = id, name = name, rangeStart = von, rangeEnd = bis, createdAt = angelegt, updatedAt = angelegt)

fun artikel(id: String, listeId: String, name: String, menge: String? = null, gekauft: Boolean = false, reihenfolge: Int = 0) =
    EinkaufsartikelEntity(
        id = id, listId = listeId, name = name, amount = menge?.let(::BigDecimal), unit = menge?.let { MeasureUnit.GRAM },
        checked = gekauft, manual = true, sortOrder = reihenfolge,
    )

/** Repository und ViewModel wie in der App, mit fester Uhr; ViewModel außerhalb der Komposition anlegen. */
class EinkaufTestUmgebung(val db: FoodyDatenbank, val uhr: Clock = TestUhr()) {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("einkauf", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    val ignoriert = IgnorierteSchluessel(prefs)
    val repo = EinkaufRepository(db, { uhr.millis() }, ignoriert)

    fun vm(saved: SavedStateHandle = SavedStateHandle()) = EinkaufViewModel(saved, repo, uhr)
}
