package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RezeptEinordnungTest {
    private fun e(name: String, tags: List<String> = emptyList(), m: Set<Mahlzeit>? = null, g: Set<Gang>? = null) =
        RezeptEinordnung.einordnen(name, tags, m, g)

    @Test fun curryIstHauptspeiseMittagAbend() {
        val r = e("Gemüsecurry mit Reis")
        assertEquals(setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN), r.mahlzeiten)
        assertEquals(setOf(Gang.HAUPTSPEISE), r.gaenge)
        assertTrue(r.mahlzeitenVermutet && r.gaengeVermutet)
    }

    @Test fun nachspeiseIstAuchSnack() {
        val r = e("Tiramisu")
        assertEquals(setOf(Mahlzeit.SNACK), r.mahlzeiten)
        assertEquals(setOf(Gang.NACHSPEISE), r.gaenge)
    }

    @Test fun tagZaehltUnabhaengigVonGrossschreibung() =
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK), e("Omas Liebling", listOf("FRÜHSTÜCK")).mahlzeiten)

    @Test fun salatVereinigtTreffer() = assertEquals(setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE), e("Kartoffelsalat").gaenge)

    @Test fun kurzeStichwoerterNurAlsGanzesWort() {
        assertFalse(Gang.NACHSPEISE in e("Gebratener Reis").gaenge)
        assertFalse(Gang.NACHSPEISE in e("Fleischpflanzerl").gaenge)
        assertTrue(Gang.NACHSPEISE in e("Eis mit Beeren").gaenge)
    }

    @Test fun laengeresStichwortVerdraengtEnthaltenes() {
        val r = e("Pfannkuchen")
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK), r.mahlzeiten)
        assertTrue(r.gaenge.isEmpty())
        assertEquals(setOf(Gang.NACHSPEISE), e("Käsekuchen").gaenge)
    }

    @Test fun herzhafteKuchenUndTortelliniSindHauptspeisen() {
        listOf("Flammkuchen Elsässer Art", "Zwiebelkuchen", "Tortellini alla panna", "Tortelloni mit Spinat").forEach { name ->
            val r = e(name)
            assertEquals(setOf(Gang.HAUPTSPEISE), r.gaenge, name)
            assertTrue(r.passtZu(Mahlzeit.ABENDESSEN), name)
        }
    }

    /** Titel aus der Chefkoch-Testsammlung, die die Start-Wortliste nicht erkannte. */
    @Test fun titelAusDerSammlung() {
        val mittagAbend = setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN)
        listOf(
            "Hähnchenbrustfilet mit Country-Kartoffeln", "Lachs aus dem Backofen", "Butter Chicken Masala",
            "Rinderrouladen klassisch", "Gnocchi aus dem Ofen in Paprika-Tomaten-Sauce", "Pulled Pork - aus dem Ofen ohne Grill",
        ).forEach { name ->
            val r = e(name)
            assertEquals(mittagAbend, r.mahlzeiten, name)
            assertEquals(setOf(Gang.HAUPTSPEISE), r.gaenge, name)
        }
        val knoedel = e("Semmelknödel mit Pfifferling - Rahmsauce")
        assertEquals(setOf(Gang.HAUPTSPEISE), knoedel.gaenge, "Semmelknödel sind keine Brotzeit")
        val semmeln = e("Kaisersemmeln / Kaiserbrötchen")
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.ABENDESSEN), semmeln.mahlzeiten)
        assertEquals(setOf(Gang.BROTZEIT), semmeln.gaenge)
        assertEquals(setOf(Gang.BROTZEIT), e("Michis superknuspriges Bauernbrot").gaenge)
        assertEquals(setOf(Mahlzeit.SNACK), e("Vanillekipferl").mahlzeiten)
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.SNACK), e("Uromas Hefezopf").mahlzeiten)
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN), e("Shakshuka").mahlzeiten)
    }

    @Test fun ohneTrefferPasstUeberall() {
        val r = e("Käsespätzle")
        assertTrue(r.mahlzeiten.isEmpty() && !r.eingeordnet && Mahlzeit.entries.all(r::passtZu))
    }

    @Test fun festgelegtSchlaegtVermutungJeDimension() {
        val r = e("Tiramisu", m = setOf(Mahlzeit.ABENDESSEN))
        assertEquals(setOf(Mahlzeit.ABENDESSEN), r.mahlzeiten)
        assertFalse(r.mahlzeitenVermutet)
        assertEquals(setOf(Gang.NACHSPEISE), r.gaenge)
        assertTrue(r.gaengeVermutet)
    }

    @Test fun bewusstLeerVermutetNicht() {
        val r = e("Pfannkuchen", g = emptySet())
        assertTrue(r.gaenge.isEmpty())
        assertFalse(r.gaengeVermutet)
    }

    @Test fun gangFilter() {
        val r = e("Gemüsecurry")
        assertTrue(r.passtZu(Mahlzeit.ABENDESSEN, emptySet()))
        assertTrue(r.passtZu(Mahlzeit.ABENDESSEN, setOf(Gang.HAUPTSPEISE, Gang.NACHSPEISE)))
        assertFalse(r.passtZu(Mahlzeit.ABENDESSEN, setOf(Gang.NACHSPEISE)))
        assertFalse(r.passtZu(Mahlzeit.FRUEHSTUECK, emptySet()))
    }

    /** Stichwörter aus mehreren Wörtern treffen auch im Namen, nicht nur als Tag. */
    @Test fun mehrteiligesStichwortImNamen() =
        assertEquals(setOf(Mahlzeit.FRUEHSTUECK), e("Overnight Oats mit Beeren").mahlzeiten)

    /** Beim Speichern bleiben Werte erhalten, die diese App-Version nicht kennt (z. B. per Sync von einer neueren). */
    @Test fun unbekannteWerteBleibenBeimSpeichern() {
        assertEquals("MITTAGESSEN,BRUNCH", alsText(setOf(Mahlzeit.MITTAGESSEN), "FRUEHSTUECK,BRUNCH", Mahlzeit.entries))
        assertEquals("ABENDESSEN", alsText(setOf(Mahlzeit.ABENDESSEN), null, Mahlzeit.entries))
        assertEquals(null, alsText(null, "BRUNCH", Mahlzeit.entries), "„vermuten“ setzt bewusst zurück")
    }

    /** Aus der Messung an der Chefkoch-Sammlung (2026-10-11): herzhafte Kuchen, gefüllte Pfannkuchen, Rösti. */
    @Test fun herzhafterKuchenMitKaeseIstHauptspeise() {
        val r = e("Gruyere - Rosmarin - Kuchen")
        assertEquals(setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN), r.mahlzeiten)
        assertEquals(setOf(Gang.HAUPTSPEISE), r.gaenge)
        // Süße Kuchen bleiben Nachspeise – auch „Käsekuchen“ (Käse ist kein herzhaftes Merkmal)
        assertEquals(setOf(Gang.NACHSPEISE), e("Der beste Käsekuchen der Welt").gaenge)
        assertEquals(setOf(Gang.NACHSPEISE), e("Saftiger Zitronenkuchen").gaenge)
    }

    @Test fun gefuellterPfannkuchenUndRoestiSindAuchHauptspeise() {
        val p = e("Pfannkuchenbeutel mit Pilzfüllung")
        assertTrue(Mahlzeit.FRUEHSTUECK in p.mahlzeiten && Mahlzeit.MITTAGESSEN in p.mahlzeiten)
        assertEquals(setOf(Gang.HAUPTSPEISE), p.gaenge)
        assertTrue(Gang.HAUPTSPEISE in e("Frühlings-Rösti mit Bärlauch-Dip").gaenge)
    }
}
