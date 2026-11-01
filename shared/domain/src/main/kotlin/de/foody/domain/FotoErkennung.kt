package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Was das Modell zur Auswahl bekommt: eigene Rezepte, Rezepte aus dem heutigen Plan und Katalogzutaten. */
data class FotoKontext(val rezepte: List<Eintrag>, val planRezepte: Set<String>, val zutaten: List<Eintrag>) {
    data class Eintrag(val id: String, val name: String)
}

/**
 * Vorschlag für das Eintragen-Blatt aus einem Foto. Nur ein Vorschlag: vorausgefüllt, gespeichert erst mit dem Häkchen.
 * Geschätzte Werte (Portionen, Gramm, Werte bei [Frei]) sind `null`, wenn das Modell nichts Plausibles geliefert hat.
 */
sealed interface FotoVorschlag {
    /** Ein eigenes Rezept; die Nährwerte kommen aus dem Rezept (Regel 24). */
    data class Rezept(val rezeptId: String, val portionen: BigDecimal) : FotoVorschlag

    /** Ein Teller aus Katalogzutaten; die Nährwerte kommen aus dem Katalog. */
    data class Teller(val zeilen: List<TellerZeile>) : FotoVorschlag

    /** Ein freier Eintrag mit Werten des Modells. */
    data class Frei(val name: String, val kcal: Int?, val eiweiss: BigDecimal?, val kohlenhydrate: BigDecimal?, val fett: BigDecimal?) : FotoVorschlag
}

data class TellerZeile(val zutatId: String, val name: String, val gramm: BigDecimal?)

/**
 * Macht aus der Antwort des Bild-Sprach-Modells einen [FotoVorschlag] (Regel 29). Zugeordnet wird nur sicher: ein
 * Rezept über seinen Namen, eine Zutat über ihren kanonischen Namen (Regel 15) – nie über Ähnlichkeit.
 */
object FotoErkennung {
    private val PORTIONEN = BigDecimal("0.25")..BigDecimal(6)
    private val GRAMM = BigDecimal.ONE..BigDecimal(1500)
    private val KCAL = BigDecimal.ONE..BigDecimal(3000)
    private val MAKRO = BigDecimal.ZERO..BigDecimal(300)
    private val VIER = BigDecimal(4)
    private val keinRezept = setOf("none", "keins", "keins davon", "kein", "nichts", "")

    /** `null`, wenn die Antwort kein lesbares JSON-Objekt enthält. */
    fun vorschlag(antwort: String, kontext: FotoKontext): FotoVorschlag? {
        val o = ModellJson.lesen(antwort) ?: return null
        rezept(o, kontext)?.let { return it }
        teller(o, kontext)?.let { return it }
        return FotoVorschlag.Frei(
            name = text(o, "dish_name", "dish", "name", "food_name").orEmpty(),
            kcal = suche(o, "calor", "kcal")?.innerhalb(KCAL)?.setScale(0, RoundingMode.HALF_UP)?.toInt(),
            eiweiss = suche(o, "protein", "eiweiß", "eiweiss")?.innerhalb(MAKRO),
            kohlenhydrate = suche(o, "carb", "kohlenhydrat")?.innerhalb(MAKRO),
            fett = suche(o, "fat", "fett")?.innerhalb(MAKRO),
        )
    }

    private fun rezept(o: ModellJson.Objekt, kontext: FotoKontext): FotoVorschlag.Rezept? {
        val wahl = schluessel(text(o, "choice", "recipe", "auswahl") ?: return null)
        if (wahl in keinRezept) return null
        val treffer = kontext.rezepte.filter { schluessel(it.name) == wahl }
        val rezept = treffer.firstOrNull { it.id in kontext.planRezepte } ?: treffer.firstOrNull() ?: return null
        val portionen = zahl(o["portions"] ?: o["portionen"] ?: o["servings"])
            ?.multiply(VIER)?.setScale(0, RoundingMode.HALF_UP)?.divide(VIER)
            ?.innerhalb(PORTIONEN) ?: BigDecimal.ONE
        return FotoVorschlag.Rezept(rezept.id, portionen)
    }

    /** Teller nur, wenn es mindestens zwei Zutaten sind und jede eine Katalogzutat ist. */
    private fun teller(o: ModellJson.Objekt, kontext: FotoKontext): FotoVorschlag.Teller? {
        val zeilen = zutatenzeilen(o)
        if (zeilen.size < 2) return null
        val katalog = kontext.zutaten.associateBy { schluessel(IngredientCatalog.canonicalName(it.name)) }
        val zugeordnet = zeilen.map { (name, gramm) ->
            val z = katalog[schluessel(IngredientCatalog.canonicalName(name))] ?: return null
            TellerZeile(z.id, z.name, gramm?.innerhalb(GRAMM))
        }
        return FotoVorschlag.Teller(zugeordnet)
    }

    /** Zutaten als `ingredients: [{name, grams}]`, als Liste von Namen oder als Tabelle `portion_size: {name: gramm}`. */
    private fun zutatenzeilen(o: ModellJson.Objekt): List<Pair<String, BigDecimal?>> {
        (o["portion_size"] as? ModellJson.Objekt)?.let { t -> return t.werte.map { (name, menge) -> name to zahl(menge) } }
        val liste = (o["ingredients"] ?: o["zutaten"]) as? ModellJson.Liste ?: return emptyList()
        return liste.werte.mapNotNull { z ->
            when (z) {
                is ModellJson.Text -> z.wert to null
                is ModellJson.Objekt -> text(z, "name", "ingredient")?.let { it to suche(z, "gram", "weight", "amount", "quantity", "menge") }
                else -> null
            }
        }
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
        schluessel.firstNotNullOfOrNull { (o[it] as? ModellJson.Text)?.wert?.trim() }

    private val ersteZahl = Regex("""-?\d+(?:[.,]\d+)?""")

    private fun zahl(w: ModellJson.Wert?): BigDecimal? = when (w) {
        is ModellJson.Zahl -> w.wert
        is ModellJson.Text -> ersteZahl.find(w.wert)?.value?.replace(',', '.')?.toBigDecimal()
        else -> null
    }

    private fun BigDecimal.innerhalb(bereich: ClosedRange<BigDecimal>): BigDecimal? = takeIf { it in bereich }

    private fun schluessel(name: String) = name.trim().lowercase().replace(Regex("""\s+"""), " ")
}
