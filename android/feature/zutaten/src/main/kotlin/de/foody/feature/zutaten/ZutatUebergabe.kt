package de.foody.feature.zutaten

import de.foody.core.data.zutaten.NAME_MAX
import de.foody.core.data.zutaten.Packungswerte
import de.foody.core.data.zutaten.nameBereinigt
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weitergabe von „Neue Zutat“ an den Editor (Phase 6e3): eine gescannte Packung („Gibt es schon“ → „Öffnen“) oder eine
 * Meldung („Schon im Katalog“). Die Liste legt ab, bevor sie den Editor öffnet; dessen ViewModel holt einmal ab.
 */
@Singleton
class ZutatUebergabe @Inject constructor() {
    private val offen = ConcurrentHashMap<String, Pair<Packungswerte?, String?>>()

    fun ablegen(id: String, packung: Packungswerte?, meldung: String?) {
        offen[id] = packung to meldung
    }

    /** Packung und Meldung für den Editor von [id]; danach ist nichts mehr da. */
    fun abholen(id: String): Pair<Packungswerte?, String?>? = offen.remove(id)
}

/** Vorschlag fürs Namensfeld aus dem Produktnamen: bereinigt, höchstens [NAME_MAX] Zeichen; ohne Namen leer. */
internal fun nameAusPackung(p: Packungswerte): String = nameBereinigt(p.name.orEmpty()).take(NAME_MAX)
