package de.foody.domain

import java.math.BigDecimal
import java.util.Locale

/**
 * Ein Lebensmittel aus dem Bundeslebensmittelschlüssel (BLS 4.0, Max Rubner-Institut), Werte je 100 g. Ein fehlender
 * Nährstoff heißt „unbekannt“, nicht 0.
 */
data class BlsLebensmittel(val code: String, val name: String, val werte: Map<Nutrient, BigDecimal>)

/**
 * Ein Treffer der Suche. [vorschlagName]: so heißt die Zutat, wenn sie in den Katalog übernommen wird (ein regionaler
 * Name wie „Breze“, sonst der BLS-Name); [stueckG]: übliches Gewicht eines Stücks, falls bekannt.
 */
data class BlsTreffer(val lebensmittel: BlsLebensmittel, val vorschlagName: String, val stueckG: BigDecimal?)

/**
 * Regionaler oder umgangssprachlicher Name. [woerter]: Schreibweisen, wie getippt (normalisiert verglichen);
 * [suchwort]: Wort, das im BLS-Namen vorkommt; [bevorzugt]: BLS-Code, der ganz oben steht, mit [anzeigename] und
 * [stueckG] (übliche Größe, Näherung – im Katalog änderbar).
 */
private class Regionalname(
    val woerter: List<String>,
    val suchwort: String,
    val bevorzugt: String? = null,
    val anzeigename: String? = null,
    val stueckG: String? = null,
)

/**
 * Suche im BLS nach Teilwörtern, ohne Groß/klein, mit bairischen und österreichischen Namen („Breze“, „Semmel“,
 * „Leberkas“, „Fleischpflanzerl“). Zusammengesetztes aus zwei bekannten Namen („Leberkassemmel“) liefert die Treffer
 * beider Teile. Kein Ähnlichkeitsvergleich (Invariante 7): Der Nutzer wählt den Treffer selbst.
 */
object BlsSuche {
    private val regionalnamen = listOf(
        Regionalname(listOf("semmel", "semmeln", "semmerl", "weckle", "weckerl", "weck", "wecken", "schrippe", "schrippen"),
            "brötchen", bevorzugt = "B511000", stueckG = "60"),
        Regionalname(listOf("breze", "brezn", "brezen", "brezel", "brezeln", "bretzel", "laugenbreze", "laugenbrezel"),
            "laugengebäck", bevorzugt = "B723000", anzeigename = "Breze", stueckG = "80"),
        Regionalname(listOf("butterbreze", "butterbrezn", "butterbrezel"),
            "laugenbrezel mit butter", bevorzugt = "X061410", anzeigename = "Butterbreze", stueckG = "100"),
        Regionalname(listOf("leberkas", "leberkäs", "leberkäse", "fleischkas", "fleischkäs", "fleischkäse"),
            "leberkäse", bevorzugt = "W233000", anzeigename = "Leberkäse", stueckG = "100"),
        Regionalname(
            listOf("fleischpflanzerl", "fleischpflanzl", "pflanzerl", "fleischküchle", "fleischküchla", "fleischlaberl",
                "fleischlaibchen", "frikadelle", "frikadellen", "bulette", "buletten", "fleischklops", "fleischklöße"),
            "frikadelle", bevorzugt = "Y036122", anzeigename = "Fleischpflanzerl", stueckG = "100",
        ),
        Regionalname(listOf("weißwurst", "weisswurst", "weißwürste", "weisswürste", "weißwürscht"),
            "weißwurst", bevorzugt = "W224032", anzeigename = "Weißwurst", stueckG = "70"),
        Regionalname(listOf("obazda", "obatzda", "obatzter", "obazter"), "obatzter", bevorzugt = "M863000", anzeigename = "Obazda"),
        Regionalname(listOf("weißbier", "weissbier", "hefeweizen", "hefeweißbier"),
            "weizenbier", bevorzugt = "P151100", anzeigename = "Weißbier"),
        Regionalname(listOf("schweinsbraten"), "schweinebraten"),
        Regionalname(listOf("erdapfel", "erdäpfel"), "kartoffel"),
        Regionalname(listOf("topfen"), "quark"),
        Regionalname(listOf("schlagobers", "obers"), "sahne"),
        Regionalname(listOf("karfiol"), "blumenkohl"),
        Regionalname(listOf("paradeiser"), "tomate"),
        Regionalname(listOf("radi"), "rettich"),
        Regionalname(listOf("knödel", "knödl", "knedl"), "knödel"),
    )

    private val nachWort: Map<String, Regionalname> = buildMap {
        regionalnamen.forEach { r -> r.woerter.forEach { put(it, r) } }
    }

    private val bevorzugt: Map<String, Regionalname> = regionalnamen.filter { it.bevorzugt != null }.associateBy { it.bevorzugt!! }

    /** Mindestens zwei Zeichen, sonst wäre fast alles ein Treffer. */
    private const val MIN_LAENGE = 2

    /** Ab so vielen Zeichen gilt ein angefangenes Wort schon für regionale Namen. */
    private const val MIN_ANFANG = 3

    /**
     * Bis zu [max] Treffer für [eingabe]: zuerst der bevorzugte Eintrag eines regionalen Namens, dann Namen, die mit
     * dem Suchwort beginnen, dann solche mit einem Wort, das damit beginnt; innerhalb davon kürzere Namen zuerst.
     */
    fun suchen(alle: List<BlsLebensmittel>, eingabe: String, max: Int = 20): List<BlsTreffer> {
        val woerter = woerter(eingabe)
        if (woerter.isEmpty()) return emptyList()
        // Ein Wort aus zwei bekannten Namen („leberkassemmel“): die Treffer beider Teile nacheinander
        val teile = woerter.singleOrNull()?.let(::zerlegen)
        if (teile != null) {
            return teile.flatMap { suchen(alle, it, max) }.distinctBy { it.lebensmittel.code }.take(max)
        }
        val gruppen = woerter.map { wort -> (listOf(wort) + regional(wort).map { it.suchwort }).distinct() }
        val gewuenscht = woerter.flatMap { regional(it) }.mapNotNull { it.bevorzugt }.toSet()
        return alle.asSequence()
            .map { it to normal(it.name) }
            .filter { (_, name) -> gruppen.all { alternativen -> alternativen.any { it in name } } }
            .sortedWith(
                compareBy<Pair<BlsLebensmittel, String>> { (l, name) -> rang(l, name, gruppen.first(), gewuenscht) }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.name },
            )
            .take(max)
            .map { (l, _) -> treffer(l) }
            .toList()
    }

    /** Treffer mit Vorschlagsname und Stückgewicht des regionalen Namens, falls [l] dessen bevorzugter Eintrag ist. */
    fun treffer(l: BlsLebensmittel): BlsTreffer {
        val r = bevorzugt[l.code]
        return BlsTreffer(l, r?.anzeigename ?: l.name, r?.stueckG?.let(::BigDecimal))
    }

    private fun rang(l: BlsLebensmittel, name: String, erste: List<String>, gewuenscht: Set<String>): Int = when {
        l.code in gewuenscht -> 0
        erste.any { name.startsWith(it) } -> 1
        erste.any { a -> name.split(' ').any { it.startsWith(a) } } -> 2
        else -> 3
    }

    /**
     * Regionale Namen zu einem Wort: genau getroffen, sonst – ab [MIN_ANFANG] Zeichen, während noch getippt wird – alle,
     * deren Schreibweise damit beginnt („brez“ → Breze).
     */
    private fun regional(wort: String): List<Regionalname> {
        nachWort[wort]?.let { return listOf(it) }
        if (wort.length < MIN_ANFANG) return emptyList()
        return regionalnamen.filter { r -> r.woerter.any { it.startsWith(wort) } }
    }

    /** Zerlegt ein Wort in zwei bekannte regionale Namen; sonst `null`. */
    private fun zerlegen(wort: String): List<String>? {
        if (wort in nachWort) return null
        for (i in MIN_LAENGE until wort.length - 1) {
            val vorne = wort.substring(0, i)
            val hinten = wort.substring(i)
            if (vorne in nachWort && hinten in nachWort) return listOf(vorne, hinten)
        }
        return null
    }

    private fun normal(text: String): String = text.lowercase(Locale.GERMAN)

    private fun woerter(text: String): List<String> =
        normal(text).split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= MIN_LAENGE }
}
