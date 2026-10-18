package de.foody.core.sync

/** Ergebnis eines [SyncLauf]s. */
sealed interface SyncErgebnis {
    /** Sync ist ausgeschaltet; es wurde nichts gefragt und nichts geändert. */
    data object Inaktiv : SyncErgebnis

    /**
     * Lauf vollständig: [gesendet] vom Server angenommene oder zusammengeführte Datensätze, [geholt] vom Server geholte
     * Datensätze (beim Voll-Abgleich alle).
     */
    data class Fertig(val gesendet: Int, val geholt: Int) : SyncErgebnis

    /**
     * Lauf abgebrochen. [kennung] ist eine der festen Kennungen aus [ServerFehler] (so auch in `sync_state.lastError`);
     * [voruebergehend]: ein späterer Versuch kann ohne Zutun des Nutzers gelingen.
     */
    data class Fehlgeschlagen(val kennung: String, val voruebergehend: Boolean) : SyncErgebnis
}
