package de.foody.domain

import java.math.BigDecimal

/**
 * Für Rezeptzeilen, die vor Regel 30 importiert oder eingegeben wurden: Küchenmaße bekommen Gramm bzw. Milliliter wie
 * bei einem neuen Import. Früher war „1 Prise“ Menge 0 („nach Bedarf“) mit der Angabe im Hinweis, „2 Msp.“ oder
 * „1 Tasse/n“ waren Stück mit dem Wort im Hinweis, „1 Beutel“ war Stück statt Packung.
 *
 * Der Hinweis einer gespeicherten Zeile ist „Gruppe · Angabe, Zusatz“ (Gruppe nur bei Zwischenüberschriften). Wie die
 * Einheit vorher hieß, steht danach weiter im Hinweis; eine umgestellte Zeile passt nicht mehr, der Nachtrag lässt sich
 * also beliebig oft ausführen.
 */
object KuechenmassNachtrag {
    data class Zeile(val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?)

    /** Neue Menge, Einheit und Hinweis; null, wenn die Zeile bleibt, wie sie ist. */
    fun umstellen(menge: BigDecimal, einheit: MeasureUnit, hinweis: String?): Zeile? {
        if (einheit != MeasureUnit.PIECE || hinweis == null) return null
        val gruppe = hinweis.substringBeforeLast(" · ", "")
        val angabe = hinweis.substringAfterLast(" · ")
        val erster = angabe.substringBefore(',').trim()
        val rest = angabe.substringAfter(',', "").trim()
        fun mitGruppe(text: String?) = listOf(gruppe, text.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }

        if (menge.signum() == 0) {
            // „1 Prise(n)“, „Prise“: bisher nach Bedarf, die Angabe steht schon im Hinweis
            if (GermanAmounts.schaetzmenge(erster.substringAfterLast(' ')) == null) return null
            val gelesen = MarkdownRecipeImporter.buildIngredient(erster, "X", group = null)
            return Zeile(gelesen.amount ?: return null, gelesen.unit ?: return null, hinweis)
        }
        GermanAmounts.schaetzmenge(erster)?.let { (je, neueEinheit) ->
            // „2 Stk.“ mit Hinweis „Msp.“: wie beim Import steht die ganze Angabe vorne im Hinweis
            val zahl = menge.stripTrailingZeros().toPlainString().replace('.', ',')
            return Zeile(menge * je, neueEinheit, mitGruppe(listOf("$zahl $erster", rest).filter { it.isNotEmpty() }.joinToString(", ")))
        }
        if (GermanAmounts.unitOf(erster) == MeasureUnit.PACKAGE) return Zeile(menge, MeasureUnit.PACKAGE, mitGruppe(rest))
        return null
    }
}
