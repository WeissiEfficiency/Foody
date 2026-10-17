package de.foody.server.ki

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Kontingent für KI-Anfragen: höchstens [proStunde] erfolgreiche Versuche je Konto in einem gleitenden Fenster
 * von 60 Minuten. Abgelehnte Versuche verbrauchen nichts. Threadsicher.
 */
class KiKontingent(private val clock: Clock, private val proStunde: Int) {
    private val versuche = HashMap<String, ArrayDeque<Instant>>()

    /** Verbucht einen Versuch für [kontoId]; `false`, wenn das Kontingent erschöpft ist. */
    @Synchronized
    fun versuche(kontoId: String): Boolean {
        val jetzt = clock.instant()
        val grenze = jetzt.minus(FENSTER)
        val liste = versuche.getOrPut(kontoId) { ArrayDeque() }
        while (liste.isNotEmpty() && !liste.first().isAfter(grenze)) liste.removeFirst()
        if (liste.size >= proStunde) return false
        liste.addLast(jetzt)
        return true
    }

    private companion object {
        val FENSTER: Duration = Duration.ofMinutes(60)
    }
}
