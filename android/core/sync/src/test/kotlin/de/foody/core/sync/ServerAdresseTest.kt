package de.foody.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerAdresseTest {
    @Test
    fun adresseNurHttpsAusserDebugLokal() {
        assertEquals("https://sync.example.org", ServerAdresse.pruefen("https://sync.example.org", debug = false))
        assertEquals("https://sync.example.org:8443", ServerAdresse.pruefen("  https://sync.example.org:8443  ", debug = false))

        // http nur im Debug-Build und nur für den Emulator-Host bzw. localhost
        assertNull(ServerAdresse.pruefen("http://10.0.2.2:8081", debug = false))
        assertEquals("http://10.0.2.2:8081", ServerAdresse.pruefen("http://10.0.2.2:8081", debug = true))
        assertNull(ServerAdresse.pruefen("http://localhost:8081", debug = false))
        assertEquals("http://localhost:8081", ServerAdresse.pruefen("http://LOCALHOST:8081/", debug = true))
        assertNull(ServerAdresse.pruefen("http://example.org", debug = true))
        assertNull(ServerAdresse.pruefen("http://10.0.2.20", debug = true))
        assertNull(ServerAdresse.pruefen("http://localhost.evil.org", debug = true))
    }

    @Test
    fun abschliessenderSchraegstrichEntfaelltPfadBleibt() {
        assertEquals("https://x", ServerAdresse.pruefen("https://x/", debug = false))
        assertEquals("https://x/foody", ServerAdresse.pruefen("https://x/foody//", debug = false))
    }

    @Test
    fun unsinnWirdAbgelehnt() {
        val falsch = listOf(
            "", "   ", "sync.example.org", "ftp://x", "https://", "https:// x", "https://user:pw@x",
            "https://x?a=1", "https://x#f", "kein url", "javascript:alert(1)",
        )
        for (eingabe in falsch) {
            assertNull(ServerAdresse.pruefen(eingabe, debug = true), "sollte abgelehnt werden: '$eingabe'")
        }
    }
}
