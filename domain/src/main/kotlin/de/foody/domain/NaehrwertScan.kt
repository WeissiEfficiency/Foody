package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Ein erkanntes Textelement (eine ML-Kit-Zeile) mit Position in Pixeln – ohne Android-Typen. */
data class OcrElement(val text: String, val links: Int, val oben: Int, val unten: Int) {
    val mitte: Int get() = (oben + unten) / 2
    val hoehe: Int get() = unten - oben
}

/** Erkannte Nährwerte je 100 g bzw. 100 ml; fehlende Nährwerte fehlen in [werte]. */
data class ScanErgebnis(val basis: NutrientBasis, val werte: Map<Nutrient, BigDecimal>)

/**
 * Liest eine Nährwerttabelle aus erkanntem Text. Nährwerttabellen sind Tabellen: Was auf einer Höhe steht, gehört
 * zusammen – auch wenn die Texterkennung Bezeichnung und Wert als getrennte Blöcke liefert. Je Zeile zählt das
 * Stichwort und die erste Zahl mit passender Einheit danach; auf EU-Packungen ist die erste Spalte „je 100 g/ml“.
 * Lieber leer als falsch: Unplausibles wird verworfen.
 */
object NaehrwertScan {
    private class Regel(val naehrwert: Nutrient, val stichwoerter: List<String>, val ausschluss: List<String> = emptyList())

    // Reihenfolge zählt: spezifischere Stichwörter zuerst („davon Zucker“ steht in derselben Tabelle wie „Kohlenhydrate“)
    private val regeln = listOf(
        Regel(Nutrient.SUGAR_G, listOf("zucker", "sugar")),
        Regel(Nutrient.FIBER_G, listOf("ballaststoff", "fibre", "fiber")),
        Regel(Nutrient.CARBS_G, listOf("kohlenhydrat", "carbohydrate")),
        Regel(Nutrient.FAT_G, listOf("fett", "fat"), ausschluss = listOf("gesättigt", "gesaettigt", "saturated", "fettsäure", "fettsaeure")),
        Regel(Nutrient.PROTEIN_G, listOf("eiweiß", "eiweiss", "protein")),
        Regel(Nutrient.SALT_G, listOf("salz", "salt")),
        Regel(Nutrient.ENERGY_KJ, listOf("brennwert", "energie", "energy")),
    )

    private const val KJ_JE_KCAL = "4.184"
    private val MAX_KJ = BigDecimal(4000)
    private val MAX_GRAMM = BigDecimal(100)

    /** Zahl mit Einheit, z. B. „1.234 kJ“, „3,5 g“, „<0,5 g“, „12mg“. */
    private val zahlMitEinheit = Regex("""(<\s*)?(\d+(?:[.  ]\d{3})*(?:[.,]\d+)?)\s*(kj|kcal|mg|g)\b""", RegexOption.IGNORE_CASE)
    private val spuren = Regex("""\b(spuren|trace|traces)\b""", RegexOption.IGNORE_CASE)

    fun auswerten(elemente: List<OcrElement>): ScanErgebnis {
        val zeilen = zeilen(elemente)
        val gesamt = zeilen.joinToString(" ").lowercase()
        val ml = Regex("""100\s*ml""").containsMatchIn(gesamt) && !Regex("""100\s*g\b""").containsMatchIn(gesamt)
        val werte = mutableMapOf<Nutrient, BigDecimal>()
        for (zeile in zeilen) {
            val klein = zeile.lowercase()
            val treffer = regeln.firstOrNull { r -> r.stichwoerter.any { it in klein } } ?: continue
            if (treffer.ausschluss.any { it in klein } || treffer.naehrwert in werte) continue
            val ab = treffer.stichwoerter.mapNotNull { s -> klein.indexOf(s).takeIf { it >= 0 } }.min()
            wert(zeile.substring(ab), treffer.naehrwert)?.let { werte[treffer.naehrwert] = it }
        }
        return ScanErgebnis(if (ml) NutrientBasis.PER_100_ML else NutrientBasis.PER_100_G, plausibel(werte))
    }

    /** Verwirft Unsinniges: Energie über 4000 kJ, Gramm über 100, Zucker über Kohlenhydraten. */
    fun plausibel(werte: Map<Nutrient, BigDecimal>): Map<Nutrient, BigDecimal> {
        val ok = werte.filter { (n, v) ->
            v.signum() >= 0 && if (n == Nutrient.ENERGY_KJ) v <= MAX_KJ else v <= MAX_GRAMM
        }.toMutableMap()
        val zucker = ok[Nutrient.SUGAR_G]
        val kh = ok[Nutrient.CARBS_G]
        if (zucker != null && kh != null && zucker > kh) ok.remove(Nutrient.SUGAR_G)
        return ok
    }

    /** Elemente gleicher Höhe (Mitte innerhalb der halben mittleren Zeilenhöhe) bilden eine Zeile, links nach rechts. */
    private fun zeilen(elemente: List<OcrElement>): List<String> {
        if (elemente.isEmpty()) return emptyList()
        val toleranz = maxOf(1, elemente.map { it.hoehe }.sorted()[elemente.size / 2] / 2)
        val gruppen = mutableListOf<MutableList<OcrElement>>()
        for (e in elemente.sortedBy { it.mitte }) {
            val g = gruppen.lastOrNull()
            if (g != null && kotlin.math.abs(e.mitte - g.map { it.mitte }.average().toInt()) <= toleranz) g += e else gruppen += mutableListOf(e)
        }
        return gruppen.map { g -> g.sortedBy { it.links }.joinToString(" ") { it.text } }
    }

    /** Erste Zahl mit passender Einheit; Energie bevorzugt kJ, sonst kcal umgerechnet. „Spuren“ = 0. */
    private fun wert(text: String, n: Nutrient): BigDecimal? {
        val zahlen = zahlMitEinheit.findAll(text).map { m -> m.groupValues[3].lowercase() to zahl(m.groupValues[2]) }.toList()
        if (n == Nutrient.ENERGY_KJ) {
            zahlen.firstOrNull { it.first == "kj" }?.let { return it.second }
            return zahlen.firstOrNull { it.first == "kcal" }?.second?.multiply(BigDecimal(KJ_JE_KCAL))?.setScale(0, RoundingMode.HALF_UP)
        }
        zahlen.firstOrNull { it.first == "g" }?.let { return it.second }
        zahlen.firstOrNull { it.first == "mg" }?.let { return it.second.divide(BigDecimal(1000)) }
        return if (spuren.containsMatchIn(text)) BigDecimal.ZERO else null
    }

    /** „3,5“ → 3,5; „1.234“ / „1 234“ → 1234; „1.5“ → 1,5 (Punkt mit genau drei Ziffern danach = Tausender). */
    private fun zahl(roh: String): BigDecimal {
        val t = roh.replace(" ", " ")
        return when {
            ',' in t -> BigDecimal(t.replace(".", "").replace(" ", "").replace(',', '.'))
            Regex("""\.\d{3}(\D|$)""").containsMatchIn(t) -> BigDecimal(t.replace(".", "").replace(" ", ""))
            else -> BigDecimal(t.replace(" ", ""))
        }
    }
}
