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
}
