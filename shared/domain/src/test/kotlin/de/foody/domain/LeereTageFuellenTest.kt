package de.foody.domain

import de.foody.domain.Abwechslung.Merkmal
import de.foody.domain.LeereTageFuellen.Rezept
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LeereTageFuellenTest {
    private val montag = LocalDate.of(2026, 10, 19)
    private val woche = (0L..6L).map { montag.plusDays(it) }
    private val abend = setOf(Mahlzeit.ABENDESSEN)
    private val haupt = setOf(Gang.HAUPTSPEISE)

    private fun einordnung(mahlzeiten: Set<Mahlzeit> = abend, gaenge: Set<Gang> = haupt) = Einordnung(mahlzeiten, gaenge, false, false)

    private fun rezept(
        id: String,
        favorit: Boolean = false,
        gekocht: Int = 0,
        zuletztGeplant: LocalDate? = null,
        merkmale: Set<Merkmal> = emptySet(),
        einordnung: Einordnung = einordnung(),
    ) = Rezept(id, einordnung, favorit, gekocht, zuletztGeplant, merkmale)

    private fun hauptgerichte(n: Int, prefix: String = "r") = (1..n).map { rezept("$prefix$it") }

    private fun vorschlag(rezepte: List<Rezept>, seed: Long = 1, tage: List<LocalDate> = woche, belegt: Map<LocalDate, Set<Merkmal>> = emptyMap()) =
        LeereTageFuellen.vorschlagen(rezepte, tage, Mahlzeit.ABENDESSEN, seed, belegt)

    @Test fun fuelltJedenTagOhneDoppelte() {
        val plan = vorschlag(hauptgerichte(20))
        assertEquals(woche.toSet(), plan.keys)
        assertEquals(7, plan.values.toSet().size)
    }

    @Test fun wenigerRezepteAlsTageFuelltDieFruehestenTage() {
        val plan = vorschlag(hauptgerichte(3))
        assertEquals(woche.take(3).toSet(), plan.keys)
    }

    @Test fun keineBrotzeitNachspeiseOderFruehstueckZumAbendessen() {
        val ungeeignet = listOf(
            rezept("dip", einordnung = einordnung(abend, setOf(Gang.BROTZEIT))),
            rezept("bauernbrot", einordnung = einordnung(setOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.ABENDESSEN), setOf(Gang.BROTZEIT))),
            rezept("tiramisu", einordnung = einordnung(setOf(Mahlzeit.SNACK), setOf(Gang.NACHSPEISE))),
            rezept("porridge", einordnung = einordnung(setOf(Mahlzeit.FRUEHSTUECK), emptySet())),
        )
        repeat(30) { seed ->
            val plan = vorschlag(hauptgerichte(3) + ungeeignet, seed.toLong())
            assertEquals(setOf("r1", "r2", "r3"), plan.values.toSet(), "lieber Lücken als ein Dip zum Abendessen")
        }
    }

    @Test fun suppeOderSalatAlsHauptspeiseIstErlaubt() {
        val suppe = rezept("suppe", einordnung = einordnung(setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN), setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE)))
        assertEquals(mapOf(montag to "suppe"), vorschlag(listOf(suppe), tage = listOf(montag)))
    }

    @Test fun ausgeschlosseneFallenVorDerStufenwahlWeg() {
        val ohneGang = rezept("pommes", einordnung = einordnung(abend, emptySet()))
        repeat(30) { seed ->
            val plan = LeereTageFuellen.vorschlagen(
                hauptgerichte(3) + ohneGang, listOf(montag), Mahlzeit.ABENDESSEN, seed.toLong(), ausgeschlossen = setOf("r1", "r2", "r3"),
            )
            assertEquals(mapOf(montag to "pommes"), plan)
        }
    }

    @Test fun eingeordneteVorUnbekanntemGangVorUneingeordnetem() {
        val ohneGang = rezept("pommes", einordnung = einordnung(abend, emptySet()))
        val uneingeordnet = rezept("la-garbure", einordnung = einordnung(emptySet(), emptySet()))
        repeat(30) { seed ->
            val plan = vorschlag(hauptgerichte(5) + ohneGang + uneingeordnet, seed.toLong())
            assertEquals(setOf("r1", "r2", "r3", "r4", "r5"), plan.filterKeys { it < montag.plusDays(5) }.values.toSet())
            assertEquals("pommes", plan[montag.plusDays(5)])
            assertEquals("la-garbure", plan[montag.plusDays(6)])
        }
    }

    @Test fun favoritenBevorzugtAberNichtAusschliesslich() {
        val rezepte = (1..10).map { rezept("fav$it", favorit = true) } + hauptgerichte(30)
        var favTreffer = 0
        var andereTreffer = 0
        var vorschlaegeMitAnderen = 0
        repeat(300) { seed ->
            val plan = vorschlag(rezepte, seed.toLong())
            val fav = plan.values.count { it.startsWith("fav") }
            favTreffer += fav
            andereTreffer += plan.size - fav
            if (fav < plan.size) vorschlaegeMitAnderen++
        }
        // je Rezept gerechnet: ein Favorit landet mindestens doppelt so oft im Vorschlag wie ein anderes Rezept
        assertTrue(favTreffer / 10.0 > 2 * andereTreffer / 30.0, "Favoriten $favTreffer, andere $andereTreffer")
        assertTrue(vorschlaegeMitAnderen > 250, "nicht jede Woche nur Favoriten: $vorschlaegeMitAnderen von 300")
    }

    @Test fun oftGekochtesWirdBevorzugt() {
        val oft = (1..10).map { rezept("oft$it", gekocht = 6) }
        var oftTreffer = 0
        var nieTreffer = 0
        repeat(300) { seed ->
            val plan = vorschlag(oft + hauptgerichte(10, "nie"), seed.toLong())
            oftTreffer += plan.values.count { it.startsWith("oft") }
            nieTreffer += plan.values.count { it.startsWith("nie") }
        }
        assertTrue(oftTreffer > nieTreffer * 1.3, "oft $oftTreffer, nie $nieTreffer")
    }

    @Test fun kuerzlichGeplantesFaelltWeg() {
        val rezepte = listOf(
            rezept("vorgestern", favorit = true, zuletztGeplant = montag.minusDays(2)),
            rezept("diese-woche", favorit = true, zuletztGeplant = montag.plusDays(4)),
            rezept("vor-einem-monat", zuletztGeplant = montag.minusDays(30)),
        )
        assertEquals(mapOf(montag to "vor-einem-monat"), vorschlag(rezepte, tage = listOf(montag)))
    }

    @Test fun nichtZweimalHintereinanderDieselbeGrundlage() {
        val rezepte = (1..6).map { rezept("nudeln$it", merkmale = setOf(Merkmal.NUDELN)) } +
            (1..6).map { rezept("reis$it", merkmale = setOf(Merkmal.REIS)) } +
            (1..6).map { rezept("kartoffel$it", merkmale = setOf(Merkmal.KARTOFFELN)) }
        repeat(100) { seed ->
            val plan = vorschlag(rezepte, seed.toLong())
            val folge = woche.map { tag -> plan.getValue(tag).takeWhile { !it.isDigit() } }
            folge.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b, "Seed $seed: $folge") }
        }
    }

    @Test fun bereitsGeplanterNachbartagZaehltMit() {
        val rezepte = listOf(rezept("nudeln", merkmale = setOf(Merkmal.NUDELN)), rezept("reis", merkmale = setOf(Merkmal.REIS)))
        val belegt = mapOf(montag.minusDays(1) to setOf(Merkmal.NUDELN), montag.plusDays(1) to setOf(Merkmal.NUDELN))
        repeat(20) { seed ->
            assertEquals(mapOf(montag to "reis"), vorschlag(rezepte, seed.toLong(), listOf(montag), belegt))
        }
    }

    @Test fun ohneAuswahlLieberWiederholungAlsLuecke() {
        val nurNudeln = (1..7).map { rezept("nudeln$it", merkmale = setOf(Merkmal.NUDELN)) }
        assertEquals(7, vorschlag(nurNudeln).size)
    }

    @Test fun gleicherSeedGleichesErgebnisAndererSeedNeuGemischt() {
        val rezepte = hauptgerichte(40)
        assertEquals(vorschlag(rezepte, 7), vorschlag(rezepte.reversed(), 7))
        assertNotEquals(vorschlag(rezepte, 7), vorschlag(rezepte, 8))
    }

    @Test fun fruehstueckBrauchtKeineHauptspeise() {
        val porridge = rezept("porridge", einordnung = einordnung(setOf(Mahlzeit.FRUEHSTUECK), emptySet()))
        val plan = LeereTageFuellen.vorschlagen(listOf(porridge, rezept("curry")), listOf(montag), Mahlzeit.FRUEHSTUECK, 1)
        assertEquals(mapOf(montag to "porridge"), plan)
    }

    @Test fun keineTageOderRezepteKeinVorschlag() {
        assertTrue(vorschlag(hauptgerichte(5), tage = emptyList()).isEmpty())
        assertTrue(vorschlag(emptyList()).isEmpty())
    }
}
