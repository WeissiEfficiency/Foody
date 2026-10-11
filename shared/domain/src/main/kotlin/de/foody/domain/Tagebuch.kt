package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class TagebuchArt { REZEPT, ZUTAT, FREI }

/**
 * Nährwerte eines Tagebuch-Eintrags; beim Eintragen berechnet und danach festgehalten.
 * `null` = unbekannt. [vollstaendig] ist false, wenn beim Berechnen Energiewerte fehlten (Anzeige „≥“).
 */
data class Naehrwerte(
    val energieKj: BigDecimal?,
    val eiweiss: BigDecimal?,
    val kohlenhydrate: BigDecimal?,
    val fett: BigDecimal?,
    val vollstaendig: Boolean,
) {
    val kcal: Int? get() = energieKj?.let(NutritionResult::kcal)
}

data class EintragWerte(val mahlzeit: Mahlzeit, val werte: Naehrwerte)

data class Summe(val kcal: Int, val eiweiss: Int, val kohlenhydrate: Int, val fett: Int, val vollstaendig: Boolean)

/** Tagessumme und Summen je Mahlzeit (nur Mahlzeiten mit Einträgen). */
data class TagesBilanz(val tag: Summe, val jeMahlzeit: Map<Mahlzeit, Summe>)

/** Rechenregeln des Tagebuchs. Gerechnet wird pro Person: eine Portion je Eintrag ist, was eine Person isst. */
object Tagebuch {
    fun naehrwerteRezept(rezept: Recipe, zutaten: Map<String, Ingredient>, portionen: BigDecimal): Naehrwerte {
        val n = NutritionCalculator.calculate(rezept, zutaten)
        fun mal(x: Nutrient) = n.perServing(x)?.multiply(portionen)
        return Naehrwerte(mal(Nutrient.ENERGY_KJ), mal(Nutrient.PROTEIN_G), mal(Nutrient.CARBS_G), mal(Nutrient.FAT_G),
            n.isComplete(Nutrient.ENERGY_KJ))
    }

    /**
     * Eine Zutat mit Menge als einzeiliges Rezept mit einer Portion – so gelten dieselben Umrechnungen (Dichte,
     * Stückgewicht) wie in Rezepten. `null`, wenn die Menge nicht umrechenbar ist oder Energiewerte fehlen.
     */
    fun naehrwerteZutat(zutat: Ingredient, menge: BigDecimal, einheit: MeasureUnit): Naehrwerte? {
        val rezept = Recipe("", zutat.name, 1, listOf(RecipeIngredient("z", zutat.id, menge, einheit)))
        val w = naehrwerteRezept(rezept, mapOf(zutat.id to zutat), BigDecimal.ONE)
        return w.takeIf { it.energieKj != null }
    }

    /**
     * Ändern eines Eintrags: Die Werte bleiben festgehalten. Ändern sich Portionen (Rezept) bzw. Menge (Zutat), werden
     * sie im Verhältnis [neu] ÷ [alt] skaliert – nie aus dem heutigen Rezept neu berechnet (ADR 0007). Unverändert ohne
     * Änderung, ohne brauchbaren alten Wert (> 0) und bei freien Einträgen.
     */
    fun skaliert(art: TagebuchArt, werte: Naehrwerte, alt: BigDecimal?, neu: BigDecimal?): Naehrwerte {
        if (art == TagebuchArt.FREI || neu == null || alt == null || alt.signum() <= 0 || neu.compareTo(alt) == 0) return werte
        val faktor = neu.divide(alt, 10, RoundingMode.HALF_UP)
        fun BigDecimal?.mal() = this?.multiply(faktor)
        return werte.copy(energieKj = werte.energieKj.mal(), eiweiss = werte.eiweiss.mal(), kohlenhydrate = werte.kohlenhydrate.mal(), fett = werte.fett.mal())
    }

    /** Freier Eintrag: kcal wie eingegeben (als kJ gespeichert), Werte gelten als vollständig. */
    fun freiWerte(kcal: Int, eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?): Naehrwerte =
        Naehrwerte(BigDecimal(kcal).multiply(NutritionResult.KJ_JE_KCAL), eiweiss, kohlenhydrate, fett, vollstaendig = true)

    fun bilanz(eintraege: List<EintragWerte>): TagesBilanz =
        TagesBilanz(summe(eintraege.map { it.werte }), eintraege.groupBy { it.mahlzeit }.mapValues { (_, e) -> summe(e.map { it.werte }) })

    /** Plan-Einträge, die noch nicht als gegessen übernommen wurden, in Eingangsreihenfolge. */
    fun <S> offeneVorschlaege(planEintraege: List<S>, id: (S) -> String, uebernommen: Set<String>): List<S> =
        planEintraege.filter { id(it) !in uebernommen }

    private fun summe(werte: List<Naehrwerte>): Summe {
        fun total(f: (Naehrwerte) -> BigDecimal?) = werte.fold(BigDecimal.ZERO) { acc, w -> acc + (f(w) ?: BigDecimal.ZERO) }
        fun BigDecimal.ganz() = setScale(0, RoundingMode.HALF_UP).toInt()
        return Summe(
            // Summe der gerundeten Zeilen: passt zu dem, was je Eintrag angezeigt wird (sonst ±1 durch doppeltes Runden).
            kcal = werte.sumOf { it.kcal ?: 0 },
            eiweiss = total { it.eiweiss }.ganz(),
            kohlenhydrate = total { it.kohlenhydrate }.ganz(),
            fett = total { it.fett }.ganz(),
            vollstaendig = werte.all { it.vollstaendig && it.energieKj != null },
        )
    }
}
