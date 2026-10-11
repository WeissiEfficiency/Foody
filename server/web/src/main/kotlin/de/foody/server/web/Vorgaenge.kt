package de.foody.server.web

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.html.FlowContent
import kotlinx.html.hiddenInput

/**
 * Schutz vor doppeltem Absenden (Doppelklick, Zurück + erneut senden): Jedes anlegende Formular trägt eine einmalige
 * Vorgangsnummer. Die erste Anfrage mit dieser Nummer schreibt; jede weitere leitet nur noch weiter. Gemerkt wird je
 * Haushalt und Weg für [GUELTIG_MS]; ohne Nummer (ältere Seite im Browser) gibt es keinen Schutz, aber auch keinen Fehler.
 */
internal object Vorgaenge {
    private const val GUELTIG_MS = 10 * 60 * 1000L
    private const val MAX_LAENGE = 64
    private const val AUFRAEUMEN_AB = 1_000

    private val gesehen = ConcurrentHashMap<String, Long>()

    fun neu(): String = UUID.randomUUID().toString()

    /** `true` = zum ersten Mal (jetzt schreiben); `false` = schon abgeschickt (nur weiterleiten). */
    fun erstmals(household: String, weg: String, nummer: String?, jetzt: Long): Boolean {
        if (nummer.isNullOrBlank() || nummer.length > MAX_LAENGE) return true
        if (gesehen.size > AUFRAEUMEN_AB) gesehen.entries.removeIf { jetzt - it.value > GUELTIG_MS }
        val alt = gesehen.putIfAbsent(schluessel(household, weg, nummer), jetzt) ?: return true
        if (jetzt - alt <= GUELTIG_MS) return false
        // Abgelaufen: wie neu behandeln
        gesehen[schluessel(household, weg, nummer)] = jetzt
        return true
    }

    /**
     * Schreibt nur beim ersten Absenden ([schreiben]); ein doppeltes Absenden liefert `null` (nur weiterleiten). Beides
     * läuft unter der [Haushaltssperre]: Das doppelte wartet, bis das erste fertig ist – die Seite danach zeigt also den
     * neuen Stand. Ist das Ergebnis nicht [ok] oder fliegt eine Ausnahme, wird die Nummer wieder freigegeben.
     */
    fun <T> einmal(household: String, weg: String, nummer: String?, jetzt: Long, ok: (T) -> Boolean, schreiben: () -> T): T? =
        Haushaltssperre.mit(household) {
            if (!erstmals(household, weg, nummer, jetzt)) return@mit null
            val ergebnis = try {
                schreiben()
            } catch (t: Throwable) {
                freigeben(household, weg, nummer)
                throw t
            }
            if (!ok(ergebnis)) freigeben(household, weg, nummer)
            ergebnis
        }

    /** Schreiben ist gescheitert: Die Nummer darf noch einmal verwendet werden (korrigiertes Formular). */
    fun freigeben(household: String, weg: String, nummer: String?) {
        if (!nummer.isNullOrBlank()) gesehen.remove(schluessel(household, weg, nummer))
    }

    private fun schluessel(household: String, weg: String, nummer: String) = "$household|$weg|$nummer"
}

/** Verstecktes Feld mit einer neuen Vorgangsnummer für ein anlegendes Formular. */
internal fun FlowContent.vorgangFeld() = hiddenInput(name = "vorgang") { value = Vorgaenge.neu() }
