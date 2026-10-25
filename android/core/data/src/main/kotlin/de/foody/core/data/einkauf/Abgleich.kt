package de.foody.core.data.einkauf

import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.domain.DiffType
import de.foody.domain.EinkaufsAbgleich
import de.foody.domain.Quantity
import de.foody.domain.ShoppingCatalog
import de.foody.domain.ShoppingDiff
import de.foody.domain.ShoppingNeed

enum class AbgleichArt { NEU, MEHR, WENIGER, WIEDER_OFFEN, ENTFAELLT }

/** Eine Zeile „Mit Plan abgleichen“: „+ neu“, „↑ 3 → 4 Stk.“, „↓ …“, „wieder auf die Liste“, „entfällt“. */
data class AbgleichZeile(
    val schluessel: String,
    val art: AbgleichArt,
    val name: String,
    val emoji: String,
    val alt: String?,
    val neu: String?,
)

/** Eine Zeile samt dem, was „Übernehmen“ dafür braucht. */
internal class Aenderung(val zeile: AbgleichZeile, val alt: EinkaufsartikelEntity?, val neu: ShoppingNeed?)

/**
 * Plan-Artikel einer Liste gegen den aktuellen Bedarf: nur Zeilen, die „Übernehmen“ wirklich ändern würde. Gekaufte, die
 * nach [EinkaufsAbgleich] bleiben, ignorierte Schlüssel und Änderungen, die im Mengentext nicht sichtbar sind
 * (aufgerundete Stückzahlen), ergeben keine Zeile.
 */
internal fun aenderungen(artikel: List<EinkaufsartikelEntity>, p: PlanBedarf, ignoriert: Set<String>): List<Aenderung> {
    val plan = artikel.filter { schluessel(it) != null }
    val diff = ShoppingDiff.diff(plan, { schluessel(it)!! }, { Quantity.of(it.amount ?: java.math.BigDecimal.ZERO, it.unit!!) }, p.bedarf)
    return diff.filter { it.key !in ignoriert }.mapNotNull { e ->
        val alt = e.old
        val neu = e.new
        val altText = alt?.let { mengeText(it.amount, it.unit) }
        val neuText = neu?.let(p::mengeText)
        val art = when (e.type) {
            DiffType.UNCHANGED -> null
            DiffType.ADDED -> AbgleichArt.NEU
            DiffType.REMOVED -> if (EinkaufsAbgleich.entfaelltLoeschen(alt!!.checked)) AbgleichArt.ENTFAELLT else null
            DiffType.CHANGED -> {
                val altMenge = Quantity.of(alt!!.amount ?: java.math.BigDecimal.ZERO, alt.unit!!)
                when {
                    altText == neuText -> null
                    alt.checked -> if (EinkaufsAbgleich.bleibtAbgehakt(true, altMenge, neu!!.toBuy)) null else AbgleichArt.WIEDER_OFFEN
                    neu!!.toBuy.baseAmount > altMenge.baseAmount -> AbgleichArt.MEHR
                    else -> AbgleichArt.WENIGER
                }
            }
        } ?: return@mapNotNull null
        val name = alt?.name ?: p.name(neu!!)
        val kategorie = alt?.category ?: neu?.let(p::kategorie)
        Aenderung(AbgleichZeile(e.key, art, name, ShoppingCatalog.emojiFor(name, kategorie), altText, neuText), alt, neu)
    }
}
