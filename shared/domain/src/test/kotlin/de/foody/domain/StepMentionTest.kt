package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepMentionTest {
    private fun mention(step: String, vararg names: Pair<String, String>) =
        StepIngredientMatcher.mentions(step, names.toMap()).associateBy { it.ingredientId }

    private fun assertAmount(expected: String, actual: BigDecimal?) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "erwartet $expected, war $actual")

    @Test fun findsPartialAmountRightBeforeTheIngredient() {
        val m = mention("Mit 0,25 l Bier sowie der Brühe ablöschen.", "bier" to "Bier")
        assertAmount("0.25", m.getValue("bier").amount)
        assertEquals(MeasureUnit.LITER, m.getValue("bier").unit)
    }

    @Test fun allowsAdjectivesBetweenAmountAndIngredient() {
        val m = mention("200 g geriebenen Käse darüberstreuen.", "k" to "Gouda", "kaese" to "Käse")
        assertAmount("200", m.getValue("kaese").amount)
        assertEquals(MeasureUnit.GRAM, m.getValue("kaese").unit)
    }

    @Test fun numberBelongingToAnotherIngredientIsNotReused() {
        val m = mention("2 Eier und Mehl verrühren.", "ei" to "Ei", "mehl" to "Weizenmehl")
        assertAmount("2", m.getValue("ei").amount)
        assertNull(m.getValue("ei").unit) // Stückzahl ohne Einheitswort
        assertNull(m.getValue("mehl").amount) // gefunden über das Synonym „Mehl“, aber ohne eigene Menge
    }

    @Test fun matchesCanonicalNamesViaTheirSynonyms() {
        // Nach dem Vereinheitlichen heißen die Zutaten „Pflanzenöl“ und „Weizenmehl“, im Text steht „Öl“ / „Mehl“
        val ids = StepIngredientMatcher.match("Öl erhitzen, dann das Mehl einrühren.", mapOf("o" to "Pflanzenöl", "m" to "Weizenmehl"))
        assertEquals(listOf("o", "m"), ids)
    }

    @Test fun shortWordsOnlyMatchAsWholeWords() {
        // „Öl“ darf nicht in „Ölsardinen“ oder „Kölsch“ treffen
        assertEquals(emptyList(), StepIngredientMatcher.match("Ölsardinen abtropfen, Kölsch kühlen.", mapOf("o" to "Pflanzenöl")))
    }

    @Test fun timesAndTemperaturesAreNoAmounts() {
        val m = mention("Die Kartoffeln 20 Minuten bei 180 Grad backen.", "k" to "Kartoffel")
        assertNull(m.getValue("k").amount)
    }
}
