package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Vorschlag für einen freien Tagebucheintrag aus einem Foto: was es ist und wie viele kcal. Nur ein Vorschlag –
 * vorausgefüllt, gespeichert erst mit dem Häkchen. Werte sind `null`, wenn das Modell nichts Plausibles geliefert hat.
 * Serializable, weil er im `SavedStateHandle` des Blatts liegt.
 */
data class FotoVorschlag(val name: String, val kcal: Int?, val eiweiss: BigDecimal?, val kohlenhydrate: BigDecimal?, val fett: BigDecimal?) :
    java.io.Serializable {
    /**
     * Umgerechnet für eine andere Portion: kcal ganzzahlig, Makros auf 0,1 g, jeweils kaufmännisch gerundet (Regel 29).
     * Faktor 1 lässt den Vorschlag, wie er ist.
     */
    fun mal(faktor: BigDecimal): FotoVorschlag = if (faktor.compareTo(BigDecimal.ONE) == 0) this else copy(
        kcal = kcal?.let { BigDecimal(it).multiply(faktor).setScale(0, RoundingMode.HALF_UP).toInt() },
        eiweiss = eiweiss?.malGerundet(faktor),
        kohlenhydrate = kohlenhydrate?.malGerundet(faktor),
        fett = fett?.malGerundet(faktor),
    )

    private fun BigDecimal.malGerundet(faktor: BigDecimal) = multiply(faktor).setScale(1, RoundingMode.HALF_UP)
}

/** Portion zum Vorschlag aus dem Foto: Die Menge schätzt das Modell am schlechtesten (Regel 29). */
enum class FotoPortion(val faktor: BigDecimal) {
    KLEIN(BigDecimal("0.7")),
    NORMAL(BigDecimal.ONE),
    GROSS(BigDecimal("1.4")),
}

/** Macht aus der Antwort des Bild-Sprach-Modells einen [FotoVorschlag] (Regel 29). */
object FotoErkennung {
    private val KCAL = BigDecimal.ONE..BigDecimal(3000)
    private val MAKRO = BigDecimal.ZERO..BigDecimal(300)

    /** Schlüssel für das Gedächtnis eigener Korrekturen: getrimmt, klein, Leerraum zu einem Leerzeichen. */
    fun schluessel(name: String): String = name.trim().lowercase().replace(Regex("""\s+"""), " ")

    /** `null`, wenn die Antwort kein lesbares JSON-Objekt enthält oder weder Name noch kcal. */
    fun vorschlag(antwort: String): FotoVorschlag? {
        val o = ModellJson.lesen(antwort) ?: return null
        val name = text(o, "dish_name", "dish", "name", "food_name").orEmpty()
        val kcal = suche(o, "calor", "kcal")?.innerhalb(KCAL)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
        if (name.isEmpty() && kcal == null) return null
        return FotoVorschlag(
            name = name,
            kcal = kcal,
            eiweiss = suche(o, "protein", "eiweiß", "eiweiss")?.innerhalb(MAKRO),
            kohlenhydrate = suche(o, "carb", "kohlenhydrat")?.innerhalb(MAKRO),
            fett = suche(o, "fat", "fett")?.innerhalb(MAKRO),
        )
    }

    /** Erste Zahl unter einem Schlüssel, der eines der [teile] enthält – erst eigene Schlüssel, dann verschachtelt. */
    private fun suche(o: ModellJson.Objekt, vararg teile: String): BigDecimal? {
        val eigene = o.werte.entries.firstOrNull { (k, v) ->
            val klein = k.lowercase()
            v !is ModellJson.Objekt && v !is ModellJson.Liste && "satur" !in klein && teile.any { it in klein } && zahl(v) != null
        }
        eigene?.let { return zahl(it.value) }
        return o.werte.values.filterIsInstance<ModellJson.Objekt>().firstNotNullOfOrNull { suche(it, *teile) }
    }

    private fun text(o: ModellJson.Objekt, vararg schluessel: String): String? =
        schluessel.firstNotNullOfOrNull { (o[it] as? ModellJson.Text)?.wert?.trim()?.takeIf(String::isNotEmpty) }

    private val ersteZahl = Regex("""-?\d+(?:[.,]\d+)?""")

    private fun zahl(w: ModellJson.Wert?): BigDecimal? = when (w) {
        is ModellJson.Zahl -> w.wert
        is ModellJson.Text -> ersteZahl.find(w.wert)?.value?.replace(',', '.')?.toBigDecimal()
        else -> null
    }

    private fun BigDecimal.innerhalb(bereich: ClosedRange<BigDecimal>): BigDecimal? = takeIf { it in bereich }
}
