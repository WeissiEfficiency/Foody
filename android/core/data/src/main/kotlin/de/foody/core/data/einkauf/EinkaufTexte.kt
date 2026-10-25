package de.foody.core.data.einkauf

import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.domain.ShoppingCatalog
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** „300 g“, „1,5 kg“; `null` ohne Menge oder Einheit. */
internal fun mengeText(amount: BigDecimal?, unit: MeasureUnit?): String? =
    if (amount == null || unit == null || amount.signum() <= 0) null else QuantityFormatter.format(Quantity.of(amount, unit))

/** „200 g Curry (Mo) + 100 g Reispfanne (Mi)“ in der Reihenfolge der Daten; `null` ohne Herkunft. */
internal fun herkunftText(quellen: List<EinkaufsartikelQuelleEntity>): String? =
    quellen.sortedWith(compareBy({ it.date }, { it.id })).takeIf { it.isNotEmpty() }?.joinToString(" + ") { q ->
        "${QuantityFormatter.format(Quantity.of(q.contributedAmount, q.unit))} ${q.recipeName} (${tagKurz(q.date)})"
    }

/** Name einer Liste aus dem Plan: „26.10.–1.11.“. */
internal fun listenName(von: LocalDate, bis: LocalDate): String =
    "${von.dayOfMonth}.${von.monthValue}.–${bis.dayOfMonth}.${bis.monthValue}."

/** Schlüssel eines Plan-Artikels wie `ShoppingNeed.key`; `null` bei manuellen Artikeln oder ohne Zutat/Einheit. */
internal fun schluessel(e: EinkaufsartikelEntity): String? =
    if (e.manual || e.ingredientId == null || e.unit == null) null else "${e.ingredientId}|${e.unit.dimension}"

/**
 * Abteilung eines Artikels: eine gewählte Abteilung (Details) gilt, sonst über Katalog und Zutatenkategorie
 * (`ShoppingCatalog.sectionFor`).
 */
internal fun abteilungVon(category: String?, name: String): String =
    category?.takeIf { it in ShoppingCatalog.sectionOrder } ?: ShoppingCatalog.sectionFor(category, name)

/** Emoji einer Abteilung; „🛒“ für „Eigene Artikel“. */
internal fun abteilungsEmoji(abteilung: String): String =
    ShoppingCatalog.sections.firstOrNull { it.name == abteilung }?.emoji ?: "🛒"

internal fun tagKurz(datum: LocalDate): String = when (datum.dayOfWeek!!) {
    DayOfWeek.MONDAY -> "Mo"
    DayOfWeek.TUESDAY -> "Di"
    DayOfWeek.WEDNESDAY -> "Mi"
    DayOfWeek.THURSDAY -> "Do"
    DayOfWeek.FRIDAY -> "Fr"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "So"
}
