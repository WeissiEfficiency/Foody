package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Dichte (g/ml) flüssiger BLS-Lebensmittel, damit sie sich auch in ml, EL und TL rechnen lassen. Der BLS gibt alle
 * Werte je 100 g an und kennt keine Dichte. Sie wird hier aus den Bestandteilen abgeleitet: Die Volumen von Wasser, Fett,
 * gelöster Trockenmasse (Eiweiß, Kohlenhydrate, Ballaststoffe, Salz) und Alkohol werden zusammengezählt. Für Wasser,
 * Milch, Sahne, Öl, Saft, Cola, Bier und Spirituosen trifft das die üblichen Werte auf ±0,02 g/ml, für sehr Süßes
 * (gezuckerte Kondensmilch, Sirup) auf etwa ±0,03 g/ml (`BlsDichteTest`).
 *
 * Feste Lebensmittel bleiben ohne Dichte: Wie dicht ein Pulver, Brot oder Käse im Messbecher liegt, steht nicht in den
 * Daten.
 */
object BlsDichte {
    /** ml je g gelöster Trockenmasse (Zucker ≈ 0,62, Eiweiß ≈ 0,73, Salz ≈ 0,46; überwiegend Zucker). */
    private val TROCKENMASSE = BigDecimal("0.62")

    /** ml je g Fett (Speiseöl ≈ 0,92 g/ml). */
    private val FETT = BigDecimal("1.087")

    /** ml je g Alkohol in Wasser (reiner Alkohol 1,27; gemischt zieht er sich zusammen). */
    private val ALKOHOL = BigDecimal("1.18")

    /** kJ je g Alkohol und je g Eiweiß, Kohlenhydrate, Fett und Ballaststoffe (Energie-Regel der LMIV). */
    private val KJ_ALKOHOL = BigDecimal(29)
    private val KJ_EIWEISS_KH = BigDecimal(17)
    private val KJ_FETT = BigDecimal(37)
    private val KJ_BALLASTSTOFFE = BigDecimal(8)

    private val HUNDERT = BigDecimal(100)

    /** Ganze Gruppen, die flüssig sind: N (Getränke), P (alkoholische Getränke). */
    private const val GETRAENKE = "NP"

    /** Worauf das Lebensmittel endet, wenn es flüssig ist („Vollmilch“, „Rapsöl“, „Gemüsebrühe“, „Haferdrink“ …). */
    private val ENDUNGEN = listOf(
        "saft", "nektar", "drink", "getränk", "schorle", "milch", "sahne", "kefir", "molke", "öl", "essig", "brühe",
        "bouillon", "sirup", "suppe",
    )

    /** Was danach kommt, beschreibt nur noch („Fleischbrühe mit Eierstich“, „Makrele im eigenen Saft“). */
    private val ZUSATZ = Regex(",| mit | aus | im | in | ohne | nach | für | \\(")

    /** Fest trotz flüssiger Wörter („Instantkaffeepulver“, „Brühe (Brühwürfel, Pulver)“) … */
    private val FEST = listOf("pulver", "granulat", "brühwürfel")

    /** … außer schon angerührt („Kaffee (Getränk) aus Instantpulver“, „Pilzcremesuppe aus Instantpulver (mit Wasser zubereitet)“). */
    private val ANGERUEHRT = listOf("(getränk)", "zubereitet")

    /** Aufgeschlagene Sahne für Torten ist fest, auch wenn sie „…sahne“ heißt. */
    private const val TORTENSAHNE = "für gebäck"

    /**
     * Ob man [l] in ml misst: Gruppen N und P ganz, sonst nach dem Lebensmittel vorne im Namen; Pulver nur angerührt.
     * Ein Wort mit Bindestrich zählt als Ganzes („Sahne-Windbeutel“ ist ein Gebäck).
     */
    fun istFluessig(l: BlsLebensmittel): Boolean {
        val name = l.name.lowercase(Locale.GERMAN)
        if (TORTENSAHNE in name) return false
        if (FEST.any { it in name } && ANGERUEHRT.none { it in name }) return false
        if (l.code.firstOrNull()?.let { it in GETRAENKE } == true) return true
        return name.split(ZUSATZ, limit = 2).first().split(' ', '/').any { wort -> ENDUNGEN.any(wort::endsWith) }
    }

    /** Dichte in g/ml auf zwei Stellen; `null` für Festes oder ohne bekannte Energie. */
    fun gProMl(l: BlsLebensmittel): BigDecimal? {
        if (!istFluessig(l)) return null
        val kj = l.werte[Nutrient.ENERGY_KJ] ?: return null
        fun g(n: Nutrient) = l.werte[n] ?: BigDecimal.ZERO
        val trocken = g(Nutrient.PROTEIN_G) + g(Nutrient.CARBS_G) + g(Nutrient.FIBER_G) + g(Nutrient.SALT_G)
        val fett = g(Nutrient.FAT_G)
        val alkohol = if (l.code.startsWith("P")) alkoholG(l, kj) else BigDecimal.ZERO
        val wasser = (HUNDERT - trocken - fett - alkohol).max(BigDecimal.ZERO)
        val volumen = wasser + trocken * TROCKENMASSE + fett * FETT + alkohol * ALKOHOL
        if (volumen.signum() <= 0) return null
        return HUNDERT.divide(volumen, 2, RoundingMode.HALF_UP)
    }

    /**
     * Alkohol (g je 100 g) als Energie, die Eiweiß, Kohlenhydrate, Fett und Ballaststoffe nicht erklären. Nur für
     * Gruppe P: Anderswo steckt der Rest in Säuren oder Zuckeralkoholen.
     */
    private fun alkoholG(l: BlsLebensmittel, kj: BigDecimal): BigDecimal {
        fun g(n: Nutrient) = l.werte[n] ?: BigDecimal.ZERO
        val erklaert = (g(Nutrient.PROTEIN_G) + g(Nutrient.CARBS_G)) * KJ_EIWEISS_KH + g(Nutrient.FAT_G) * KJ_FETT +
            g(Nutrient.FIBER_G) * KJ_BALLASTSTOFFE
        return (kj - erklaert).max(BigDecimal.ZERO).divide(KJ_ALKOHOL, 3, RoundingMode.HALF_UP)
    }
}
