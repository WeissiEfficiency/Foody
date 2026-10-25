package de.foody.feature.kochen.timer

/**
 * Ein gestarteter Kochtimer. [rezeptId]/[portionen] führen aus der Benachrichtigung zurück in den Kochmodus;
 * [gemeldet]: das Ende wurde schon benachrichtigt (nach einem Neustart nicht noch einmal).
 */
data class LaufenderTimer(
    val id: String,
    val rezeptId: String,
    val portionen: Int,
    val rezeptName: String,
    val label: String,
    val endeUm: Long,
    val gemeldet: Boolean = false,
) {
    fun abgelaufen(jetzt: Long) = endeUm <= jetzt
}
