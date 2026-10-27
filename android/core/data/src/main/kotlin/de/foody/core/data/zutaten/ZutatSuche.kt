package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog

/**
 * Zutat zu einem eingegebenen Namen (Tagebuch, „Neue Zutat“): erst der bereinigte Name, dann der kanonische des
 * Katalogs („Mehl“ → „Weizenmehl“), jeweils ohne Groß/klein – in Kotlin, damit auch Umlaute passen. Leer → `null`.
 */
fun zutatMitNamen(alle: List<ZutatEntity>, name: String): ZutatEntity? {
    val n = nameBereinigt(name)
    if (n.isEmpty()) return null
    fun mit(x: String) = alle.firstOrNull { it.canonicalName.equals(x, ignoreCase = true) }
    return mit(n) ?: mit(IngredientCatalog.canonicalName(n))
}
