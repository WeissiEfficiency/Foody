package de.foody.feature.heute

import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.domain.Mahlzeit
import java.time.LocalDateTime
import java.time.LocalTime

/** Die Mahlzeit für die große Karte; [morgen], wenn heute nichts mehr offen ist. */
data class NaechsteMahlzeit(val mahlzeit: GeplanteMahlzeit, val morgen: Boolean)

private val FRUEHSTUECK_ENDE = LocalTime.of(10, 0)
private val MITTAGESSEN_ENDE = LocalTime.of(14, 30)
private val SNACK_ENDE = LocalTime.of(17, 0)
private val TAG_ENDE = LocalTime.of(23, 59)

/** Ende des Zeitfensters eines Slots: Frühstück 10:00, Mittagessen 14:30, Snack 17:00, sonst 23:59. */
fun fensterEnde(slot: String): LocalTime = when (Mahlzeit.ausText(slot)) {
    Mahlzeit.FRUEHSTUECK -> FRUEHSTUECK_ENDE
    Mahlzeit.MITTAGESSEN -> MITTAGESSEN_ENDE
    Mahlzeit.SNACK -> SNACK_ENDE
    Mahlzeit.ABENDESSEN, null -> TAG_ENDE
}

/** Mahlzeit, deren Zeitfenster [zeit] enthält (Vorbelegung beim Eintragen). */
fun mahlzeitNachUhrzeit(zeit: LocalTime): Mahlzeit = when {
    zeit < FRUEHSTUECK_ENDE -> Mahlzeit.FRUEHSTUECK
    zeit < MITTAGESSEN_ENDE -> Mahlzeit.MITTAGESSEN
    zeit < SNACK_ENDE -> Mahlzeit.SNACK
    else -> Mahlzeit.ABENDESSEN
}

/**
 * Der erste nicht gekochte Eintrag von heute, dessen Fenster noch nicht vorbei ist (Ende ausgeschlossen: um 10:00 ist
 * das Frühstück vorbei), nach Fensterende; sonst der erste von morgen; sonst `null`. „Heute“ ist der Tag von [jetzt].
 */
fun naechsteMahlzeit(plan: List<GeplanteMahlzeit>, jetzt: LocalDateTime): NaechsteMahlzeit? {
    val heute = jetzt.toLocalDate()
    val offen = plan.filter { !it.gekocht }.sortedBy { fensterEnde(it.slot) }
    offen.firstOrNull { it.datum == heute && jetzt.toLocalTime() < fensterEnde(it.slot) }?.let { return NaechsteMahlzeit(it, morgen = false) }
    return offen.firstOrNull { it.datum == heute.plusDays(1) }?.let { NaechsteMahlzeit(it, morgen = true) }
}

/** Anzeigename einer Mahlzeit. */
fun Mahlzeit.anzeigename(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittagessen"
    Mahlzeit.SNACK -> "Snack"
    Mahlzeit.ABENDESSEN -> "Abendessen"
}

/** Anzeigename eines Plan-Slots; alte Freitexte bleiben, wie sie sind. */
fun slotName(slot: String): String = Mahlzeit.ausText(slot)?.anzeigename() ?: slot
