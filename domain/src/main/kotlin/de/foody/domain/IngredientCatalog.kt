package de.foody.domain

/**
 * Vereinheitlicht Zutatennamen aus Importen, damit „Mehl“, „Knoblauchzehe“ oder „Zwiebeln“ auf die
 * Stammzutat mit Nährwerten und Umrechnungsdaten zeigen. Bewusst nur über eine feste Synonymliste und
 * einfache Pluralformen – kein Ähnlichkeitsvergleich (Invariante 7: nie über Freitextähnlichkeit).
 */
object IngredientCatalog {

    /** Synonym (normalisiert) → kanonischer Name. Die kanonischen Namen entsprechen den Startdaten. */
    private val aliases: Map<String, String> = buildMap {
        fun alias(canonical: String, vararg names: String) {
            put(key(canonical), canonical)
            names.forEach { put(key(it), canonical) }
        }
        alias("Weizenmehl", "Mehl", "Weizenmehl Type 405", "Mehl Type 405", "Mehl Type 550", "Weizenmehl Type 550")
        alias("Milch 3,5 %", "Milch", "Vollmilch", "Frischmilch")
        alias("Reis (roh)", "Reis", "Langkornreis", "Basmatireis", "Jasminreis")
        alias("Nudeln (roh)", "Nudeln", "Spaghetti", "Penne", "Bandnudeln", "Makkaroni", "Fusilli", "Rigatoni", "Tagliatelle")
        alias("Knoblauch", "Knoblauchzehe", "Knoblauchzehen")
        alias("Paprika", "Paprikaschote", "Paprikaschoten")
        alias("Karotte", "Möhre", "Möhren", "Mohrrübe")
        alias("Kartoffel", "Kartoffeln", "Kartoffeln, festkochend", "Kartoffeln, mehligkochend")
        alias("Zwiebel", "Zwiebeln", "Gemüsezwiebel")
        alias("Ei", "Eier")
        alias("Tomate", "Tomaten")
        alias("Dosentomaten", "Tomaten, geschälte", "Tomaten aus der Dose", "Pizzatomaten", "Tomaten, stückige")
        alias("Rinderhack", "Rinderhackfleisch", "Hackfleisch vom Rind")
        alias("Hackfleisch, gemischt", "Hackfleisch", "Hackfleisch, gemischtes", "Gehacktes")
        alias("Hähnchenbrust", "Hähnchenbrustfilet", "Hähnchenbrustfilets", "Hühnerbrust", "Hühnerbrustfilet", "Hähnchenfilet")
        alias("Olivenöl", "Olivenöl, extra vergine", "Olivenöl extra vergine")
        alias("Pflanzenöl", "Öl", "Rapsöl", "Sonnenblumenöl", "Speiseöl", "Bratöl")
        alias("Sahne", "Schlagsahne", "süße Sahne")
        alias("Salz", "Meersalz", "Jodsalz", "Speisesalz")
        alias("Pfeffer", "Pfeffer, schwarz", "schwarzer Pfeffer", "Pfeffer aus der Mühle")
        alias("Zucker", "weißer Zucker", "Kristallzucker")
        alias("Wasser", "Leitungswasser")
        alias("Gemüsebrühe", "Gemüsefond")
        alias("Kokosmilch", "Kokosmilch, ungesüßt")
        alias("Eigelb", "Eigelbe")
    }

    private val neverBuyKeys = setOf("wasser", "leitungswasser", "eiswürfel", "heißes wasser", "kaltes wasser")

    private fun key(name: String): String =
        name.trim().lowercase().replace(Regex("""\s+"""), " ").replace(" ,", ",")

    /** Kanonischer Name für [name]; unbekannte Namen bleiben (getrimmt) unverändert. */
    fun canonicalName(name: String): String {
        val k = key(name)
        aliases[k]?.let { return it }
        // Einfache Pluralformen: „Zwiebeln“ → „Zwiebel“, „Tomaten“ → „Tomate“
        for (suffix in listOf("n", "en", "e", "s")) {
            if (k.length > suffix.length + 2 && k.endsWith(suffix)) aliases[k.dropLast(suffix.length)]?.let { return it }
        }
        return name.trim().replace(Regex("""\s+"""), " ")
    }

    /**
     * Alle bekannten Schreibweisen (klein) einer Zutat einschließlich des Namens selbst, z. B. für „Weizenmehl“
     * auch „mehl“ – damit Schritttexte („Mehl einrühren“) die vereinheitlichte Zutat finden.
     */
    fun synonymsOf(name: String): List<String> {
        val canonical = canonicalName(name)
        val own = key(name).replace(Regex("""\s*\([^)]*\)"""), "").trim() // „reis (roh)“ → „reis“
        return (listOf(key(name), own) + aliases.filterValues { it == canonical }.keys).distinct().filter { it.isNotBlank() }
    }

    /** Zutaten, die nie auf die Einkaufsliste gehören (z. B. Leitungswasser). */
    fun neverBuy(name: String): Boolean = key(name) in neverBuyKeys

    // Reihenfolge zählt: spezifischere Abteilungen zuerst („Kokosmilch“ ist Konserve, nicht Kühlregal)
    private val categoryRules = listOf(
        "Konserven" to listOf("dose", "mais", "kokosmilch", "tomatenmark", "passierte"),
        "Öle & Gewürze" to listOf("pulver", "gewürz", "pfeffer", "salz", "zimt", "muskat", "kümmel", "oregano", "thymian",
            "rosmarin", "lorbeer", "curry", "öl", "essig", "senf", "brühe", "fond", "sojasauce", "chili", "vanille"),
        "Fleisch & Fisch" to listOf("fleisch", "hack", "schwein", "rind", "kalb", "hähnchen", "huhn", "hühner", "pute", "ente",
            "wurst", "würstchen", "speck", "schinken", "lachs", "fisch", "thunfisch", "garnele", "braten", "filet", "keule", "gulasch"),
        "Kühlregal" to listOf("sahne", "schmand", "quark", "joghurt", "käse", "butter", "milch", "crème", "creme", "mozzarella",
            "parmesan", "feta", "frischkäse", "eigelb", "eiweiß"),
        "Obst & Gemüse" to listOf("zwiebel", "knoblauch", "tomate", "kartoffel", "karotte", "möhre", "paprika", "zucchini",
            "gurke", "salat", "spinat", "lauch", "porree", "sellerie", "kohl", "brokkoli", "pilz", "champignon", "apfel", "zitrone",
            "limette", "orange", "banane", "beere", "petersilie", "schnittlauch", "basilikum", "dill", "ingwer", "kürbis", "aubergine",
            "frühlingszwiebel", "rucola"),
        "Trockenwaren" to listOf("mehl", "zucker", "reis", "nudel", "spaghetti", "grieß", "haferflocken", "backpulver", "hefe",
            "stärke", "semmelbrösel", "linsen", "bohnen", "kichererbsen", "nüsse", "mandeln"),
    )

    /** Grobe Supermarkt-Abteilung per Stichwort; null, wenn nichts passt. */
    fun guessCategory(name: String): String? {
        val k = key(name)
        if (k in setOf("ei", "eier")) return "Kühlregal"
        return categoryRules.firstOrNull { (_, words) -> words.any { it in k } }?.first
    }
}
