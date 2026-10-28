package de.foody.core.data.db

/**
 * Leert die Fachtabellen, Kinder zuerst; mit [mitZutaten] auch den Zutatenkatalog. Eine neue Fachtabelle kommt nur hier
 * dazu: Die Sicherung ersetzt alles, das Verwerfen beim Trennen vom Server behält die Zutaten. Nur in einer Transaktion
 * aufrufen.
 */
suspend fun FoodyDatenbank.fachdatenLeeren(mitZutaten: Boolean) {
    tagebuchDao().alleLoeschen()
    einkaufsartikelQuelleDao().alleLoeschen()
    einkaufsartikelDao().alleLoeschen()
    einkaufslisteDao().alleLoeschen()
    planEintragDao().alleLoeschen()
    arbeitsschrittDao().alleLoeschen()
    rezeptZutatDao().alleLoeschen()
    rezeptDao().alleLoeschen()
    if (mitZutaten) zutatDao().alleLoeschen()
}
