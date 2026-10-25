package de.foody.app3.navigation

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * Typisierte Routen der V3 (Navigation Compose mit kotlinx.serialization). Deep Links (`foody://`) siehe FoodyNavHost.
 */

@Serializable data object Heute

@Serializable data object Rezepte

@Serializable data object Woche

@Serializable data object Einkauf

@Serializable data object Einstellungen

@Serializable data class RezeptDetail(val id: String)

@Serializable data object RezeptNeu

/** [gekochtMit]: aus dem Kochmodus mit dieser Portionenzahl (Hinweis im Editor). */
@Serializable data class RezeptBearbeiten(val id: String, val gekochtMit: Int? = null)

/** [portionen] `null` = Portionen des Rezepts. */
@Serializable data class Kochen(val rezeptId: String, val portionen: Int? = null)

@Serializable data class EinkaufListe(val listeId: String)

/** [datum] als ISO-Datum (`2026-10-24`); siehe [tagebuchDatum]. */
@Serializable data class TagebuchTag(val datum: String)

/** ISO-Datum aus einem Link; Ungültiges (`gestern`, `2026-02-30`) zeigt [heute] statt abzustürzen. */
fun tagebuchDatum(text: String, heute: LocalDate): LocalDate =
    try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        heute
    }
