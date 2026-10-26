package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import de.foody.domain.NutritionResult

/** Eine Zeile des Zutatenkatalogs; [kcal] je 100 g/ml, `null` ohne Nährwerte; [eigen] = keine Startzutat. */
data class ZutatKurz(
    val id: String,
    val name: String,
    val kategorie: String?,
    val kcal: Int?,
    val rezepte: Int,
    val strichcode: String?,
    val eigen: Boolean,
)

/** Filterchips des Katalogs; mehrere gelten zusammen. */
enum class ZutatFilter { OHNE_NAEHRWERTE, EIGENE, UNBENUTZT, MIT_STRICHCODE }

private fun ZutatFilter.passt(z: ZutatKurz): Boolean = when (this) {
    ZutatFilter.OHNE_NAEHRWERTE -> z.kcal == null
    ZutatFilter.EIGENE -> z.eigen
    ZutatFilter.UNBENUTZT -> z.rezepte == 0
    ZutatFilter.MIT_STRICHCODE -> z.strichcode != null
}

/**
 * Suche (getrimmt) als Teilstring ohne Groß/klein oder über den Katalognamen („Zwiebeln“ findet „Zwiebel“) und alle
 * gewählten Filter; die Reihenfolge der Liste bleibt.
 */
fun filtern(liste: List<ZutatKurz>, suche: String, filter: Set<ZutatFilter>): List<ZutatKurz> {
    val s = suche.trim()
    val katalog = if (s.isEmpty()) "" else IngredientCatalog.canonicalName(s)
    return liste.filter { z ->
        (s.isEmpty() || z.name.contains(s, ignoreCase = true) || z.name.equals(katalog, ignoreCase = true)) &&
            filter.all { it.passt(z) }
    }
}

/** Präfix der IDs aus dem Startkatalog. */
internal const val START_PRAEFIX = "seed-"

internal fun ZutatEntity.kurz(rezepte: Int): ZutatKurz = ZutatKurz(
    id = id,
    name = canonicalName,
    kategorie = category,
    kcal = energyKj?.let { NutritionResult.kcal(it) },
    rezepte = rezepte,
    strichcode = barcode,
    eigen = !id.startsWith(START_PRAEFIX),
)
