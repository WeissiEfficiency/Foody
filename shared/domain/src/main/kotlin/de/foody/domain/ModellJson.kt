package de.foody.domain

import java.math.BigDecimal

/**
 * Nachsichtiger JSON-Leser für Antworten des Bild-Sprach-Modells (Fotoerkennung, Regel 29). Modelle umgeben ihr JSON
 * gern mit Text oder einem Codezaun, schreiben Zahlen mit Einheit (`"fat": 15g`) und lassen ein Komma vor `}` stehen.
 * Das alles wird hingenommen; was danach nicht lesbar ist, ergibt `null` statt einer Vermutung.
 */
object ModellJson {
    sealed interface Wert

    data class Objekt(val werte: Map<String, Wert>) : Wert {
        /** Schlüssel ohne Rücksicht auf Groß-/Kleinschreibung. */
        operator fun get(schluessel: String): Wert? = werte[schluessel] ?: werte.entries.firstOrNull { it.key.equals(schluessel, ignoreCase = true) }?.value
    }

    data class Liste(val werte: List<Wert>) : Wert

    data class Text(val wert: String) : Wert

    data class Zahl(val wert: BigDecimal) : Wert

    data object Wahr : Wert

    data object Falsch : Wert

    data object Leer : Wert

    /** Zahl mit Einheit direkt nach einem Doppelpunkt, z. B. `: 15g`, `: 250 kcal`, `: 20,5g` → nur die Zahl. */
    private val zahlMitEinheit = Regex(""":\s*(-?\d+(?:[.,]\d+)?)\s*(?:g|kg|mg|kcal|cal|kj|ml|l)\b""", RegexOption.IGNORE_CASE)

    /** Das erste lesbare JSON-Objekt in [text]; `null`, wenn keines lesbar ist. */
    fun lesen(text: String): Objekt? {
        val repariert = zahlMitEinheit.replace(text) { ": " + it.groupValues[1].replace(',', '.') }
        var ab = repariert.indexOf('{')
        while (ab >= 0) {
            runCatching { Leser(repariert, ab).objekt() }.getOrNull()?.let { return it }
            ab = repariert.indexOf('{', ab + 1)
        }
        return null
    }

    private class Leser(private val s: String, private var i: Int) {
        fun objekt(): Objekt {
            erwarte('{')
            val werte = LinkedHashMap<String, Wert>()
            while (true) {
                leer()
                if (zeichen() == '}') {
                    i++
                    return Objekt(werte)
                }
                val schluessel = text()
                leer()
                erwarte(':')
                werte[schluessel] = wert()
                leer()
                when (zeichen()) {
                    ',' -> i++
                    '}' -> {}
                    else -> fehler()
                }
            }
        }

        private fun liste(): Liste {
            erwarte('[')
            val werte = mutableListOf<Wert>()
            while (true) {
                leer()
                if (zeichen() == ']') {
                    i++
                    return Liste(werte)
                }
                werte += wert()
                leer()
                when (zeichen()) {
                    ',' -> i++
                    ']' -> {}
                    else -> fehler()
                }
            }
        }

        private fun wert(): Wert {
            leer()
            return when (val c = zeichen()) {
                '{' -> objekt()
                '[' -> liste()
                '"' -> Text(text())
                else -> when {
                    s.startsWith("true", i) -> Wahr.also { i += 4 }
                    s.startsWith("false", i) -> Falsch.also { i += 5 }
                    s.startsWith("null", i) -> Leer.also { i += 4 }
                    c == '-' || c.isDigit() -> zahl()
                    else -> fehler()
                }
            }
        }

        private fun zahl(): Zahl {
            val ab = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return Zahl(s.substring(ab, i).toBigDecimalOrNull() ?: fehler())
        }

        private fun text(): String {
            erwarte('"')
            val b = StringBuilder()
            while (true) {
                val c = zeichen()
                i++
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        val e = zeichen()
                        i++
                        when (e) {
                            'n' -> b.append('\n')
                            't' -> b.append('\t')
                            'r' -> b.append('\r')
                            'u' -> {
                                b.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun leer() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun zeichen(): Char = if (i < s.length) s[i] else fehler()

        private fun erwarte(c: Char) {
            if (zeichen() != c) fehler()
            i++
        }

        private fun fehler(): Nothing = throw IllegalArgumentException("kein JSON an Stelle $i")
    }
}
