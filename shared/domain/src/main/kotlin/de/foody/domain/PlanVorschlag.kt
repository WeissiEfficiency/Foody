package de.foody.domain

import java.time.LocalDate

/** Eingaben für [MealSuggestions], wie App und Web-Oberfläche sie bilden. */
object PlanVorschlag {
    data class Rezept(val id: String, val favorit: Boolean, val mahlzeiten: Set<Mahlzeit>, val pflichtZutaten: List<PantryCoverage.Requirement>)

    /** Tage ab [heute], an denen [mahlzeit] noch fehlt; [belegt] = slotTypes je Tag. */
    fun leereTage(tage: List<LocalDate>, heute: LocalDate, belegt: Map<LocalDate, List<String>>, mahlzeit: Mahlzeit): List<LocalDate> =
        tage.filter { tag -> tag >= heute && belegt[tag].orEmpty().none { Mahlzeit.ausText(it) == mahlzeit } }

    /**
     * Vorschlag „Tag → Rezept-ID“. [eingeplant] = (Rezept, Datum) aller Planeinträge; zählt, was ab
     * [MealSuggestions.REPEAT_GAP_DAYS] vor dem ersten leeren Tag bis [bis] eingeplant ist (auch diese Woche).
     */
    fun vorschlagen(
        rezepte: List<Rezept>,
        leereTage: List<LocalDate>,
        vorratZutaten: Set<String>,
        eingeplant: List<Pair<String, LocalDate>>,
        bis: LocalDate,
        seed: Long,
        mahlzeit: Mahlzeit,
    ): Map<LocalDate, String> {
        if (leereTage.isEmpty() || rezepte.isEmpty()) return emptyMap()
        val ab = leereTage.min().minusDays(MealSuggestions.REPEAT_GAP_DAYS)
        val zuletzt = eingeplant.filter { (_, d) -> d in ab..bis }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
        val fehlend = PantryCoverage.missingByRecipe(rezepte.flatMap { it.pflichtZutaten }, vorratZutaten)
        val kandidaten = rezepte.map { MealSuggestions.Candidate(it.id, it.favorit, fehlend[it.id], zuletzt[it.id], it.mahlzeiten) }
        return MealSuggestions.suggest(kandidaten, leereTage, seed, mahlzeit)
    }
}
