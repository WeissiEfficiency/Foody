package de.foody.server.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Der Haushaltsstand wird je Revision gemerkt: unverändert → kein erneutes Laden und Umwandeln. */
class HaushaltsDatenTest {
    @Test fun zweiterAbrufNutztDenCache() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val daten = HaushaltsDaten(env.deps.db)
        assertEquals(setOf("r1"), daten.fuer(h).rezepte.keys)
        daten.fuer(h)
        assertEquals(1, daten.ladevorgaenge)
    }

    @Test fun nachPushNeuerStand() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val daten = HaushaltsDaten(env.deps.db)
        daten.fuer(h)
        env.push(h, rezept("r2", "Risotto"))
        assertEquals(setOf("r1", "r2"), daten.fuer(h).rezepte.keys)
        assertEquals(2, daten.ladevorgaenge)
    }

    @Test fun haushalteGetrennt() = webServer { env ->
        val (_, _, a) = env.haushalt("A")
        val (_, _, b) = env.haushalt("B")
        env.push(a, reis(), rezept("r1", "Curry"))
        val daten = HaushaltsDaten(env.deps.db)
        assertTrue(daten.fuer(b).rezepte.isEmpty())
        assertEquals(setOf("reis"), daten.fuer(a).zutaten.keys)
    }
}
