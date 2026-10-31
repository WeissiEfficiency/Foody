package de.foody.core.data

/**
 * Namen der SharedPreferences, ihrer Schlüssel und des Fotoordners. Die Werte stammen aus der v1 und dürfen sich nie
 * ändern: Nach dem Update auf die V3 (gleiche App-ID) liest die App dieselben Einträge weiter.
 */
object Einstellungsschluessel {
    /** Name der SharedPreferences-Datei. */
    const val PREFS = "foody"

    /** Version der zuletzt eingespielten Startdaten (Int). */
    const val SEED_VERSION = "seedVersion"

    /** Alter Schlüssel (Boolean) vor [SEED_VERSION]; `true` entspricht Version 1. */
    const val SEEDED_ALT = "seeded"

    /** Tagesziel in kcal. */
    const val TAGESZIEL = "dailyKcalGoal"

    /** Hell/Dunkel/System. */
    const val DARSTELLUNG = "themeMode"

    /** Gewählte Farbwelt als Name (z. B. `MANGO`); neu in der V3, fehlt = Standard des Designsystems. */
    const val FARBWELT = "farbwelt"

    /** Ansicht der Einkaufsliste. */
    const val EINKAUF_ANSICHT = "shoppingListView"

    /** Ob beim Scannen online nach Produkten gesucht werden darf. */
    const val ONLINE_SUCHE = "onlineProduktsuche"

    /** Ob die vorhandenen Rezeptfotos bereits verkleinert wurden. */
    const val FOTOS_VERKLEINERT = "fotosVerkleinert"

    /** Ordner der Rezeptfotos im App-Speicher. */
    const val FOTO_ORDNER = "recipe_images"
}
