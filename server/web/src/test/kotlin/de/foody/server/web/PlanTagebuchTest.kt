package de.foody.server.web

import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.TagebuchPayload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertTrue

/** Heute ist laut Testuhr der 11.10.2026 (Sonntag). */
class PlanTagebuchTest {
    private fun slot(id: String, datum: String, mahlzeit: String, rezept: String = "r1", portionen: Int = 2) =
        datensatz(id, RecordType.MEAL_SLOT, MealSlotPayload(datum, mahlzeit, rezept, portionen))

    private fun eintrag(id: String, datum: String, mahlzeit: String, name: String, kj: String) =
        datensatz(id, RecordType.TAGEBUCH_EINTRAG, TagebuchPayload(datum = datum, mahlzeit = mahlzeit, art = "FREI", name = name, energieKj = kj))

    @Test fun planZeigtTageInReihenfolge() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), rezept("r2", "Porridge"),
            slot("s1", "2026-10-12", "ABENDESSEN", "r1"), slot("s2", "2026-10-12", "FRUEHSTUECK", "r2"))
        val c = ohneWeiterleitung()
        val html = c.get("/plan?ab=2026-10-12") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue(html.indexOf("Porridge") in 0 until html.indexOf("Curry"), "Frühstück vor Abendessen")
        assertTrue("Mo., 12.10." in html && "So., 18.10." in html, "7 Tage ab dem Datum")
        assertTrue("Sa., 11.10." !in html && "Mo., 19.10." !in html)
    }

    @Test fun planTagesKcalUndSchnitt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        // Ein Tag mit Abendessen + Mittagessen (je 350 kcal) zählt für den Schnitt, einer nur mit Abendessen nicht
        env.push(h, reis(), rezept("r1", "Curry"),
            slot("a", "2026-10-12", "ABENDESSEN"), slot("b", "2026-10-12", "MITTAGESSEN"), slot("c", "2026-10-13", "ABENDESSEN"))
        val c = ohneWeiterleitung()
        val html = c.get("/plan?ab=2026-10-12") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("700 kcal" in html, "Tag mit zwei Mahlzeiten")
        assertTrue("Ø 700 kcal pro Tag" in html, html)
    }

    @Test fun tagebuchSummeWieInDerApp() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        // 100,4 kcal je Eintrag: Zeilen 100 + 100, Summe 200 (nicht 201) – wie Tagebuch.bilanz
        val kj = (java.math.BigDecimal("100.4") * java.math.BigDecimal("4.184")).toPlainString()
        env.push(h, eintrag("t1", "2026-10-11", "SNACK", "Apfel", kj), eintrag("t2", "2026-10-11", "SNACK", "Birne", kj))
        val c = ohneWeiterleitung()
        val html = c.get("/tagebuch") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Apfel" in html && "Birne" in html && "Snack" in html)
        assertTrue("200 kcal" in html, html)
    }

    @Test fun tagebuchWochenleiste() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, eintrag("t1", "2026-10-09", "ABENDESSEN", "Pizza", "4184"))
        val c = ohneWeiterleitung()
        val html = c.get("/tagebuch?tag=2026-10-11") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("/tagebuch?tag=2026-10-05" in html && "/tagebuch?tag=2026-10-11" in html, "7 Tage bis heute")
        assertTrue("1000" in html, "kcal des 9.10. in der Leiste")
    }

    @Test fun leererHaushaltZeigtLeerenPlanUndTagebuch() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("Nichts geplant" in c.get("/plan") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue("Noch nichts eingetragen" in c.get("/tagebuch") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }
}
