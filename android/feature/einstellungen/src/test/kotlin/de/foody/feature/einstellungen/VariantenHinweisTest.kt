package de.foody.feature.einstellungen

import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import org.junit.Test
import kotlin.test.assertEquals

/** Der Satz unter der Variantenwahl beschreibt beim Grauen beide Gesichter (ADR 0022); das Necronomicon ist fertig. */
class VariantenHinweisTest {
    @Test
    fun necronomiconOhneImAufbau() {
        assertEquals("Die App als verwittertes Buch: Pergament, Tinte und Blut.", variantenHinweis(FoodyStil.GRAUEN, StilVariante.IMMERSIV))
    }

    @Test
    fun ominoesUnveraendert() {
        assertEquals("Das grüne Grauen: Sternenhimmel, Glut und Mondbogen.", variantenHinweis(FoodyStil.GRAUEN, StilVariante.RUHIG))
    }
}
