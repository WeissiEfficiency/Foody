package de.foody.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlanVorschlagTest {
    private val montag = LocalDate.of(2026, 10, 12)
    private val woche = (0L..6L).map { montag.plusDays(it) }

    private fun rezepte(n: Int) = (1..n).map { PlanVorschlag.Rezept("r$it", favorit = it % 3 == 0, mahlzeiten = emptySet(), pflichtZutaten = emptyList()) }

    @Test fun leereTageNurAbHeuteUndOhneMahlzeit() {
        val belegt = mapOf(montag.plusDays(2) to listOf("ABENDESSEN"), montag.plusDays(3) to listOf("MITTAGESSEN"))
        val leer = PlanVorschlag.leereTage(woche, montag.plusDays(1), belegt, Mahlzeit.ABENDESSEN)
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L).map { montag.plusDays(it) }, leer)
    }

    @Test fun gleicherSeedGleicherVorschlagWieMealSuggestions() {
        val r = rezepte(10) + PlanVorschlag.Rezept("frueh", false, setOf(Mahlzeit.FRUEHSTUECK), emptyList()) +
            PlanVorschlag.Rezept("voll", false, emptySet(), listOf(PantryCoverage.Requirement("voll", "reis", "Reis")))
        val eingeplant = listOf("r1" to montag.minusDays(30))
        val ergebnis = PlanVorschlag.vorschlagen(r, woche, setOf("reis"), eingeplant, woche.last(), 42, Mahlzeit.ABENDESSEN)
        val erwartet = MealSuggestions.suggest(r.map {
            MealSuggestions.Candidate(it.id, it.favorit, if (it.id == "voll") 0 else null, if (it.id == "r1") montag.minusDays(30) else null, it.mahlzeiten)
        }, woche, 42, Mahlzeit.ABENDESSEN)
        assertEquals(erwartet, ergebnis)
        assertEquals(ergebnis, PlanVorschlag.vorschlagen(r, woche, setOf("reis"), eingeplant, woche.last(), 42, Mahlzeit.ABENDESSEN))
    }

    @Test fun kuerzlichEingeplantesFaelltWeg() {
        val r = rezepte(10)
        val ergebnis = PlanVorschlag.vorschlagen(r, woche, emptySet(), listOf("r5" to montag.minusDays(3), "r6" to montag.plusDays(5)), woche.last(), 1, Mahlzeit.ABENDESSEN)
        assertFalse("r5" in ergebnis.values)
        assertFalse("r6" in ergebnis.values, "diese Woche schon eingeplant")
    }
}
