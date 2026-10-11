package de.foody.server.web

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VorgaengeTest {
    /** Final-Review: das doppelte Absenden wartet, bis das erste fertig geschrieben hat (sonst zeigt die Seite noch nichts). */
    @Test fun doppeltWartetAufDasErste() {
        val h = "haushalt-w"
        val erstesFertig = AtomicLong()
        val zweitesZurueck = AtomicLong()
        val gestartet = CountDownLatch(1)
        val erstes = thread {
            Vorgaenge.einmal(h, "weg", "n1", 0, { true }) { gestartet.countDown(); Thread.sleep(300); erstesFertig.set(System.nanoTime()); "ok" }
        }
        gestartet.await()
        val zweites = Vorgaenge.einmal(h, "weg", "n1", 0, { true }) { "doppelt geschrieben" }
        zweitesZurueck.set(System.nanoTime())
        erstes.join()
        assertNull(zweites, "zweites Absenden schreibt nicht")
        assertTrue(zweitesZurueck.get() >= erstesFertig.get(), "zweites kam erst nach dem ersten zurück")
    }

    /** Final-Review: Ausnahme oder Misserfolg geben die Nummer frei – erneutes Absenden schreibt dann wirklich. */
    @Test fun fehlerGibtNummerFrei() {
        val h = "haushalt-f"
        assertFailsWith<IllegalStateException> { Vorgaenge.einmal<String>(h, "weg", "n2", 0, { true }) { error("DB weg") } }
        assertEquals("zweiter Versuch", Vorgaenge.einmal(h, "weg", "n2", 0, { true }) { "zweiter Versuch" })
        assertEquals("abgelehnt", Vorgaenge.einmal(h, "weg", "n3", 0, { it == "ok" }) { "abgelehnt" })
        assertEquals("ok", Vorgaenge.einmal(h, "weg", "n3", 0, { it == "ok" }) { "ok" })
        assertNull(Vorgaenge.einmal(h, "weg", "n3", 0, { it == "ok" }) { "ok" })
    }
}
