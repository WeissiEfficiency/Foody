package de.foody.core.scan

/** Texte des Scans (Spec 6e3, Abschnitt 4); wie in der V3 üblich als Konstanten statt Android-Ressourcen. */
object ScanTexte {
    const val KNOPF = "Packung scannen"
    const val STRICHCODE = "Strichcode"
    const val FOTO = "Foto der Nährwerttabelle"
    const val GALERIE = "Aus der Galerie"

    const val NICHT_VERFUEGBAR = "Scannen braucht die Google-Play-Dienste"
    const val WIRD_GELADEN = "Scanner wird vorbereitet – gleich noch einmal versuchen"
    const val NICHT_GEFUNDEN = "Produkt nicht gefunden – Foto der Nährwerttabelle?"
    const val OFFLINE = "Keine Verbindung – Foto der Nährwerttabelle?"
    const val ONLINE_AUS = "Online-Suche ist aus – Foto der Nährwerttabelle?"
    const val KEINE_TABELLE = "Keine Nährwerttabelle erkannt – bitte näher und gerade fotografieren"
    const val FEHLER = "Scannen hat nicht geklappt"

    /** Hinweis, wenn ein Strichcode keine Werte brachte. */
    fun nichtGefunden(grund: ScanAusgang.Grund): String = when (grund) {
        ScanAusgang.Grund.UNBEKANNT -> NICHT_GEFUNDEN
        ScanAusgang.Grund.OFFLINE -> OFFLINE
        ScanAusgang.Grund.ONLINE_AUS -> ONLINE_AUS
    }

    /** „Werte aus Open Food Facts – bitte prüfen“ bzw. „Werte aus dem Foto – bitte prüfen“. */
    fun bittePruefen(quelle: String): String = "Werte aus ${quelleText(quelle)} – bitte prüfen"

    /** „Werte aus Open Food Facts werden übernommen“ bzw. „Werte aus dem Foto werden übernommen“. */
    fun werdenUebernommen(quelle: String): String = "Werte aus ${quelleText(quelle)} werden übernommen"

    /** Die Foto-Quelle heißt im Satz „dem Foto“, andere Quellen stehen mit ihrem Namen. */
    private fun quelleText(quelle: String): String = if (quelle == PackungScan.QUELLE_FOTO) "dem Foto" else quelle
}
