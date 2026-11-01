package de.foody.feature.zutaten

import de.foody.core.data.zutaten.Angleichung
import de.foody.core.data.zutaten.ZutatKurz

private fun anzahl(n: Int, eins: String, viele: String) = "$n ${if (n == 1) eins else viele}"

/** „unbenutzt“, „in 1 Rezept“, „in 3 Rezepten“. */
fun verwendungText(rezepte: Int): String = if (rezepte == 0) "unbenutzt" else "in ${anzahl(rezepte, "Rezept", "Rezepten")}"

/** „249 Zutaten · 12 ohne Nährwerte“ (der zweite Teil nur, wenn es welche gibt). */
fun zaehlzeile(treffer: List<ZutatKurz>): String {
    val ohne = treffer.count { it.kcal == null }
    return anzahl(treffer.size, "Zutat", "Zutaten") + if (ohne > 0) " · $ohne ohne Nährwerte" else ""
}

/** Vorschau von „Namen vereinheitlichen“: Umbenennen und Zusammenführen, höchstens [max] Zeilen, dann „und n weitere“. */
fun vorschauZeilen(a: List<Angleichung>, max: Int = 8): List<String> {
    val zeilen = a.mapNotNull {
        when (it) {
            is Angleichung.Umbenennen -> "${it.alt} → ${it.neu}"
            is Angleichung.Zusammenfuehren -> "${it.vonName} → ${it.nachName} (zusammenführen)"
            is Angleichung.KategorieErgaenzen -> null
        }
    }
    return if (zeilen.size <= max) zeilen else zeilen.take(max) + "und ${zeilen.size - max} weitere"
}

fun kategorienText(n: Int): String = anzahl(n, "Kategorie", "Kategorien") + " ergänzt"

fun angepasstText(n: Int): String = anzahl(n, "Zutat", "Zutaten") + " angepasst"

/** „Verwendet in: A, B und 1 weiteres“ – höchstens zwei Namen. */
fun verwendetInText(namen: List<String>): String {
    if (namen.isEmpty()) return "Noch in keinem Rezept"
    val rest = namen.size - 2
    val weitere = when {
        rest <= 0 -> ""
        rest == 1 -> " und 1 weiteres"
        else -> " und $rest weitere"
    }
    return "Verwendet in: " + namen.take(2).joinToString(", ") + weitere
}

fun zusammenfuehrenFrage(von: String, nach: String, rezepte: Int): String {
    val umgestellt = when (rezepte) {
        0 -> ""
        1 -> "1 Rezept wird umgestellt, "
        else -> "$rezepte Rezepte werden umgestellt, "
    }
    return "„$von“ in „$nach“ zusammenführen? $umgestellt„$von“ wird gelöscht."
}

fun inVerwendungText(rezepte: Int): String =
    "In ${anzahl(rezepte, "Rezept", "Rezepten")} verwendet. Zum Entfernen mit einer anderen Zutat zusammenführen."
