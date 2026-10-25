package de.foody.core.data.einkauf

import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import java.time.LocalDate

/** Ein Artikel einer Einkaufsliste, fertig für die Kachel. */
data class EinkaufsArtikel(
    val id: String,
    val name: String,
    val emoji: String,
    val abteilung: String,
    /** „300 g“; `null` ohne Mengenangabe. */
    val menge: String?,
    val notiz: String?,
    val gekauft: Boolean,
    /** Aus dem Plan berechnet (Menge bestimmt der Abgleich). */
    val plan: Boolean,
    /** „200 g Curry (Mo) + 100 g Reispfanne (Mi)“; `null` ohne Herkunft. */
    val herkunft: String?,
    val ingredientId: String?,
)

/** Offene Artikel einer Abteilung. */
data class Abteilung(val name: String, val emoji: String, val artikel: List<EinkaufsArtikel>)

/** Eine Einkaufsliste: offen nach Abteilungen in Laufreihenfolge, dazu alle Artikel (auch gekaufte). */
data class EinkaufsAnsicht(
    val id: String,
    val name: String,
    val von: LocalDate?,
    val bis: LocalDate?,
    val offen: List<Abteilung>,
    val alle: List<EinkaufsArtikel>,
) {
    val anzahlOffen: Int get() = offen.sumOf { it.artikel.size }
}

/** Ein zuletzt gekaufter Artikel (über alle Listen). */
data class ZuletztArtikel(val name: String, val emoji: String, val abteilung: String, val ingredientId: String?)

/** Gelöschter Artikel samt Herkunft, für „Rückgängig“. */
data class GeloeschterArtikel(val artikel: EinkaufsartikelEntity, val herkunft: List<EinkaufsartikelQuelleEntity>)

/** Kopf einer früheren Liste. */
data class ListenKopf(val id: String, val name: String, val angelegt: LocalDate, val artikel: Int)
