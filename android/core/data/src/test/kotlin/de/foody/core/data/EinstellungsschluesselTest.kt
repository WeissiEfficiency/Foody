package de.foody.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die V3 übernimmt nach dem Update die Einstellungen der v1 aus denselben SharedPreferences. Ein Tippfehler in einem
 * Schlüssel würde die Einstellung still verlieren – deshalb hier jede Konstante gegen den Wert der v1.
 */
class EinstellungsschluesselTest {
    @Test
    fun gleicheSchluesselWieVEins() {
        assertEquals("foody", Einstellungsschluessel.PREFS)
        assertEquals("seedVersion", Einstellungsschluessel.SEED_VERSION)
        assertEquals("dailyKcalGoal", Einstellungsschluessel.TAGESZIEL)
        assertEquals("themeMode", Einstellungsschluessel.DARSTELLUNG)
        assertEquals("onlineProduktsuche", Einstellungsschluessel.ONLINE_SUCHE)
        assertEquals("fotosVerkleinert", Einstellungsschluessel.FOTOS_VERKLEINERT)
        assertEquals("recipe_images", Einstellungsschluessel.FOTO_ORDNER)
    }
}
