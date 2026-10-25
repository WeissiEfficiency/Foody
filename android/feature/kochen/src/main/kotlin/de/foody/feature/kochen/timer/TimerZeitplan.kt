package de.foody.feature.kochen.timer

/** Was der Timer-Dienst jetzt tun muss: [neuAbgelaufen] melden, bis [naechstesEnde] warten (`null` = nichts läuft). */
data class Zeitplan(val neuAbgelaufen: List<LaufenderTimer>, val naechstesEnde: Long?)

fun zeitplan(timer: List<LaufenderTimer>, jetzt: Long): Zeitplan = Zeitplan(
    neuAbgelaufen = timer.filter { it.abgelaufen(jetzt) && !it.gemeldet },
    naechstesEnde = timer.filterNot { it.abgelaufen(jetzt) }.minOfOrNull { it.endeUm },
)
