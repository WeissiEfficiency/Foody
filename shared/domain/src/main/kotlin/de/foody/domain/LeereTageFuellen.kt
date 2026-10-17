package de.foody.domain

import de.foody.domain.Abwechslung.Merkmal
import java.time.LocalDate
import kotlin.math.pow
import kotlin.random.Random

/**
 * „Leere Tage füllen“ für V3 (ohne Vorrat, ADR 0011): schlägt je leerem Tag ein Rezept für die gewählte Mahlzeit vor.
 *
 * 1. **Einordnung:** Zu Mittag- und Abendessen nur Hauptspeisen – Brotzeit, Dips oder Nachspeisen nie. Rezepte mit
 *    passender Mahlzeit und Hauptspeise kommen zuerst, dann solche ohne bekannten Gang, uneingeordnete nur als
 *    Lückenfüller.
 * 2. **Gewichtet statt sortiert:** Favoriten ×3, „Oft gekocht“ bis ×2 – bevorzugt, aber nicht jede Woche dieselben.
 * 3. **Abwechslung:** Gleiche Grundlage oder Eiweißquelle ([Abwechslung]) wie am Vortag, am selben Tag oder am Folgetag
 *    fällt weg, solange es Alternativen gibt; jede Wiederholung in der Woche halbiert das Gewicht.
 * 4. **Keine Wiederholung:** nichts, was [OHNE_WIEDERHOLUNG_TAGE] vor dem ersten leeren Tag oder später eingeplant ist,
 *    und kein Rezept zweimal im Vorschlag.
 *
 * Gleicher [seed] → gleiches Ergebnis, unabhängig von der Reihenfolge der Rezepte; „Neu mischen“ = anderer Seed.
 */
object LeereTageFuellen {
    data class Rezept(
        val id: String,
        val einordnung: Einordnung,
        val favorit: Boolean,
        /** Wie oft als „gekocht“ markiert. */
        val gekocht: Int,
        /** Letzter Planeintrag, auch in der Zukunft; null = nie. */
        val zuletztGeplant: LocalDate?,
        val merkmale: Set<Merkmal>,
    )

    const val OHNE_WIEDERHOLUNG_TAGE = 14L
    private const val FAVORIT = 3.0
    private const val GEKOCHT_VOLL_AB = 6
    private const val WIEDERHOLT_IN_DER_WOCHE = 0.5
    private val hauptmahlzeiten = setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN)

    /**
     * Vorschlag „Tag → Rezept-ID“ für die [leereTage]. [belegt] = Merkmale der schon geplanten Gerichte je Tag
     * (Zeitraum und Nachbartage), damit auch sie in die Abwechslung zählen.
     */
    fun vorschlagen(
        rezepte: List<Rezept>,
        leereTage: List<LocalDate>,
        mahlzeit: Mahlzeit,
        seed: Long,
        belegt: Map<LocalDate, Set<Merkmal>> = emptyMap(),
    ): Map<LocalDate, String> {
        if (leereTage.isEmpty()) return emptyMap()
        val tage = leereTage.sorted()
        val sperreBis = tage.first().minusDays(OHNE_WIEDERHOLUNG_TAGE)
        val stufen = rezepte
            .sortedBy { it.id } // unabhängig von der Reihenfolge der Datenbankabfrage
            .filter { it.zuletztGeplant == null || it.zuletztGeplant < sperreBis }
            .groupBy { stufe(it.einordnung, mahlzeit) }
        val reihen = listOfNotNull(stufen[1], stufen[2], stufen[3])

        val merkmaleAm = belegt.mapValues { it.value.toMutableSet() }.toMutableMap()
        val haeufigkeit = belegt.filterKeys { it in tage.first()..tage.last() }.values.flatten().groupingBy { it }.eachCount().toMutableMap()
        val vergeben = mutableSetOf<String>()
        val random = Random(seed)
        val ergebnis = linkedMapOf<LocalDate, String>()

        for (tag in tage) {
            val nachbarn = listOf(tag.minusDays(1), tag, tag.plusDays(1)).flatMap { merkmaleAm[it].orEmpty() }.toSet()
            val wahl = reihen.firstNotNullOfOrNull { reihe ->
                val frei = reihe.filter { it.id !in vergeben }
                ziehen(frei.filter { r -> r.merkmale.none { it in nachbarn } }.ifEmpty { frei }, haeufigkeit, random)
            } ?: continue
            ergebnis[tag] = wahl.id
            vergeben += wahl.id
            merkmaleAm.getOrPut(tag) { mutableSetOf() } += wahl.merkmale
            wahl.merkmale.forEach { haeufigkeit.merge(it, 1, Int::plus) }
        }
        return ergebnis
    }

    /** 1 = passt sicher, 2 = Gang unbekannt, 3 = nicht eingeordnet, null = passt nicht. */
    private fun stufe(e: Einordnung, mahlzeit: Mahlzeit): Int? {
        val hauptmahlzeit = mahlzeit in hauptmahlzeiten
        val keineHauptspeise = e.gaenge.isNotEmpty() && Gang.HAUPTSPEISE !in e.gaenge
        return when {
            hauptmahlzeit && keineHauptspeise -> null
            e.mahlzeiten.isEmpty() -> 3
            mahlzeit !in e.mahlzeiten -> null
            hauptmahlzeit && e.gaenge.isEmpty() -> 2
            else -> 1
        }
    }

    private fun gewicht(r: Rezept, haeufigkeit: Map<Merkmal, Int>): Double {
        val favorit = if (r.favorit) FAVORIT else 1.0
        val gekocht = 1.0 + r.gekocht.coerceIn(0, GEKOCHT_VOLL_AB).toDouble() / GEKOCHT_VOLL_AB
        val wiederholt = WIEDERHOLT_IN_DER_WOCHE.pow(r.merkmale.sumOf { haeufigkeit[it] ?: 0 })
        return favorit * gekocht * wiederholt
    }

    private fun ziehen(kandidaten: List<Rezept>, haeufigkeit: Map<Merkmal, Int>, random: Random): Rezept? {
        if (kandidaten.isEmpty()) return null
        val gewichte = kandidaten.map { gewicht(it, haeufigkeit) }
        var rest = random.nextDouble() * gewichte.sum()
        kandidaten.forEachIndexed { i, r ->
            rest -= gewichte[i]
            if (rest < 0) return r
        }
        return kandidaten.last()
    }
}
