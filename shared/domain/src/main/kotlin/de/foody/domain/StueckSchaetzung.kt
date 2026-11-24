package de.foody.domain

import java.math.BigDecimal
import java.util.Locale

/**
 * Geschätztes Gewicht eines Stücks nach dem Namen, wenn die Zutat selbst keines kennt (BLS-Treffer wie „Döner Kebab, …“).
 * Grundlage: die Stückgewichte der Startdaten, die Zutaten mit Stückangabe aus dem Rezeptkorpus und übliche Portionen von
 * Fertiggerichten. Eine Näherung zum Anpassen – beim Eintragen änderbar, die Zutat merkt sich den Wert.
 *
 * Verglichen wird nur der Teil vor dem ersten Komma (im BLS folgt danach die Zubereitung), Wort für Wort ohne Groß/klein.
 * Ein Stichwort trifft ein gleiches Wort; ab [MIN_ENDUNG] Zeichen auch das Ende eines zusammengesetzten Worts
 * („Gemüsezwiebel“ → „zwiebel“). Das längste treffende Stichwort gewinnt („frühlingszwiebel“ vor „zwiebel“).
 */
object StueckSchaetzung {
    /** Kürzere Stichwörter („ei“) treffen nur als ganzes Wort, sonst wäre „Brei“ ein Ei. */
    private const val MIN_ENDUNG = 4

    private val gewichte: Map<String, BigDecimal> = buildMap {
        fun eintrag(gramm: String, vararg woerter: String) = woerter.forEach { put(it, BigDecimal(gramm)) }
        // Fertiggerichte und Imbiss
        eintrag("350", "döner", "doener", "dürüm", "dürum", "kebab", "pizza")
        eintrag("250", "burger", "hamburger", "cheeseburger", "wrap")
        eintrag("150", "hotdog", "currywurst", "frühlingsrolle")
        eintrag("200", "leberkässemmel", "fischbrötchen", "sandwich")
        eintrag("120", "muffin", "berliner", "krapfen", "donut")
        eintrag("60", "brezelstange")
        eintrag("50", "schokoriegel")
        eintrag("25", "riegel", "müsliriegel")
        // Eier
        eintrag("60", "ei", "eier", "hühnerei", "hühnereier", "vollei")
        eintrag("18", "eigelb")
        eintrag("33", "eiweiß", "eiklar")
        // Gemüse
        eintrag("80", "zwiebel", "zwiebeln", "karotte", "karotten", "möhre", "möhren", "gelbe rübe", "gelbrübe")
        eintrag("250", "gemüsezwiebel", "gemüsezwiebeln")
        eintrag("15", "frühlingszwiebel", "frühlingszwiebeln", "lauchzwiebel", "lauchzwiebeln")
        eintrag("25", "schalotte", "schalotten")
        eintrag("4", "knoblauch", "knoblauchzehe", "knoblauchzehen", "zehe")
        eintrag("50", "knoblauchknolle")
        eintrag("100", "tomate", "tomaten", "petersilienwurzel", "rübe", "weiße rübe")
        eintrag("15", "cherrytomate", "cherrytomaten", "kirschtomate", "kirschtomaten")
        eintrag("150", "kartoffel", "kartoffeln", "pellkartoffel", "paprika", "paprikaschote", "paprikaschoten", "rote bete",
            "pastinake", "chicorée", "pak choi", "avocado")
        eintrag("300", "aubergine", "kohlrabi")
        eintrag("200", "zucchini", "lauch", "porree", "lauchstange", "porreestange")
        eintrag("25", "schnittlauch")
        eintrag("50", "bärlauch")
        eintrag("400", "gurke", "salatgurke", "brokkoli", "suppengrün")
        eintrag("40", "gewürzgurke", "gewürzgurken", "staudensellerie")
        eintrag("600", "knollensellerie", "sellerie", "sellerieknolle")
        eintrag("800", "wirsing", "chinakohl")
        eintrag("1000", "weißkohl", "rotkohl", "blaukraut")
        eintrag("500", "eisbergsalat")
        eintrag("300", "blattsalat", "kopfsalat")
        eintrag("250", "fenchel")
        eintrag("1200", "hokkaido", "hokkaidokürbis", "kürbis")
        eintrag("20", "champignon", "champignons", "spargel", "spargelstange")
        eintrag("100", "portobello", "portobellopilz", "portobellopilze")
        eintrag("12", "chili", "chilischote", "chilischoten", "peperoni")
        eintrag("10", "radieschen", "maiskölbchen", "okraschote")
        eintrag("250", "maiskolben")
        eintrag("30", "ingwer", "ingwerwurzel")
        eintrag("15", "zitronengras")
        // Obst
        eintrag("150", "apfel", "äpfel", "pfirsich", "nektarine")
        eintrag("120", "banane", "bananen", "zitrone", "zitronen")
        eintrag("170", "birne", "birnen")
        eintrag("200", "orange", "orangen")
        eintrag("350", "grapefruit")
        eintrag("70", "mandarine", "mandarinen", "clementine", "clementinen")
        eintrag("60", "limette", "limetten")
        eintrag("75", "kiwi")
        eintrag("400", "mango")
        eintrag("600", "ananas")
        eintrag("1500", "melone", "honigmelone")
        eintrag("35", "pflaume", "pflaumen", "zwetschge", "zwetschgen", "aprikose", "aprikosen")
        eintrag("15", "erdbeere", "erdbeeren")
        eintrag("6", "kirsche", "kirschen")
        eintrag("5", "traube", "trauben", "weintraube", "weintrauben")
        // Brot und Gebäck
        eintrag("60", "brötchen", "semmel", "semmeln", "burgerbrötchen", "croissant", "tortilla")
        eintrag("70", "laugenbrötchen", "pitabrot", "pita")
        eintrag("80", "brezel", "breze", "brezn")
        eintrag("65", "vollkornbrötchen")
        eintrag("50", "scheibe brot", "brotscheibe", "vollkornbrot")
        eintrag("25", "toast", "toastbrot", "toastscheibe")
        eintrag("90", "bagel", "naan", "naanbrot")
        eintrag("250", "baguette")
        eintrag("300", "ciabatta")
        eintrag("400", "fladenbrot")
        eintrag("10", "knäckebrot", "zwieback")
        eintrag("12", "keks", "kekse")
        eintrag("6", "butterkeks", "löffelbiskuit", "löffelbiskuits")
        eintrag("25", "lebkuchen")
        eintrag("20", "schokokuss", "schokoküsse")
        // Fleisch, Wurst, Fisch
        eintrag("150", "hähnchenbrust", "hühnerbrust", "putenbrust", "schnitzel", "schweineschnitzel", "putenschnitzel",
            "kalbsschnitzel", "rinderroulade", "roulade", "kasseler", "kotelett")
        eintrag("200", "hähnchenkeule", "hühnerkeule", "rindersteak", "steak")
        eintrag("300", "entenkeule", "entenbrust")
        eintrag("100", "bratwurst", "bratwürste", "frikadelle", "frikadellen", "leberkäse")
        eintrag("50", "wiener", "wiener würstchen", "würstchen", "frankfurter", "wienerle")
        eintrag("70", "weißwurst", "weißwürste")
        eintrag("125", "lachsfilet", "kabeljaufilet", "fischfilet", "seelachsfilet")
        eintrag("500", "schweinefilet")
        eintrag("200", "rinderbäckchen")
        eintrag("1200", "schweinshaxe", "haxe")
        eintrag("1500", "suppenhuhn", "kaninchen")
        eintrag("2000", "lammkeule")
        eintrag("80", "lammkotelett")
        eintrag("15", "garnele", "garnelen")
        eintrag("25", "scheibe schinken", "kochschinken", "schinkenscheibe")
        eintrag("10", "speckscheibe", "bacon")
        eintrag("6", "salamischeibe")
        // Milchprodukte
        eintrag("125", "mozzarella", "camembert", "brie")
        eintrag("20", "mini-mozzarella", "minimozzarella")
        eintrag("25", "käsescheibe", "scheibe käse")
        eintrag("150", "joghurt", "skyr", "fruchtjoghurt", "vanillejoghurt")
        eintrag("200", "feta", "schafskäse", "frischkäse", "schmelzkäse", "hüttenkäse", "becher sahne")
        eintrag("100", "ziegenkäse")
        // Sonstiges
        eintrag("3", "vanilleschote", "zimtstange")
        eintrag("0.2", "lorbeerblatt", "lorbeerblätter", "kardamomkapsel")
        eintrag("0.1", "nelke", "gewürznelke", "wacholderbeere", "pimentkorn")
        eintrag("0.3", "sternanis")
        eintrag("1", "zweig", "thymianzweig", "rosmarinzweig")
        eintrag("4", "olive", "oliven", "sardellenfilet")
        eintrag("8", "vanillezucker", "backpulver")
        eintrag("42", "hefewürfel", "würfel hefe")
        eintrag("200", "tofu")
        eintrag("100", "tafel schokolade", "schokolade")
        eintrag("50", "kugel eis")
    }

    /** Alle Stichwörter mit ihrem Gewicht (für Tests). */
    fun alle(): Map<String, BigDecimal> = gewichte

    /** Geschätzte Gramm je Stück für [name]; `null`, wenn kein Stichwort passt. */
    fun gramm(name: String): BigDecimal? {
        val kern = name.substringBefore(',').lowercase(Locale.GERMAN).trim()
        if (kern.isEmpty()) return null
        val woerter = kern.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return gewichte.entries
            .filter { (stichwort, _) -> passt(stichwort, kern, woerter) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    private fun passt(stichwort: String, kern: String, woerter: List<String>): Boolean = when {
        // Mehrere Wörter („wiener würstchen“): als Wortfolge im Namen
        stichwort.contains(' ') || stichwort.contains('-') -> " ${woerter.joinToString(" ")} ".contains(" ${stichwort.replace('-', ' ')} ") ||
            kern.contains(stichwort)
        else -> woerter.any { it == stichwort || (stichwort.length >= MIN_ENDUNG && it.endsWith(stichwort)) }
    }
}
