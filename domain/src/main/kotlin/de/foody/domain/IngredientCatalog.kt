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
        alias("Ei", "Eier", "Ei Gr. M", "Eier Gr. M", "Ei Gr. L", "Eier Gr. L", "Ei, Größe M", "Eier, Größe M")
        alias("Tomate", "Tomaten")
        alias("Dosentomaten", "Tomaten, geschälte", "Tomaten aus der Dose", "Pizzatomaten", "Tomaten, stückige")
        alias("Rinderhack", "Rinderhackfleisch", "Hackfleisch vom Rind")
        alias("Hackfleisch, gemischt", "Hackfleisch", "Hackfleisch, gemischtes", "Gehacktes")
        alias("Hähnchenbrust", "Hähnchenbrustfilet", "Hähnchenbrustfilets", "Hühnerbrust", "Hühnerbrustfilet", "Hähnchenfilet")
        alias("Olivenöl", "Olivenöl, extra vergine", "Olivenöl extra vergine", "Natives Olivenöl")
        alias("Pflanzenöl", "Öl", "Rapsöl", "Sonnenblumenöl", "Speiseöl", "Bratöl")
        alias("Sahne", "Schlagsahne", "süße Sahne")
        alias("Salz", "Meersalz", "Jodsalz", "Speisesalz")
        alias("Pfeffer", "Pfeffer, schwarz", "schwarzer Pfeffer", "Pfeffer aus der Mühle")
        alias("Zucker", "weißer Zucker", "Kristallzucker")
        alias("Wasser", "Leitungswasser")
        // Fertige Brühen liegen nährwertlich alle bei wenigen kcal je 100 ml – eine Stammzutat genügt
        alias("Gemüsebrühe", "Gemüsefond", "Brühe")
        // Eigene Stammzutat trotz gleicher Nährwerte: Fleischbrühe macht ein Rezept nicht-vegetarisch
        alias("Fleischbrühe", "Rinderbrühe", "Geflügelbrühe", "Hühnerbrühe", "Rinderfond", "Kalbsfond", "Wildfond", "Entenfond", "Lammfond")
        alias("Kokosmilch", "Kokosmilch, ungesüßt")
        alias("Eigelb", "Eigelbe")
        // Version 4: häufigste Zutaten der importierten Rezepte ohne Nährwerte
        alias("Pfeffer", "Pfefferkörner", "Pfeffer, weiß", "weißer Pfeffer")
        alias("Hefe", "Frischhefe", "Backhefe", "Hefewürfel")
        alias("Trockenhefe", "Trockenbackhefe")
        alias("Ingwer", "Ingwerwurzel", "Ingwer, frisch")
        alias("Crème fraîche", "Creme fraiche", "Crème fraiche", "Creme fraîche")
        alias("Chilischote", "Chili", "Peperoni, rot", "Chilischote, rot")
        alias("Lorbeerblatt", "Lorbeerblätter", "Lorbeer")
        alias("Pimentkörner", "Piment", "Pimentkorn")
        alias("Gemüsebrühepulver", "Brühpulver", "gekörnte Brühe", "Gemüsebrühe, instant", "Instant-Gemüsebrühe")
        alias("Quark", "Magerquark", "Speisequark")
        alias("Joghurt", "Naturjoghurt", "Joghurt, natur")
        alias("Champignons", "Champignon", "Pilze, braune")
        alias("Frühlingszwiebel", "Lauchzwiebel", "Lauchzwiebeln")
        alias("Lauch", "Porree")
        alias("Speisestärke", "Maisstärke", "Stärke")
        alias("Vanillepuddingpulver", "Puddingpulver, Vanille", "Puddingpulver")
        alias("Sojasauce", "Sojasoße", "Sojasauce, helle", "Sojasauce, dunkle")
        alias("Sesamöl", "Sesamöl, geröstet")
        alias("Knollensellerie", "Sellerie", "Sellerieknolle")
        alias("Butterschmalz", "Ghee", "Schweineschmalz", "Schmalz")
        alias("Mozzarella", "Büffelmozzarella")
        alias("Parmesan", "Parmigiano", "Parmesankäse", "Grana Padano")
        alias("Rotwein", "Rotwein, trocken")
        alias("Weißwein", "Weißwein, trocken")
        alias("Eiweiß", "Eiklar")
        alias("Schnittlauch", "Schnittlauchröllchen")
        alias("Semmelbrösel", "Paniermehl", "Panko")
        alias("Brötchen", "Semmel", "Semmeln", "Weizenbrötchen")
        alias("Rindfleisch", "Rindergulasch", "Gulasch, Rind", "Rinderbraten")
        alias("Schweinebraten", "Schweinenacken", "Schweineschulter", "Schweinenacken, ohne Knochen")
        alias("Schweinefilet", "Schweinelende")
        alias("Kochschinken", "Schinken", "Schinken, gekocht")
        alias("Balsamico", "Aceto Balsamico", "Balsamicoessig")
        alias("Brokkoli", "Broccoli")
        alias("Hokkaidokürbis", "Kürbis", "Hokkaido")
        // Version 5
        alias("Weizenmehl", "Weizenmehl Type 812", "Weizenmehl Type 1050", "Mehl Type 1050")
        alias("Roggenmehl", "Roggenmehl Type 1150", "Roggenmehl Type 997")
        alias("Dinkelmehl", "Dinkelmehl Type 630", "Dinkelmehl Type 1050")
        alias("Brötchen", "Knödelbrot", "Semmel Brötchen", "Semmel Knödelbrot", "altbackene Brötchen", "Toastbrot")
        alias("Gouda", "Käse", "Käse Gouda", "Käse, gerieben")
        alias("Emmentaler", "Käse Emmentaler", "Bergkäse")
        alias("Feta", "Feta-Käse", "Schafskäse", "Hirtenkäse")
        alias("Frischkäse", "Doppelrahmfrischkäse", "Ziegenfrischkäse", "Frischkäse natur")
        alias("Schmelzkäse", "Sahneschmelzkäse", "Kräuterschmelzkäse")
        alias("Mozzarella", "Mini-Mozzarella", "Mozzarella Mini")
        alias("Saure Sahne", "saure Sahne", "Sauerrahm")
        alias("Joghurt", "Sahnejoghurt", "griechischer Joghurt")
        alias("Butter", "Butterflöckchen")
        alias("Bier", "Schwarzbier", "Bier dunkles", "dunkles Bier", "Weißbier")
        alias("Rotwein", "Rotwein süß")
        alias("Schweineschnitzel", "Schnitzel", "Schweineschnitzel, dünn")
        alias("Putenbrust", "Putenfleisch", "Putenschnitzel")
        alias("Kalbfleisch", "Kalbsschnitzel")
        alias("Hähnchenbrust", "Hühnerfleisch", "Hähnchenfleisch")
        alias("Rindfleisch", "Rinderbäckchen", "Tatar", "Rinderhüfte", "Suppenfleisch")
        alias("Rindersteak", "Rumpsteak", "Hüftsteak", "Entrecôte")
        alias("Rinderroulade", "Rinderrouladen")
        alias("Hackfleisch, gemischt", "Schweinehackfleisch", "Schweinehack")
        alias("Schweinebraten", "Schweinegulasch", "Gulasch")
        alias("Wildfleisch", "Wildschweinfleisch", "Rehfleisch", "Hirschfleisch", "Wildgulasch")
        alias("Entenkeule", "Ente Keule", "Keule Entenkeulen", "Entenkeulen")
        alias("Bratwurst", "Salsiccia", "Lammbratwürste", "Bratwürste")
        alias("Wiener Würstchen", "Würstchen", "Wiener")
        alias("Speck", "Bacon", "Frühstücksspeck", "Schinkenwürfel", "Speckwürfel")
        alias("Rohschinken", "Parmaschinken", "Serranoschinken")
        alias("Lachs", "Lachsfilet", "Lachsfilet TK")
        alias("Kabeljau", "Kabeljaufilet", "Seelachsfilet")
        alias("Zucchini", "Zucchinis")
        alias("Gurke", "Salatgurke")
        alias("Tomate", "Kirschtomate", "Cocktailtomaten", "Datteltomate", "Flaschentomate San-Marzano-Tomaten", "Rispentomaten")
        alias("Zitrone", "Bio-Zitrone", "Zitronen")
        alias("Zitronensaft", "Limettensaft")
        alias("Apfel", "Äpfel")
        alias("Spinat", "Blattspinat", "Babyspinat")
        alias("Champignons", "Pilze", "Pfifferlinge", "Steinpilze", "Kräuterseitling", "Portobellopilze", "Shiitake",
            "Champignons und Shitake")
        alias("Lauch", "Lauchstange")
        alias("Thymian", "Thymianzweig")
        alias("Rosmarin", "Rosmarinzweig")
        alias("Kakaopulver", "Backkakao", "Kakao")
        alias("Schokolade", "Zartbitterschokolade", "Kuvertüre", "Zartbitterkuvertüre")
        alias("Vanillezucker", "Vanillinzucker")
        alias("Zucker", "Hagelzucker", "brauner Zucker", "Rohrzucker")
        alias("Speisestärke", "Speisestärke Mondamin")
        alias("Grieß", "Hartweizengrieß", "Weichweizengrieß")
        alias("Polenta", "Maismehl", "Maisgrieß")
        alias("Nudeln (roh)", "Lasagneplatte", "Lasagneplatten", "Ramen-Nudeln", "chinesische Eiernudeln", "Eiernudeln")
        alias("Mandeln", "Mandel", "gemahlene Mandeln")
        alias("Haselnüsse", "gemahlene Haselnüsse")
        alias("Tofu", "Naturtofu")
        alias("Ahornsirup", "Rübensirup")
        alias("Konfitüre", "Marmelade", "Gelee", "Gelee Johannisbeer")
        alias("Essig", "Rotweinessig", "Weißweinessig", "Reisessig", "Apfelessig")
        alias("Kaffee", "Espresso")
        alias("Chilipulver", "Chiliflocken", "Cayennepfeffer", "Chilifäden")
        alias("Kreuzkümmel", "Kreuzkümmelpulver", "Kreuzkümmelsamen", "Cumin", "Kreuzkümmelpulver Cumin")
        alias("Koriander", "Korianderpulver", "Koriandersamen")
        alias("Kurkuma", "Kurkumapulver", "Kurkuma Pulver")
        alias("Kardamom", "Kardamomkapsel", "Kardamompulver")
        alias("Nelke", "Gewürznelke", "Nelken")
        alias("Muskat", "Muskatnuss")
        alias("Zimt", "Zimtstange")
        alias("Wacholderbeere", "Wacholderbeeren")
        alias("Fleischbrühepulver", "Hühnerbrühepulver", "Fleischbrühe Instant", "Rinderbrühepulver")
        alias("Brot", "Bauernbrot", "Mischbrot", "Roggenbrot", "Weißbrot")
        alias("Gewürzgurke", "Gewürzgurken", "Essiggurke", "Essiggurken")
        // v8: Zutaten mit Werten aus BLS 4.0 / USDA (SeedData) und ihre Schreibweisen in importierten Rezepten
        alias("Suppengrün", "Suppengemüse", "Suppengemüse Karotten")
        alias("Kartoffel", "Pellkartoffel", "Pellkartoffeln")
        alias("Oliven", "Oliven Taggiasca Oliven", "Taggiasca Oliven")
        alias("Basilikum", "Thai-Basilikum")
        alias("Gemüsebrühepulver", "Brühepulver")
        alias("Fett", "Bratfett", "Frittierfett", "Fett für die Form")
        alias("Kräutersalz", "Gewürzsalz", "Bratengewürzsalz", "Gewürzmischung Bratengewürzsalz")
        alias("Wasabipaste", "Wasabi", "Wasabi-Paste")
        alias("Barbecuesauce", "BBQ-Sauce", "Barbecue-Sauce", "Grillsauce")
        alias("Gelatine", "Blattgelatine", "Gelatine, weiß")
        alias("Schokostreusel", "Schokoladenstreusel")
        alias("Schokoladenpuddingpulver", "Schokopuddingpulver", "Puddingpulver Schokolade")
        alias("Kuchenglasur", "Fettglasur")
        alias("Keks", "Kekse", "Cookies")
        alias("Croûtons", "Croutons")
        alias("Kartoffelpufferteig", "HENGLEIN Kartoffelpufferteig")
        alias("Eierspätzle", "Spätzle", "HENGLEIN Frische Eierspätzle", "Frische Eierspätzle")
        alias("Graukäse", "Tiroler Graukäse")
        alias("Räßkäse", "Rässkäse")
        alias("Entenfond", "Fond Entenfond")
        alias("Bambussprossen", "Bambussprosse")
        alias("Mungobohnensprossen", "Mungbohnensprossen", "Sojasprossen")
        alias("Weiße Rübe", "Rübe", "Rüben", "Rübchen", "Mairübe", "Mairübchen", "Speiserübe")
        alias("Zuckerschote", "Zuckerschoten", "Zuckererbse", "Zuckererbsen", "Kaiserschoten")
        alias("Chicorée", "Chicoree")
        alias("Okraschote", "Okraschoten", "Okra")
        alias("Palmenherzen", "Palmenherz")
        alias("Blattsalat", "Kopfsalat")
        alias("Kräuter", "Wildkräuter", "gemischte Kräuter", "frische Kräuter")
        alias("Wermut", "Noilly Prat", "Wermutwein")
        alias("Oregano", "Oregano, getrocknet")
        alias("Anis", "Anissamen")
        alias("Fenchelsamen", "Fenchelsaat")
        alias("Senfkörner", "Senfsaat", "Senfsamen")
        alias("Natron", "Kaiser-Natron", "Speisenatron")
        alias("Teriyakisauce", "Teriyaki-Sauce", "Teriyaki Sauce")
        alias("Zitronenschale", "Bio-Zitronenschale", "Zitronenabrieb", "abgeriebene Zitronenschale", "Zitronenzesten")
        alias("Orangenschale", "Orange Abrieb", "Orangenabrieb", "Bio-Orangenschale", "Orangenzesten")
        alias("Koriandergrün", "Korianderblätter", "frischer Koriander", "Koriander, frisch")
        alias("Strudelteig", "Filoteig", "Yufkateig", "HENGLEIN Frischer Strudelteig", "Frischer Strudelteig")
    }

    /**
     * Typische Packungsgröße in Gramm – „1 Pck. Vanillezucker“ = 8 g. Faustwerte gängiger Marken; ohne Eintrag
     * lässt sich eine Packung nicht in Gramm umrechnen (Nährwert dann unvollständig statt falsch).
     */
    private val packageGrams = mapOf(
        "vanillezucker" to "8", "backpulver" to "15", "hefe" to "7", "trockenhefe" to "7",
        "vanillepuddingpulver" to "37", "gemüsebrühepulver" to "10",
    )

    /** Typischer Doseninhalt in Gramm (Abtropfgewicht bei Hülsenfrüchten und Mais). */
    private val canGrams = mapOf(
        "dosentomaten" to "400", "tomate" to "400", "kokosmilch" to "400",
        "kidneybohnen" to "250", "kichererbsen" to "240", "mais" to "285", "weiße bohnen" to "250",
    )

    fun packageWeightG(name: String): java.math.BigDecimal? = packageGrams[key(canonicalName(name))]?.toBigDecimal()
    fun canWeightG(name: String): java.math.BigDecimal? = canGrams[key(canonicalName(name))]?.toBigDecimal()

    private val neverBuyKeys = setOf("wasser", "leitungswasser", "eiswürfel", "heißes wasser", "kaltes wasser")

    private fun key(name: String): String =
        name.trim().lowercase().replace(Regex("""\s+"""), " ").replace(" ,", ",")

    /** Größenangaben wie „Größe M“, „Gr. L“, „Kl. M“ – für die Zuordnung ohne Bedeutung. */
    private val sizeSuffix = Regex("""[, ]+(gr\.|größe|kl\.)\s*(s|m|l|xl)$""")

    /** Kanonischer Name für [name]; unbekannte Namen bleiben (getrimmt) unverändert. */
    fun canonicalName(name: String): String {
        val k = key(name).replace(sizeSuffix, "")
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

    private val basicKeys = setOf("salz", "pfeffer", "salz und pfeffer", "zucker", "olivenöl", "pflanzenöl")

    /** Grundzutaten, die man zu Hause voraussetzen darf (Wasser, Salz, Pfeffer, Zucker, Speiseöl) – auch ohne Vorratseintrag. */
    fun assumedAtHome(name: String): Boolean = neverBuy(name) || key(canonicalName(name)) in basicKeys

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
