package de.foody.domain

import de.foody.domain.Abwechslung.Merkmal
import kotlin.test.Test
import kotlin.test.assertEquals

class AbwechslungTest {
    private fun merkmale(name: String, vararg zutaten: String) = Abwechslung.merkmale(name, zutaten.toList())

    @Test fun grundlageAusZutaten() {
        assertEquals(setOf(Merkmal.NUDELN), merkmale("Spaghetti Carbonara", "Spaghetti", "Speck", "Eigelb", "Parmesan"))
        assertEquals(setOf(Merkmal.REIS, Merkmal.GEFLUEGEL), merkmale("Hähnchen-Curry", "Basmatireis", "Hähnchenbrustfilet", "Kokosmilch"))
        assertEquals(setOf(Merkmal.KARTOFFELN, Merkmal.RIND_SCHWEIN), merkmale("Bauernfrühstück", "Kartoffeln", "Schweinebauch", "Eier"))
        assertEquals(setOf(Merkmal.HUELSENFRUECHTE), merkmale("Linsensuppe", "Rote Linsen", "Möhre"))
        assertEquals(setOf(Merkmal.FISCH), merkmale("Ofenlachs", "Lachsfilet", "Zitrone"))
    }

    @Test fun nameZaehltMit() {
        assertEquals(setOf(Merkmal.NUDELN), merkmale("Lasagne", "Tomaten", "Mozzarella"))
        assertEquals(setOf(Merkmal.TEIG), merkmale("Flammkuchen", "Schmand", "Zwiebeln"))
    }

    @Test fun wurstSchnitzelUndHackbaellchenSindFleisch() {
        assertEquals(setOf(Merkmal.RIND_SCHWEIN), merkmale("Salsiccia-Meatballs in Tomatensauce", "Salsiccia", "Passierte Tomaten"))
        assertEquals(setOf(Merkmal.RIND_SCHWEIN), merkmale("Altbayerisches Schnitzel", "Semmelbrösel", "Eier"))
        assertEquals(setOf(Merkmal.RIND_SCHWEIN, Merkmal.KARTOFFELN), merkmale("Currywurst mit Pommes", "Bratwürste", "Kartoffeln"))
    }

    @Test fun wuerzzutatenZaehlenNicht() {
        assertEquals(setOf(Merkmal.GEFLUEGEL), merkmale("Scharfes Hühnerfleisch mit Thai-Basilikum", "Hühnerfleisch", "Fischsauce", "Austernsauce"))
        assertEquals(emptySet(), merkmale("Gemüsesuppe", "Hühnerbrühe", "Rinderfond", "Kartoffelstärke", "Reismehl"))
    }

    @Test fun keineFalschenTreffer() {
        assertEquals(emptySet(), merkmale("Wildbraten mit Preiselbeeren", "Preiselbeeren", "Reisessig", "Speckwürfel"))
        assertEquals(emptySet(), merkmale("Gemüsepfanne", "Zucchini", "Paprika", "Brühe"))
    }
}
