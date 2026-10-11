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

    /** Einheit als Symbol („g“, „EL“, „Stk.“, Groß-/Kleinschreibung egal) oder interner Name („GRAM“). */
    fun einheit(text: String): MeasureUnit? = text.trim().let { e ->
        MeasureUnit.entries.firstOrNull { it.name == e } ?: MeasureUnit.entries.firstOrNull { it.symbol.equals(e, ignoreCase = true) }
    }

    /** Zahl für ein Formularfeld: unrund, mit Komma (wie der Rezept-Editor). */
    fun anzeigen(wert: BigDecimal): String = wert.stripTrailingZeros().toPlainString().replace('.', ',')
}
