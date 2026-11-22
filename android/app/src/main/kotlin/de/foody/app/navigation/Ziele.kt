package de.foody.app.navigation

import de.foody.domain.Mahlzeit
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.SofortStart
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/*
 * Typisierte Routen der V3 (Navigation Compose mit kotlinx.serialization). Deep Links (`foody://`) siehe FoodyNavHost.
 */

@Serializable data object Heute

@Serializable data object Rezepte

@Serializable data object Woche

@Serializable data object Einkauf

@Serializable data object Einstellungen

@Serializable data object ServerVerbinden

/** Zutatenkatalog (aus den Einstellungen). */
@Serializable data object Zutaten

@Serializable data class Zutat(val id: String)

@Serializable data class RezeptDetail(val id: String)

@Serializable data object RezeptNeu

/** [gekochtMit]: aus dem Kochmodus mit dieser Portionenzahl (Hinweis im Editor). */
@Serializable data class RezeptBearbeiten(val id: String, val gekochtMit: Int? = null)

/** [portionen] `null` = Portionen des Rezepts. */
@Serializable data class Kochen(val rezeptId: String, val portionen: Int? = null)

@Serializable data class EinkaufListe(val listeId: String)

/** [datum] als ISO-Datum (`2026-10-24`); siehe [tagebuchDatum]. */
@Serializable data class TagebuchTag(val datum: String)

/**
 * Eintragen als eigene Seite (aus Heute und dem Tagebuch): [datum] als ISO-Datum, [mahlzeit] und [sofort] als Namen von
 * [Mahlzeit] bzw. [SofortStart] (`null` = nach Uhrzeit bzw. erst Erfassen). [auftrag] ist je Öffnen neu, damit ein
 * Neuaufbau die Eingaben stehen lässt; siehe [alsZiel] und [alsStart].
 */
@Serializable data class Eintragen(val datum: String, val mahlzeit: String? = null, val sofort: String? = null, val auftrag: String)

/** Der Start als Ziel mit einem neuen [auftrag]. */
fun EintragenStart.alsZiel(auftrag: String = UUID.randomUUID().toString()): Eintragen =
    Eintragen(datum.toString(), mahlzeit?.name, sofort?.name, auftrag)

/** Umkehrung von [alsZiel]: Datum wie [tagebuchDatum]; unbekannte Mahlzeit bzw. unbekannter Sofort-Start → `null`. */
fun Eintragen.alsStart(heute: LocalDate): EintragenStart = EintragenStart(
    tagebuchDatum(datum, heute),
    Mahlzeit.entries.firstOrNull { it.name == mahlzeit },
    SofortStart.entries.firstOrNull { it.name == sofort },
)

/** ISO-Datum aus einem Link; Ungültiges (`gestern`, `2026-02-30`) zeigt [heute] statt abzustürzen. */
fun tagebuchDatum(text: String, heute: LocalDate): LocalDate =
    try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        heute
    }
