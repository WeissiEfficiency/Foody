package de.foody.feature.einkauf

import de.foody.core.data.einkauf.AbgleichZeile
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.VorschauZeile
import de.foody.core.data.einkauf.ZuletztArtikel
import java.time.LocalDate

/** Das offene Blatt; im `SavedStateHandle` als Text ([text], [ausText]). */
sealed interface Blatt {
    data object Vorschau : Blatt

    data object Abgleich : Blatt

    data class Details(val id: String) : Blatt

    data object Frueher : Blatt

    val text: String
        get() = when (this) {
            Vorschau -> "vorschau"
            Abgleich -> "abgleich"
            Frueher -> "frueher"
            is Details -> "details:$id"
        }

    companion object {
        fun ausText(t: String?): Blatt? = when {
            t == null -> null
            t == "vorschau" -> Vorschau
            t == "abgleich" -> Abgleich
            t == "frueher" -> Frueher
            t.startsWith("details:") -> Details(t.removePrefix("details:"))
            else -> null
        }
    }
}

/** „Neue Liste aus dem Plan“: Zeitraum, Zeilen (`null` = wird gerechnet) und abgewählte Schlüssel. */
data class VorschauZustand(val von: LocalDate, val bis: LocalDate, val zeilen: List<VorschauZeile>?, val abgewaehlt: Set<String>)

/**
 * Was der Einkauf-Bildschirm zeigt: die Liste ([ansicht], `null` = keine), [listeFehlt], wenn eine bestimmte Liste
 * verlangt war und es sie nicht (mehr) gibt; [abgleich] nicht leer = Hinweis „Plan geändert – abgleichen“.
 */
data class EinkaufZustand(
    val ansicht: EinkaufsAnsicht? = null,
    val geladen: Boolean = false,
    val listeFehlt: Boolean = false,
    val zuletzt: List<ZuletztArtikel> = emptyList(),
    val abgleich: List<AbgleichZeile> = emptyList(),
    val suche: String = "",
)
