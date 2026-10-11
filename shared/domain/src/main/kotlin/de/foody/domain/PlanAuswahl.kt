package de.foody.domain

/** Rezeptauswahl beim Einplanen: streng nach Mahlzeit und optional Gang, mit „Alle zeigen“ als Ausweg. */
object PlanAuswahl {
    fun <T> filtern(rezepte: List<T>, einordnung: (T) -> Einordnung, mahlzeit: Mahlzeit, gaenge: Set<Gang>, alle: Boolean): List<T> =
        if (alle) rezepte else rezepte.filter { einordnung(it).passtZu(mahlzeit, gaenge) }

    /** Was nach einem Filterwechsel nicht mehr sichtbar ist, bleibt nicht gewählt – sonst plante man Unsichtbares ein. */
    fun <T> auswahlBehalten(gewaehltId: String?, sichtbar: List<T>, id: (T) -> String): String? =
        gewaehltId?.takeIf { g -> sichtbar.any { id(it) == g } }
}
