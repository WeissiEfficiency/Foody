package de.foody.server.web

import de.foody.domain.Ingredient
import de.foody.server.db.Database
import de.foody.server.sync.RecordStore
import java.util.concurrent.ConcurrentHashMap

/** Lesestand eines Haushalts als `domain`-Objekte; unveränderlich. [revision] = höchste Revision beim Laden. */
internal class Haushalt(
    val revision: Long,
    val zutaten: Map<String, Ingredient>,
    val rezepte: Map<String, WebRezept>,
    val plan: List<WebPlanEintrag>,
    val listen: List<WebEinkaufsliste>,
    val eintraege: List<WebEintrag>,
    val tagebuch: List<WebTagebuch>,
    val vorrat: List<WebVorrat> = emptyList(),
    val zutatenRoh: Map<String, WebZutat> = emptyMap(),
)

/**
 * Lädt den Stand eines Haushalts und merkt ihn sich bis zur nächsten Revision: Eine Seite kostet dann nur eine kleine
 * Abfrage (`MAX(rev)`), statt jedes Mal alle Datensätze zu lesen und umzuwandeln. Jedes Schreiben – vom Handy oder aus
 * dem Browser – erhöht die Revision und macht den gemerkten Stand ungültig.
 */
internal class HaushaltsDaten(private val db: Database, private val store: RecordStore = RecordStore()) {
    private val cache = ConcurrentHashMap<String, Haushalt>()

    /** Wie oft wirklich geladen wurde (für Tests). */
    @Volatile var ladevorgaenge = 0
        private set

    fun fuer(household: String): Haushalt {
        val aktuell = db.tx { c -> store.hoechsteRevision(c, household) }
        cache[household]?.takeIf { it.revision == aktuell }?.let { return it }
        val (records, revision) = db.tx { c -> store.lebende(c, household) }
        ladevorgaenge++
        val haushalt = Haushalt(
            revision = revision,
            zutaten = records.mapNotNull(PayloadZuDomain::zutat).associateBy { it.id },
            rezepte = records.mapNotNull(PayloadZuDomain::rezept).associateBy { it.id },
            plan = records.mapNotNull(PayloadZuDomain::planEintrag),
            listen = records.mapNotNull(PayloadZuDomain::liste),
            eintraege = records.mapNotNull(PayloadZuDomain::eintrag),
            tagebuch = records.mapNotNull(PayloadZuDomain::tagebuch),
            vorrat = records.mapNotNull(PayloadZuDomain::vorrat),
            zutatenRoh = records.mapNotNull(PayloadZuDomain::zutatRoh).associateBy { it.id },
        )
        cache[household] = haushalt
        return haushalt
    }
}
