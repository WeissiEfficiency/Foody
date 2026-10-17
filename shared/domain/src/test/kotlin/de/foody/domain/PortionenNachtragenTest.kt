package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vor Regel 27 importierte Rezepte: Portionen aus der Datei nachtragen, ohne Nutzer-Korrekturen anzufassen. */
class PortionenNachtragenTest {
    private val quelle = "https://www.chefkoch.de/rezepte/1/spitzel.html"
    private fun datei(name: String = "Spitzel", portionen: Int?) = ImportedRecipe(name, quelle, emptyList(), emptyList(), servings = portionen)
    private fun alteNotiz(n: Int) = RezeptImport.hinweis(quelle, n, geschaetzt = true)

    @Test fun geschaetztWirdDurchDateiErsetzt() {
        assertEquals(
            RezeptImport.Nachtrag(5, "Quelle: $quelle"),
            RezeptImport.portionenNachtragen(alteNotiz(1), aktuellePortionen = 1, datei = datei(portionen = 5)),
        )
    }

    @Test fun gleicheZahlEntferntNurDenHinweis() {
        assertEquals(
            RezeptImport.Nachtrag(4, "Quelle: $quelle"),
            RezeptImport.portionenNachtragen(alteNotiz(4), aktuellePortionen = 4, datei = datei(portionen = 4)),
        )
    }

    @Test fun eigeneNotizenBleiben() {
        val notiz = alteNotiz(2) + "\nSchmeckt mit Thymian besser."
        assertEquals(
            RezeptImport.Nachtrag(8, "Quelle: $quelle\nSchmeckt mit Thymian besser."),
            RezeptImport.portionenNachtragen(notiz, aktuellePortionen = 2, datei = datei(portionen = 8)),
        )
    }

    @Test fun ohneQuelleBleibtKeineNotiz() {
        val notiz = RezeptImport.hinweis(null, 2, geschaetzt = true)
        assertEquals(RezeptImport.Nachtrag(6, null), RezeptImport.portionenNachtragen(notiz, 2, datei(portionen = 6)))
    }

    @Test fun vomNutzerGeaendertePortionenBleiben() {
        // Notiz sagt 2, das Rezept hat inzwischen 3 – der Nutzer hat korrigiert
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(2), aktuellePortionen = 3, datei = datei(portionen = 6)))
    }

    @Test fun ohneSchaetzhinweisNichts() {
        // Hinweis gelöscht (Nutzer hat geprüft), Datei-Portionen schon übernommen oder Vorgabe statt Schätzung
        assertNull(RezeptImport.portionenNachtragen("Quelle: $quelle", 2, datei(portionen = 6)))
        assertNull(RezeptImport.portionenNachtragen(null, 2, datei(portionen = 6)))
        assertNull(RezeptImport.portionenNachtragen(RezeptImport.hinweis(quelle, 4, geschaetzt = false), 4, datei(portionen = 6)))
    }

    @Test fun dateiOhnePortionenNichts() {
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(2), 2, datei(portionen = null)))
    }

    @Test fun ganzeBackwareBleibtGeschaetzt() {
        // „Portionen: 1“ bei einem Kuchen schätzt auch der heutige Import – also nichts ändern
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(12), 12, datei("Apfelkuchen", portionen = 1)))
        assertEquals(RezeptImport.Nachtrag(16, "Quelle: $quelle"), RezeptImport.portionenNachtragen(alteNotiz(12), 12, datei("Apfelkuchen", portionen = 16)))
    }

    @Test fun zahlMussGenauPassen() {
        // „auf 1 geschätzt“ darf nicht auf ein Rezept mit 12 Portionen passen und umgekehrt
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(12), 1, datei(portionen = 5)))
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(1), 12, datei(portionen = 5)))
    }
}
