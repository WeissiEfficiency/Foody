package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tagebuch: Werte je Eintrag (Rezept × Portionen, Zutat × Menge) und Summen je Mahlzeit und Tag. */
class TagebuchTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun assertBd(expected: String, actual: BigDecimal?) = assertEquals(0, bd(expected).compareTo(actual!!), "$actual ≠ $expected")
    private fun profil(kj: String, basis: NutrientBasis = NutrientBasis.PER_100_G) =
        NutrientProfile(basis, mapOf(Nutrient.ENERGY_KJ to bd(kj), Nutrient.PROTEIN_G to bd("10")))

    private val reis = Ingredient("reis", "Reis", nutrients = profil("400"))
    private val milch = Ingredient("milch", "Milch", nutrients = profil("250", NutrientBasis.PER_100_ML))
    private val ei = Ingredient("ei", "Ei", ConversionInfo(pieceWeightG = bd("50")), profil("400"))
    private val bier = Ingredient("bier", "Bier")
    private val zutaten = listOf(reis, milch, ei, bier).associateBy { it.id }

    private fun rezept(vararg zeilen: RecipeIngredient) = Recipe("r", "Reisgericht", 2, zeilen.toList())

    @Test fun rezeptPortionenSkalieren() {
        val r = rezept(RecipeIngredient("a", "reis", bd("200"), MeasureUnit.GRAM)) // 800 kJ, 2 Portionen
        assertBd("400", Tagebuch.naehrwerteRezept(r, zutaten, BigDecimal.ONE).energieKj)
        assertBd("200", Tagebuch.naehrwerteRezept(r, zutaten, bd("0.5")).energieKj)
        val zwei = Tagebuch.naehrwerteRezept(r, zutaten, bd("2"))
        assertBd("800", zwei.energieKj)
        assertBd("20", zwei.eiweiss)
        assertTrue(zwei.vollstaendig)
    }

    @Test fun rezeptUnvollstaendig() {
        val r = rezept(
            RecipeIngredient("a", "reis", bd("200"), MeasureUnit.GRAM),
            RecipeIngredient("b", "bier", bd("1"), MeasureUnit.LITER),
        )
        val w = Tagebuch.naehrwerteRezept(r, zutaten, BigDecimal.ONE)
        assertFalse(w.vollstaendig)
        assertBd("400", w.energieKj)
    }

    @Test fun zutatGrammMlUndStueck() {
        assertBd("600", Tagebuch.naehrwerteZutat(reis, bd("150"), MeasureUnit.GRAM)?.energieKj)
        assertBd("500", Tagebuch.naehrwerteZutat(milch, bd("200"), MeasureUnit.MILLILITER)?.energieKj)
        assertBd("400", Tagebuch.naehrwerteZutat(ei, bd("2"), MeasureUnit.PIECE)?.energieKj)
        assertBd("15", Tagebuch.naehrwerteZutat(reis, bd("150"), MeasureUnit.GRAM)?.eiweiss)
    }

    @Test fun zutatNichtUmrechenbar() {
        assertNull(Tagebuch.naehrwerteZutat(reis, bd("2"), MeasureUnit.PIECE), "Stück ohne Stückgewicht")
        assertNull(Tagebuch.naehrwerteZutat(bier, bd("500"), MeasureUnit.MILLILITER), "keine Nährwerte")
    }

    private fun werte(kcal: Int?, vollstaendig: Boolean = true) =
        Naehrwerte(kcal?.let { bd(it.toString()).multiply(bd("4.184")) }, bd("5"), null, null, vollstaendig)

    @Test fun bilanzSummiertUndGibtLueckenWeiter() {
        val b = Tagebuch.bilanz(
            listOf(
                EintragWerte(Mahlzeit.FRUEHSTUECK, werte(100)),
                EintragWerte(Mahlzeit.FRUEHSTUECK, werte(200)),
                EintragWerte(Mahlzeit.ABENDESSEN, werte(300, vollstaendig = false)),
            ),
        )
        assertEquals(600, b.tag.kcal)
        assertEquals(15, b.tag.eiweiss)
        assertFalse(b.tag.vollstaendig)
        assertEquals(300, b.jeMahlzeit.getValue(Mahlzeit.FRUEHSTUECK).kcal)
        assertTrue(b.jeMahlzeit.getValue(Mahlzeit.FRUEHSTUECK).vollstaendig)
        assertFalse(Mahlzeit.MITTAGESSEN in b.jeMahlzeit)
    }

    @Test fun eintragOhneEnergieZaehltNullUndIstUnvollstaendig() {
        val b = Tagebuch.bilanz(listOf(EintragWerte(Mahlzeit.SNACK, werte(null))))
        assertEquals(0, b.tag.kcal)
        assertFalse(b.tag.vollstaendig)
    }

    @Test fun offeneVorschlaegeOhneUebernommene() =
        assertEquals(listOf("a", "c"), Tagebuch.offeneVorschlaege(listOf("a", "b", "c"), { it }, setOf("b")))

    /** Die Summe passt zu den angezeigten Zeilen: 2 × 100,4 kcal → Zeilen 100 + 100, Summe 200 (nicht 201). */
    @Test fun summeAusGerundetenZeilen() {
        val kj = bd("100.4") * bd("4.184") // 420,0736 kJ ≈ 100,4 kcal
        val zeile = Naehrwerte(kj, null, null, null, true)
        assertEquals(100, zeile.kcal)
        val b = Tagebuch.bilanz(listOf(EintragWerte(Mahlzeit.SNACK, zeile), EintragWerte(Mahlzeit.SNACK, zeile)))
        assertEquals(200, b.tag.kcal)
        assertEquals(200, b.jeMahlzeit.getValue(Mahlzeit.SNACK).kcal)
    }
}
