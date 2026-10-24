package de.foody.domain

import java.math.BigDecimal

/**
 * Eine Zutat als Freitextzeile („200 g Mehl, gesiebt“, „1 Prise Salz (optional)“) – für den Rezept-Editor. Zerlegt mit
 * derselben Logik wie der Markdown-Import ([MarkdownRecipeImporter]), dort stehen Menge und Name auf zwei Zeilen.
 */
object ZutatZeile {
    private val zahlAmAnfang = Regex("""^(?:${GermanAmounts.NUMBER})(?:\s*(?:[-–]|bis\s)\s*(?:${GermanAmounts.NUMBER}))?(?=\s|$|[A-Za-zÄÖÜäöüß])""")
    private val optionalWort = Regex("""\(?\boptional\b\)?""", RegexOption.IGNORE_CASE)

    /** Großgeschriebene Mengenwörter, die zum Mengenteil gehören („1 Dose Tomaten“, „½ Bund Dill“). */
    private val mengenwoerter = setOf(
        "prise", "bund", "dose", "glas", "zehe", "packung", "pck.", "päckchen", "becher", "stück", "stk.", "scheibe",
        "tasse", "handvoll", "msp.", "zweig", "würfel", "blatt", "blätter", "flasche", "tüte", "beutel", "stange", "kopf",
        "knolle",
    )

    private fun istMengenwort(wort: String): Boolean {
        val w = wort.lowercase().trimEnd(',')
        return w in mengenwoerter || listOf("n", "en", "e").any { w.removeSuffix(it) in mengenwoerter }
    }

    /** `null` bei leerer Zeile oder ohne Zutatnamen („200 g“). */
    fun lesen(text: String): ImportedIngredient? {
        val zeile = text.trim().replace(Regex("""\s+"""), " ")
        if (zeile.isEmpty()) return null
        val (mengenteil, namensteil) = teilen(zeile)
        if (namensteil.isBlank()) return null
        val zutat = MarkdownRecipeImporter.buildIngredient(mengenteil, namensteil, group = null)
        if (zutat.name.isBlank()) return null
        val hinweis = zutat.note?.let { optionalWort.replace(it, "") }
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ")?.ifEmpty { null }
        return zutat.copy(note = hinweis)
    }

    /** Je nicht-leerer Zeile ein Eintrag; `null` für eine Zeile ohne erkannte Zutat. */
    fun zeilen(text: String): List<ImportedIngredient?> = text.lines().filter { it.isNotBlank() }.map(::lesen)

    /** Zeile zurück in Text, so dass [lesen] dieselbe Zutat liefert. */
    fun schreiben(menge: BigDecimal?, einheit: MeasureUnit?, name: String, hinweis: String?, optional: Boolean): String {
        val teile = hinweis?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().toMutableList()
        val vorne = mutableListOf<String>()
        if (menge != null) {
            vorne += menge.stripTrailingZeros().toPlainString().replace('.', ',')
            if (einheit != null && einheit != MeasureUnit.PIECE) {
                vorne += einheit.symbol
            } else if (teile.isNotEmpty() && teile.first().let { ' ' !in it && (istMengenwort(it) || it.first().isLowerCase()) }) {
                // „2 große Zwiebeln“, „½ Bund Dill“: das Stückwort steht vor dem Namen
                vorne += teile.removeAt(0)
            }
        } else if (teile.isNotEmpty() && teile.first().let { zahlAmAnfang.containsMatchIn(it) || vageMenge(it) != null }) {
            // „1 Prise Salz“, „etwas Petersilie“
            vorne += teile.removeAt(0)
        }
        return buildString {
            vorne.forEach { append(it).append(' ') }
            append(name)
            if (teile.isNotEmpty()) append(", ").append(teile.joinToString(", "))
            if (optional) append(" (optional)")
        }
    }

    /** Mengen- und Namensteil einer Zeile. */
    private fun teilen(zeile: String): Pair<String?, String> {
        val zahl = zahlAmAnfang.find(zeile)
        if (zahl != null) {
            val woerter = zeile.substring(zahl.range.last + 1).trim().split(' ').filter { it.isNotEmpty() }
            fun einheitsartig(w: String) = GermanAmounts.unitOf(w) != null || istMengenwort(w)
            var i = 0
            while (i < woerter.size && (woerter[i].first().isLowerCase() || einheitsartig(woerter[i]))) i++
            // Alles verbraucht: ein klein geschriebener Name („2 eier“) bleibt Name, eine Einheit („200 g“) nicht
            if (i == woerter.size && i > 0 && !einheitsartig(woerter[i - 1])) i--
            val menge = (listOf(zahl.value.trim()) + woerter.take(i)).joinToString(" ")
            return menge to woerter.drop(i).joinToString(" ")
        }
        val vage = vageMenge(zeile) ?: return null to zeile
        return vage to zeile.drop(vage.length).trim()
    }

    /** Führende vage Mengenangabe („etwas“, „evtl.“, „nach Belieben“), wie der Markdown-Import sie kennt. */
    private fun vageMenge(text: String): String? = MarkdownRecipeImporter.vagueAmounts
        .sortedByDescending { it.length }
        .firstOrNull { text.lowercase().startsWith("$it ") || text.lowercase() == it }
        ?.let { text.take(it.length) }
}
