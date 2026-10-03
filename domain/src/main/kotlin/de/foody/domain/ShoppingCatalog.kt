package de.foody.domain

/**
 * Artikelkatalog für die Kachel-Einkaufsliste: Abteilungen in Laufreihenfolge durch den Supermarkt, je Artikel
 * ein Emoji. Kacheln und Listeneinträge werden über den kanonischen Namen verglichen – die Kachel „Zwiebeln“
 * ist rot, wenn der Planer „Zwiebel“ auf die Liste gesetzt hat.
 */
object ShoppingCatalog {
    data class Item(val name: String, val emoji: String)
    data class Section(val name: String, val emoji: String, val items: List<Item>)

    const val OWN_ITEMS = "Eigene Artikel"
    private const val FALLBACK_EMOJI = "🛒"

    private fun section(name: String, emoji: String, vararg items: Pair<String, String>) =
        Section(name, emoji, items.map { (n, e) -> Item(n, e) })

    val sections: List<Section> = listOf(
        section(
            "Obst & Gemüse", "🥕",
            "Äpfel" to "🍎", "Bananen" to "🍌", "Birnen" to "🍐", "Orangen" to "🍊", "Zitronen" to "🍋", "Limetten" to "🍋",
            "Trauben" to "🍇", "Erdbeeren" to "🍓", "Heidelbeeren" to "🫐", "Kirschen" to "🍒", "Pfirsiche" to "🍑",
            "Mango" to "🥭", "Ananas" to "🍍", "Kiwi" to "🥝", "Melone" to "🍈", "Wassermelone" to "🍉", "Avocado" to "🥑",
            "Tomaten" to "🍅", "Cherrytomaten" to "🍅", "Gurke" to "🥒", "Paprika" to "🫑", "Zucchini" to "🥒",
            "Aubergine" to "🍆", "Karotten" to "🥕", "Kartoffeln" to "🥔", "Süßkartoffeln" to "🍠", "Zwiebeln" to "🧅",
            "Frühlingszwiebeln" to "🧅", "Knoblauch" to "🧄", "Ingwer" to "🌱", "Brokkoli" to "🥦", "Blumenkohl" to "🥦",
            "Spinat" to "🥬", "Salat" to "🥬", "Rucola" to "🥬", "Kohlrabi" to "🥬", "Lauch" to "🥬", "Sellerie" to "🥬",
            "Champignons" to "🍄", "Mais" to "🌽", "Chili" to "🌶️", "Kürbis" to "🎃", "Radieschen" to "🌱",
            "Petersilie" to "🌿", "Basilikum" to "🌿", "Schnittlauch" to "🌿", "Thymian" to "🌿", "Rosmarin" to "🌿",
            "Koriander" to "🌿",
        ),
        section(
            "Brot & Gebäck", "🍞",
            "Brot" to "🍞", "Brötchen" to "🥖", "Baguette" to "🥖", "Toast" to "🍞", "Croissants" to "🥐", "Brezeln" to "🥨",
            "Knäckebrot" to "🍞", "Wraps" to "🌯", "Blätterteig" to "🥐", "Pizzateig" to "🍕",
        ),
        section(
            "Milch & Käse", "🧀",
            "Milch" to "🥛", "Hafermilch" to "🥛", "Butter" to "🧈", "Margarine" to "🧈", "Eier" to "🥚", "Joghurt" to "🥛",
            "Quark" to "🥛", "Sahne" to "🥛", "Schmand" to "🥛", "Crème fraîche" to "🥛", "Frischkäse" to "🧀",
            "Käse" to "🧀", "Reibekäse" to "🧀", "Mozzarella" to "🧀", "Parmesan" to "🧀", "Feta" to "🧀", "Mascarpone" to "🧀",
        ),
        section(
            "Fleisch & Fisch", "🥩",
            "Hähnchenbrust" to "🍗", "Hähnchen" to "🍗", "Hackfleisch" to "🥩", "Rindfleisch" to "🥩", "Gulaschfleisch" to "🥩",
            "Schweinefleisch" to "🥩", "Schnitzel" to "🥩", "Steak" to "🥩", "Speck" to "🥓", "Schinken" to "🥓",
            "Salami" to "🍖", "Aufschnitt" to "🍖", "Würstchen" to "🌭", "Bratwurst" to "🌭", "Lachs" to "🐟",
            "Fisch" to "🐟", "Thunfisch" to "🐟", "Garnelen" to "🦐",
        ),
        section(
            "Zutaten & Gewürze", "🧂",
            "Mehl" to "🌾", "Zucker" to "🍬", "Puderzucker" to "🍬", "Vanillezucker" to "🍬", "Backpulver" to "🧁",
            "Hefe" to "🍞", "Speisestärke" to "🌽", "Salz" to "🧂", "Pfeffer" to "🧂", "Paprikapulver" to "🌶️",
            "Zimt" to "🧂", "Oregano" to "🌿", "Öl" to "🫒", "Olivenöl" to "🫒", "Essig" to "🍶", "Balsamico" to "🍶",
            "Sojasauce" to "🍶", "Senf" to "🧂", "Ketchup" to "🍅", "Mayonnaise" to "🥚", "Tomatenmark" to "🍅",
            "Dosentomaten" to "🥫", "Passierte Tomaten" to "🥫", "Kokosmilch" to "🥥", "Brühe" to "🍲", "Honig" to "🍯",
            "Marmelade" to "🍓", "Nuss-Nougat-Creme" to "🍫", "Nüsse" to "🥜", "Mandeln" to "🌰", "Walnüsse" to "🌰",
            "Pinienkerne" to "🌰", "Semmelbrösel" to "🍞", "Kaffee" to "☕", "Tee" to "🍵",
        ),
        section(
            "Fertig- & Tiefkühlprodukte", "🧊",
            "Pizza" to "🍕", "Pommes" to "🍟", "Gemüse, tiefgekühlt" to "🥦", "Fischstäbchen" to "🐟", "Eis" to "🍨",
            "Lasagne" to "🍝", "Knödel" to "🥟", "Maultaschen" to "🥟", "Suppe" to "🍲", "Pesto" to "🌿",
        ),
        section(
            "Getreideprodukte", "🌾",
            "Nudeln" to "🍝", "Reis" to "🍚", "Risottoreis" to "🍚",
            "Couscous" to "🌾", "Haferflocken" to "🥣", "Müsli" to "🥣", "Cornflakes" to "🥣", "Gnocchi" to "🥔",
            "Linsen" to "🌱", "Kichererbsen" to "🌱", "Bohnen" to "🌱", "Tofu" to "🍱",
        ),
        section(
            "Snacks & Süßwaren", "🍫",
            "Schokolade" to "🍫", "Kekse" to "🍪", "Löffelbiskuits" to "🍪", "Chips" to "🥔", "Salzstangen" to "🥨",
            "Erdnüsse" to "🥜", "Gummibärchen" to "🍬", "Müsliriegel" to "🍫", "Popcorn" to "🍿", "Kuchen" to "🍰",
        ),
        section(
            "Getränke", "🥤",
            "Wasser" to "💧", "Mineralwasser" to "💧", "Apfelsaft" to "🧃", "Orangensaft" to "🧃", "Cola" to "🥤",
            "Limonade" to "🥤", "Eistee" to "🥤", "Bier" to "🍺", "Rotwein" to "🍷", "Weißwein" to "🥂", "Sekt" to "🍾",
            "Kakao" to "🍫",
        ),
        section(
            "Haushalt", "🧽",
            "Toilettenpapier" to "🧻", "Küchenrolle" to "🧻", "Taschentücher" to "🧻", "Servietten" to "🧻",
            "Spülmittel" to "🧴", "Geschirrtabs" to "🧽", "Schwämme" to "🧽", "Putzmittel" to "🧴", "Waschmittel" to "🧺",
            "Weichspüler" to "🧺", "Müllbeutel" to "🗑️", "Alufolie" to "📦", "Frischhaltefolie" to "📦", "Backpapier" to "📜",
            "Batterien" to "🔋", "Glühbirne" to "💡", "Kerzen" to "🕯️",
        ),
        section(
            "Pflege & Gesundheit", "🧴",
            "Zahnpasta" to "🪥", "Zahnbürste" to "🪥", "Shampoo" to "🧴", "Duschgel" to "🧴", "Deo" to "🧴", "Seife" to "🧼",
            "Handcreme" to "🧴", "Sonnencreme" to "🧴", "Pflaster" to "🩹", "Rasierer" to "🪒", "Vitamine" to "💊",
            "Schmerzmittel" to "💊", "Windeln" to "👶",
        ),
        section(
            "Tierbedarf", "🐾",
            "Katzenfutter" to "🐱", "Katzenstreu" to "🐱", "Hundefutter" to "🐶", "Leckerli" to "🦴", "Vogelfutter" to "🐦",
        ),
        section(
            "Baumarkt & Garten", "🪴",
            "Blumenerde" to "🪴", "Pflanzen" to "🪴", "Dünger" to "🌱", "Holzkohle" to "🔥", "Grillanzünder" to "🔥",
            "Schrauben" to "🔩",
        ),
    )

    /** Abteilungsreihenfolge inkl. „Eigene Artikel“ am Ende – so läuft man den Laden einmal ab. */
    val sectionOrder: List<String> = sections.map { it.name } + OWN_ITEMS

    private fun matchKey(name: String) = IngredientCatalog.canonicalName(name).lowercase()

    private val byKey: Map<String, Pair<Item, Section>> = buildMap {
        for (s in sections) for (i in s.items) putIfAbsent(matchKey(i.name), i to s)
    }

    /** Gleicher Artikel? („Zwiebeln“ ↔ „Zwiebel“, „Mehl“ ↔ „Weizenmehl“). */
    fun sameItem(a: String, b: String): Boolean = matchKey(a) == matchKey(b)

    fun find(name: String): Item? = byKey[matchKey(name)]?.first

    fun emojiFor(name: String, category: String? = null): String =
        byKey[matchKey(name)]?.first?.emoji
            ?: sections.firstOrNull { it.name == sectionFor(category, name) }?.emoji
            ?: FALLBACK_EMOJI

    /**
     * Abteilung eines Listeneintrags: zuerst über den Katalog, sonst über die Zutatenkategorie aus der Datenbank
     * (die ältere, feinere Einteilung wird auf die Kachel-Abteilungen abgebildet), sonst „Eigene Artikel“.
     */
    fun sectionFor(category: String?, name: String): String =
        byKey[matchKey(name)]?.second?.name
            ?: when (category) {
                null -> null
                in sectionOrder -> category
                "Kühlregal" -> "Milch & Käse"
                "Öle & Gewürze", "Konserven", "Backzutaten" -> "Zutaten & Gewürze"
                "Trockenwaren" -> "Getreideprodukte"
                "Brot & Backwaren" -> "Brot & Gebäck"
                else -> null
            }
            ?: OWN_ITEMS

    /** Katalogsuche für „Was willst du einkaufen?“: Treffer am Wortanfang zuerst. */
    fun search(query: String): List<Pair<Item, Section>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return sections.flatMap { s -> s.items.map { it to s } }
            .filter { (i, _) -> q in i.name.lowercase() }
            .sortedBy { (i, _) -> if (i.name.lowercase().startsWith(q)) 0 else 1 }
    }
}
