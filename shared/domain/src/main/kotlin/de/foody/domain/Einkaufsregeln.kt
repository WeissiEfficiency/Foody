package de.foody.domain

import java.time.LocalDate

/**
 * Einkaufsbedarf für einen Planzeitraum: [GenerateShoppingListUseCase] plus Ausschluss von allem, was nie eingekauft
 * wird (Leitungswasser & Co.).
 */
object EinkaufsBedarf {
    private val generate = GenerateShoppingListUseCase()

    fun berechnen(
        range: DateRange,
        slots: List<MealSlot>,
        recipes: Map<String, Recipe>,
        ingredients: Map<String, Ingredient>,
        ausgeschlossen: Set<String> = emptySet(),
    ): List<ShoppingNeed> {
        val nieKaufen = ingredients.values.filter { IngredientCatalog.neverBuy(IngredientCatalog.canonicalName(it.name)) }.map { it.id }
        return generate(range, slots, recipes, ingredients, excludedIngredientIds = ausgeschlossen + nieKaufen)
    }
}

/** Regeln beim Übernehmen einer Neuberechnung in eine bestehende Liste. */
object EinkaufsAbgleich {
    /** Ein abgehakter Eintrag kommt nur zurück auf die Liste, wenn jetzt mehr (oder in anderer Dimension) gebraucht wird. */
    fun bleibtAbgehakt(warAbgehakt: Boolean, alt: Quantity, neu: Quantity): Boolean =
        warAbgehakt && alt.dimension == neu.dimension && neu.baseAmount <= alt.baseAmount

    /** Entfallene Einträge werden nur gelöscht, solange sie nicht schon gekauft sind. */
    fun entfaelltLoeschen(warAbgehakt: Boolean): Boolean = !warAbgehakt
}

/**
 * Die Zutaten eines Rezepts direkt auf eine Liste, ohne Umweg über den Planer. Gleiche Zutat + Dimension wird auf einen
 * offenen manuellen Eintrag aufaddiert; abgehakte und aus dem Plan erzeugte Einträge bleiben unberührt.
 */
object RezeptAufListe {
    data class Bestand(val id: String, val ingredientId: String?, val menge: Quantity?, val manuell: Boolean, val abgehakt: Boolean)

    sealed interface Aenderung {
        data class Erhoehen(val id: String, val neu: Quantity) : Aenderung
        data class Neu(val bedarf: ShoppingNeed) : Aenderung
    }

    /** Bedarf für [portionen] ohne „nach Bedarf“, optionale Zutaten und Nie-Kaufen; nur, was wirklich zu kaufen ist. */
    fun bedarf(rezept: Recipe, portionen: Int, ingredients: Map<String, Ingredient>, heute: LocalDate): List<ShoppingNeed> =
        EinkaufsBedarf.berechnen(
            DateRange.ofDays(heute, 1),
            listOf(MealSlot("recipe:${rezept.id}", heute, "", rezept.id, portionen)),
            mapOf(rezept.id to rezept),
            ingredients,
        ).filter { !it.toBuy.isZero() }

    fun zusammenfuehren(bedarf: List<ShoppingNeed>, bestand: List<Bestand>): List<Aenderung> = bedarf.map { n ->
        val passend = bestand.firstOrNull {
            it.manuell && !it.abgehakt && it.ingredientId == n.ingredientId && it.menge != null && it.menge.dimension == n.toBuy.dimension
        }
        if (passend != null) Aenderung.Erhoehen(passend.id, passend.menge!! + n.toBuy) else Aenderung.Neu(n)
    }
}

/**
 * Freitext „Was willst du einkaufen?“: Ein bekannter Artikel verhält sich wie seine Kachel (offen → gekauft, gekauft →
 * wieder auf die Liste, sonst neu in seiner Abteilung); unbekannter Text wird ein eigener Artikel.
 */
object EinkaufEingabe {
    data class Eintrag(val id: String, val name: String, val abgehakt: Boolean)

    sealed interface Aktion {
        data class Abhaken(val id: String) : Aktion
        data class Zurueck(val id: String) : Aktion
        data class Neu(val name: String, val abteilung: String) : Aktion
    }

    fun entscheiden(text: String, eintraege: List<Eintrag>): Aktion? {
        val name = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val bekannt = ShoppingCatalog.find(name) ?: return Aktion.Neu(name, ShoppingCatalog.OWN_ITEMS)
        val vorhanden = eintraege.filter { ShoppingCatalog.sameItem(it.name, bekannt.name) }
        vorhanden.firstOrNull { !it.abgehakt }?.let { return Aktion.Abhaken(it.id) }
        vorhanden.firstOrNull { it.abgehakt }?.let { return Aktion.Zurueck(it.id) }
        return Aktion.Neu(bekannt.name, ShoppingCatalog.sectionFor(null, bekannt.name))
    }
}
