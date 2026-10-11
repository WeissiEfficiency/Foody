package de.foody.domain

import java.math.BigDecimal

/** „Gekocht“: zieht die verbrauchten Pflichtzutaten vom Vorrat ab – in App und Web-Oberfläche gleich. */
object Kochen {
    data class Vorrat(val id: String, val ingredientId: String, val menge: BigDecimal, val einheit: MeasureUnit)

    /**
     * Neue Mengen der Vorratseinträge, die sich ändern (id → Menge in ihrer eigenen Einheit). Umgerechnet wird nur, wenn
     * das sicher geht; nie unter 0. Optionale Zeilen und Zutaten ohne Eintrag in [umrechnung] (unbekannt) bleiben außen vor.
     */
    fun vorratAbziehen(rezept: Recipe, portionen: Int, umrechnung: Map<String, ConversionInfo>, vorrat: List<Vorrat>): Map<String, BigDecimal> {
        val bestand = vorrat.toMutableList()
        val geaendert = LinkedHashMap<String, BigDecimal>()
        for (zeile in rezept.ingredients) {
            if (zeile.optional) continue
            val info = umrechnung[zeile.ingredientId] ?: continue
            var bedarf = Quantity.of(RecipeScaler.scale(zeile.amount, rezept.defaultServings, portionen), zeile.unit)
            for ((i, p) in bestand.withIndex()) {
                if (p.ingredientId != zeile.ingredientId || bedarf.isZero()) continue
                val lager = Quantity.of(p.menge, p.einheit)
                val bedarfImLager = UnitConverter.convert(bedarf, lager.dimension, info) ?: continue
                val nehmen = if (lager.baseAmount <= bedarfImLager.baseAmount) lager else bedarfImLager
                val rest = lager.minusClamped(nehmen).amountIn(p.einheit)
                if (rest.compareTo(p.menge) != 0) {
                    bestand[i] = p.copy(menge = rest)
                    geaendert[p.id] = rest
                }
                bedarf = bedarf.minusClamped(UnitConverter.convert(nehmen, bedarf.dimension, info) ?: nehmen)
            }
        }
        return geaendert
    }
}
