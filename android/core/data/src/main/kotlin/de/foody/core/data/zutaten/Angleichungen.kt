package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog

/** Ein Schritt von „Namen vereinheitlichen“. */
sealed interface Angleichung {
    data class Umbenennen(val id: String, val alt: String, val neu: String) : Angleichung

    data class Zusammenfuehren(val vonId: String, val vonName: String, val nachId: String, val nachName: String) : Angleichung

    data class KategorieErgaenzen(val id: String, val name: String, val kategorie: String) : Angleichung
}

/**
 * Plant „Namen vereinheitlichen“ wie v1 `harmonizeNames`: Die Zutaten werden nach Name (ohne Groß/klein, dann ID)
 * nacheinander betrachtet, jeweils auf dem Stand nach den bisherigen Schritten. Ziel ist der Katalogname; heißt schon
 * eine andere Zutat so (ohne Groß/klein), wird in sie zusammengeführt, sonst umbenannt. Fehlt dann die Kategorie, wird
 * sie geraten.
 */
fun angleichungen(zutaten: List<ZutatEntity>): List<Angleichung> {
    // Stand je ID: Name und Kategorie; zusammengeführte fallen heraus
    val namen = LinkedHashMap<String, String>().apply { zutaten.forEach { put(it.id, it.canonicalName) } }
    val schritte = ArrayList<Angleichung>()
    for (z in zutaten.sortedWith(compareBy<ZutatEntity> { it.canonicalName.lowercase() }.thenBy { it.id })) {
        val name = namen[z.id] ?: continue
        val ziel = IngredientCatalog.canonicalName(name)
        val andere = namen.entries.firstOrNull { (id, n) -> id != z.id && n.equals(ziel, ignoreCase = true) }
        if (andere != null) {
            schritte += Angleichung.Zusammenfuehren(z.id, name, andere.key, andere.value)
            namen.remove(z.id)
            continue
        }
        if (ziel != name) {
            schritte += Angleichung.Umbenennen(z.id, name, ziel)
            namen[z.id] = ziel
        }
        if (z.category == null) {
            IngredientCatalog.guessCategory(ziel)?.let { schritte += Angleichung.KategorieErgaenzen(z.id, ziel, it) }
        }
    }
    return schritte
}
