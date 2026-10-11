package de.foody.server.web

import de.foody.domain.DateRange
import de.foody.domain.EinkaufsBedarf
import de.foody.domain.MealSlot
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Heute ist laut Testuhr der 11.10.2026; Rezept „r1“ = 200 g Reis für 2 Portionen. */
class ListeAusPlanTest {
    private fun slot(id: String, datum: String, rezept: String = "r1", portionen: Int = 2) =
        datensatz(id, RecordType.MEAL_SLOT, MealSlotPayload(datum, "ABENDESSEN", rezept, portionen))

    private fun vorrat(gramm: String) = datensatz("v1", RecordType.PANTRY_ITEM, PantryItemPayload("reis", gramm, "GRAM"))

    private suspend fun HttpClient.post(cookie: String, pfad: String, felder: List<Pair<String, String>>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    private fun anlegenFelder(vararg mit: String, vorrat: String = "1", name: String = "Einkauf 12.–18.10.") =
        listOf("name" to name, "von" to "2026-10-12", "bis" to "2026-10-18", "vorrat" to vorrat) + mit.map { "mit" to it }

    private fun eintraege(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h).let { d -> d.eintraege.filter { e -> e.listId == d.listen.single().id } }

    private fun menge(e: WebEintrag) = BigDecimal(e.payload.amount!!)

    @Test fun vorschauUndAnlegenWieInDerApp() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"), slot("s2", "2026-10-14", portionen = 4))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val vorschau = c.get("/einkauf/neu?von=2026-10-12&bis=2026-10-18&vorrat=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("value=\"Einkauf 12.–18.10.\"" in vorschau && "name=\"mit\"" in vorschau && "600 g" in vorschau, vorschau.take(2000))
        val antwort = c.post(cookie, "/einkauf/neu", anlegenFelder("reis"))
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(500))
        val haushalt = HaushaltsDaten(env.deps.db).fuer(h)
        val liste = haushalt.listen.single()
        assertEquals("/einkauf?liste=${liste.id}", antwort.headers[HttpHeaders.Location])
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18), liste.von to liste.bis)
        val erwartet = EinkaufsBedarf.berechnen(
            DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
            haushalt.plan.map { MealSlot(it.id, it.datum, it.mahlzeit, it.rezeptId, it.portionen) },
            haushalt.rezepte.mapValues { it.value.domain }, haushalt.zutaten, emptyList(),
        ).filter { !it.toBuy.isZero() }
        val e = eintraege(env, h).single()
        assertEquals(erwartet.single().ingredientId, e.payload.ingredientId)
        assertEquals(0, menge(e).compareTo(erwartet.single().toBuy.amountIn(MeasureUnit.GRAM)))
        assertEquals("GRAM", e.payload.unit)
        assertFalse(e.payload.manual)
        assertEquals(setOf("s1", "s2"), e.payload.sources.map { it.mealSlotId }.toSet())
        assertTrue(e.payload.sources.all { it.recipeName == "Curry" })
    }

    @Test fun abgewaehlteZutatFehlt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/einkauf/neu", anlegenFelder())
        assertTrue("Nichts zu kaufen" in antwort.bodyAsText())
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).listen.isEmpty())
    }

    @Test fun vorratWirdAbgezogen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12", portionen = 4), vorrat("150"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.post(cookie, "/einkauf/neu", anlegenFelder("reis"))
        assertEquals(0, menge(eintraege(env, h).single()).compareTo(BigDecimal(250)))
        val ohne = c.post(cookie, "/einkauf/neu", anlegenFelder("reis", vorrat = "0", name = "Ohne Vorrat"))
        val id = ohne.headers[HttpHeaders.Location]!!.substringAfter("liste=")
        val e = HaushaltsDaten(env.deps.db).fuer(h).eintraege.single { it.listId == id }
        assertEquals(0, menge(e).compareTo(BigDecimal(400)))
    }

    @Test fun nichtsZuKaufen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val vorschau = c.get("/einkauf/neu?von=2026-10-12&bis=2026-10-18") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Im Zeitraum ist nichts geplant" in vorschau && "Liste anlegen" !in vorschau)
        assertTrue("Nichts zu kaufen" in c.post(cookie, "/einkauf/neu", anlegenFelder("reis")).bodyAsText())
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).listen.isEmpty())
    }

    @Test fun abgleichZeigtUndUebernimmt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val nudeln = datensatz("nudeln", RecordType.INGREDIENT, de.foody.sync.protocol.IngredientPayload(name = "Nudeln"))
        val pasta = datensatz("r2", RecordType.RECIPE, de.foody.sync.protocol.RecipePayload(
            name = "Pasta", servings = 2, tags = "", favorite = false,
            lines = listOf(de.foody.sync.protocol.RecipePayload.Line("r2-l1", "nudeln", "250", "GRAM", 0, null, false)), steps = emptyList(),
        ))
        env.push(h, reis(), nudeln, rezept("r1", "Curry"), pasta, slot("s1", "2026-10-12", portionen = 4), slot("s2", "2026-10-13", rezept = "r2"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.post(cookie, "/einkauf/neu", anlegenFelder("reis", "nudeln"))
        val daten = HaushaltsDaten(env.deps.db)
        val listeId = daten.fuer(h).listen.single().id
        // Reis abhaken, Nudeln offen lassen; dann weniger Reis planen und Pasta streichen
        val reisEintrag = eintraege(env, h).single { it.payload.ingredientId == "reis" }
        val token = c.csrf(cookie)
        c.submitForm("/einkauf/${reisEintrag.id}/abhaken", parameters { append("_csrf", token); append("erledigt", "1") }) { header(HttpHeaders.Cookie, cookie) }
        env.push(h, slot("s1", "2026-10-12", portionen = 2).copy(baseRev = daten.fuer(h).plan.single { it.id == "s1" }.rev, updatedAt = 20),
            SyncRecord(id = "s2", type = RecordType.MEAL_SLOT, deleted = true, updatedAt = 20), slot("s3", "2026-10-15", rezept = "r2", portionen = 4))
        val anzeige = c.get("/einkauf/$listeId/abgleich?vorrat=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Geändert" in anzeige && "400 g → 200 g" in anzeige && "schon gekauft" in anzeige, anzeige.take(3000))
        assertTrue("250 g → 500 g" in anzeige, "Nudeln mehr")
        val antwort = c.post(cookie, "/einkauf/$listeId/abgleich", listOf("vorrat" to "1"))
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(500))
        val nachher = eintraege(env, h)
        val reis = nachher.single { it.payload.ingredientId == "reis" }
        assertTrue(reis.payload.checked, "weniger als gekauft → bleibt abgehakt")
        assertEquals(0, menge(reis).compareTo(BigDecimal(200)))
        assertEquals(0, menge(nachher.single { it.payload.ingredientId == "nudeln" }).compareTo(BigDecimal(500)))
        assertEquals(2, daten.fuer(h).listen.single().let { l -> env.deps.db.tx { c2 -> de.foody.server.sync.RecordStore().get(c2, h, RecordType.SHOPPING_LIST, l.id) } }
            ?.let { de.foody.sync.protocol.Protocol.json.decodeFromJsonElement(ShoppingListPayload.serializer(), it.payload!!).version })
        // Mehr Reis als gekauft → wieder offen; Pasta weg → offener Nudel-Eintrag entfällt
        env.push(h, slot("s1", "2026-10-12", portionen = 6).copy(baseRev = daten.fuer(h).plan.single { it.id == "s1" }.rev, updatedAt = 30),
            SyncRecord(id = "s3", type = RecordType.MEAL_SLOT, deleted = true, updatedAt = 30))
        c.post(cookie, "/einkauf/$listeId/abgleich", listOf("vorrat" to "1"))
        val zuletzt = eintraege(env, h)
        assertFalse(zuletzt.single { it.payload.ingredientId == "reis" }.payload.checked, "mehr gebraucht → wieder offen")
        assertNull(zuletzt.firstOrNull { it.payload.ingredientId == "nudeln" }, "entfällt, war offen")
    }

    /** Review Focus 3. */
    @Test fun abgleichLaesstManuelleInRuhe() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.post(cookie, "/einkauf/neu", anlegenFelder("reis"))
        val listeId = HaushaltsDaten(env.deps.db).fuer(h).listen.single().id
        env.push(h, datensatz("m1", RecordType.SHOPPING_ITEM, ShoppingItemPayload(
            listId = listeId, ingredientId = "reis", name = "Reis", amount = "1000", unit = "GRAM", checked = false, checkedChangedAt = 0,
            manual = true, sortOrder = 9, sources = emptyList(),
        )), SyncRecord(id = "s1", type = RecordType.MEAL_SLOT, deleted = true, updatedAt = 20))
        c.post(cookie, "/einkauf/$listeId/abgleich", listOf("vorrat" to "1"))
        val rest = eintraege(env, h)
        assertEquals(listOf("m1"), rest.map { it.id }, "generierter Reis entfällt, manueller bleibt")
        assertEquals(0, menge(rest.single()).compareTo(BigDecimal(1000)))
    }

    /** Review Focus 1: archiviertes Rezept zählt wie in der App, gelöschtes wird ignoriert. */
    @Test fun archiviertesOderGeloeschtesRezeptImPlan() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry", archiviert = true), rezept("weg", "Weg"), slot("s1", "2026-10-12"), slot("s2", "2026-10-13", rezept = "weg"))
        env.push(h, SyncRecord(id = "weg", type = RecordType.RECIPE, deleted = true, updatedAt = 20))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.OK, c.get("/einkauf/neu?von=2026-10-12&bis=2026-10-18") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.OK, c.get("/plan?ab=2026-10-12") { header(HttpHeaders.Cookie, cookie) }.status)
        c.post(cookie, "/einkauf/neu", anlegenFelder("reis"))
        assertEquals(0, menge(eintraege(env, h).single()).compareTo(BigDecimal(200)), "nur das archivierte Curry zählt")
    }

    /** Final-Review 5: lehnt der Server einen Eintrag ab, heißt das „Das ging nicht“, nicht „Nichts zu kaufen“. */
    @Test fun abgelehnterEintragWirdGemeldet() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val geist = datensatz("geist", RecordType.INGREDIENT, de.foody.sync.protocol.IngredientPayload(name = "Geisterzutat"))
        val r = datensatz("r2", RecordType.RECIPE, de.foody.sync.protocol.RecipePayload(
            name = "Spuk", servings = 2, tags = "", favorite = false,
            lines = listOf(de.foody.sync.protocol.RecipePayload.Line("r2-l1", "geist", "100", "GRAM", 0, null, false)), steps = emptyList(),
        ))
        env.push(h, geist, r, slot("s1", "2026-10-12", rezept = "r2"))
        env.push(h, SyncRecord(id = "geist", type = RecordType.INGREDIENT, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/einkauf/neu", anlegenFelder("geist"))
        assertEquals(HttpStatusCode.Conflict, antwort.status, antwort.bodyAsText().take(400))
        assertTrue("Das ging nicht" in antwort.bodyAsText())
        val rezeptAufListe = c.post(cookie, "/einkauf/rezept", listOf("rezept" to "r2", "portionen" to "2", "liste" to ""))
        assertEquals(HttpStatusCode.Conflict, rezeptAufListe.status)
    }

    @Test fun zeitraumGrenzen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val rueckwaerts = c.get("/einkauf/neu?von=2026-10-18&bis=2026-10-12") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("„Bis“ darf nicht vor „Von“ liegen." in rueckwaerts)
        val zuLang = c.post(cookie, "/einkauf/neu", listOf("name" to "X", "von" to "2026-10-01", "bis" to "2026-11-15", "vorrat" to "1", "mit" to "reis"))
        assertTrue("Höchstens 31 Tage." in zuLang.bodyAsText())
        val ohneName = c.post(cookie, "/einkauf/neu", anlegenFelder("reis", name = "  "))
        assertTrue("Bitte einen Namen eingeben." in ohneName.bodyAsText())
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).listen.isEmpty())
    }

    @Test fun fremdeListeIst404UndCsrf403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, datensatz("fl", RecordType.SHOPPING_LIST, ShoppingListPayload("Geheim", "2026-10-12", "2026-10-18", 1)))
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.NotFound, c.get("/einkauf/fl/abgleich") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/einkauf/fl/abgleich", listOf("vorrat" to "1")).status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/einkauf/neu", anlegenFelder("reis"), csrf = false).status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/einkauf/fl/abgleich", listOf("vorrat" to "1"), csrf = false).status)
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).listen.isEmpty())
    }

    @Test fun einkaufSeiteVerlinkt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("/einkauf/neu" in c.get("/einkauf") { header(HttpHeaders.Cookie, cookie) }.bodyAsText(), "auch ohne Liste")
        c.post(cookie, "/einkauf/neu", anlegenFelder("reis"))
        val listeId = HaushaltsDaten(env.deps.db).fuer(h).listen.single().id
        assertTrue("/einkauf/$listeId/abgleich" in c.get("/einkauf") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }
}
