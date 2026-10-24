package de.foody.core.data.rezepte

import de.foody.core.data.db.RezeptEntity
import de.foody.domain.Diet
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.ImportedIngredient
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutritionResult
import java.math.BigDecimal

/** Ein Rezept für die Liste: alles, was Karte, Suche, Filter und Sortierung brauchen. */
data class RezeptKarte(
    val id: String,
    val name: String,
    val bild: String?,
    val kcalJePortion: Int?,
    val eiweissJePortion: Int?,
    val zeitMinuten: Int?,
    val favorit: Boolean,
    /** Anzahl gekochter Plan-Einträge („Oft gekocht“). */
    val gekocht: Int,
    val diaeten: Set<Diet>,
    val einordnung: Einordnung,
    val tags: List<String>,
    val zutatNamen: List<String>,
    val archiviert: Boolean,
    val angelegt: Long,
)

/** Eine Zutatzeile mit Namen; [hatNaehrwerte] für die Vollständigkeitsanzeige. */
data class RezeptZeile(
    val zutatId: String,
    val name: String,
    val menge: BigDecimal,
    val einheit: MeasureUnit,
    val hinweis: String?,
    val optional: Boolean,
    val hatNaehrwerte: Boolean,
)

/** Ein Rezept vollständig für das Detail. */
data class RezeptVoll(
    val rezept: RezeptEntity,
    val zeilen: List<RezeptZeile>,
    val schritte: List<String>,
    val naehrwerte: NutritionResult,
    val einordnung: Einordnung,
)

/** Tags aus dem gespeicherten Text: Komma-getrennt, getrimmt, ohne leere. */
internal fun tagsAus(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Was der Editor (und der Import) speichert; [mahlzeiten]/[gaenge] `null` = vermuten. */
data class RezeptEntwurf(
    val id: String?,
    val name: String,
    val portionen: Int,
    val vorbereitung: Int?,
    val kochzeit: Int?,
    val tags: String,
    val zeilen: List<ImportedIngredient>,
    val schritte: List<String>,
    val notizen: String?,
    val quelle: String?,
    val mahlzeiten: Set<Mahlzeit>?,
    val gaenge: Set<Gang>?,
    val bild: String?,
)

/** Ergebnis von [RezeptRepository.speichern]. */
sealed interface Gespeichert {
    data class Ok(val id: String) : Gespeichert

    /** Das bearbeitete Rezept gibt es nicht mehr (z. B. per Sync gelöscht); nichts gespeichert. */
    data object Geloescht : Gespeichert
}
