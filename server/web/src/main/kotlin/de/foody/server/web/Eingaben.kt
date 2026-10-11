package de.foody.server.web

import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.ZahlGrenzen
import java.math.BigDecimal

/** Gemeinsame Leseregeln für Zahl- und Einheitenfelder aller Formulare (Rezept-Editor, Vorrat, Zutaten, Tagebuch). */
internal object Eingaben {
    /** Zahl ≥ 0 mit Komma oder Punkt in den Grenzen von [ZahlGrenzen]; leer oder ungültig = `null`. */
    fun zahl(text: String): BigDecimal? = when {
        text.isBlank() -> null
        // Wie `parseNichtNegativ` der App: keine Exponent-Schreibweise („1e5“) in Eingabefeldern
        text.contains('e', ignoreCase = true) -> null
        else -> ZahlGrenzen.lesen(text.trim().replace(',', '.'))?.takeIf { it.signum() >= 0 }
    }

    /** Ausgeschriebene und übliche Kurzformen (klein, ohne Punkt) → Einheit. */
    private val SCHREIBWEISEN: Map<String, MeasureUnit> = mapOf(
        "gramm" to MeasureUnit.GRAM, "gr" to MeasureUnit.GRAM,
        "kilogramm" to MeasureUnit.KILOGRAM, "kilo" to MeasureUnit.KILOGRAM,
        "milligramm" to MeasureUnit.MILLIGRAM,
        "liter" to MeasureUnit.LITER, "milliliter" to MeasureUnit.MILLILITER, "zentiliter" to MeasureUnit.CENTILITER,
        "stück" to MeasureUnit.PIECE, "stueck" to MeasureUnit.PIECE, "stk" to MeasureUnit.PIECE, "st" to MeasureUnit.PIECE,
        "esslöffel" to MeasureUnit.TABLESPOON, "essloeffel" to MeasureUnit.TABLESPOON,
        "teelöffel" to MeasureUnit.TEASPOON, "teeloeffel" to MeasureUnit.TEASPOON,
        "packung" to MeasureUnit.PACKAGE, "packungen" to MeasureUnit.PACKAGE, "pck" to MeasureUnit.PACKAGE, "päckchen" to MeasureUnit.PACKAGE,
        "dosen" to MeasureUnit.CAN,
    )

    /**
     * Einheit als Symbol („g“, „EL“, „Stk.“), interner Name („GRAM“) oder ausgeschrieben („Gramm“, „Stück“, „Esslöffel“);
     * Groß-/Kleinschreibung und ein Schlusspunkt sind egal.
     */
    fun einheit(text: String): MeasureUnit? = text.trim().let { e ->
        MeasureUnit.entries.firstOrNull { it.name == e }
            ?: MeasureUnit.entries.firstOrNull { it.symbol.equals(e, ignoreCase = true) }
            ?: e.lowercase().removeSuffix(".").let { k -> SCHREIBWEISEN[k] ?: MeasureUnit.entries.firstOrNull { it.symbol.lowercase().removeSuffix(".") == k } }
    }

    /** Zahl für ein Formularfeld: unrund, mit Komma (wie der Rezept-Editor). */
    fun anzeigen(wert: BigDecimal): String = wert.stripTrailingZeros().toPlainString().replace('.', ',')
}
