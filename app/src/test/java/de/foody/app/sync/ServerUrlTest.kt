package de.foody.app.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlTest {
    @Test
    fun httpsIsAccepted() {
        assertEquals("https://sync.example.org", ServerUrl.normalize("https://sync.example.org", false))
        assertEquals("https://sync.example.org:8443", ServerUrl.normalize("  https://sync.example.org:8443  ", false))
    }

    @Test
    fun trailingSlashIsRemovedAndPathKept() {
        assertEquals("https://x", ServerUrl.normalize("https://x/", false))
        assertEquals("https://x/foody", ServerUrl.normalize("https://x/foody//", false))
    }

    @Test
    fun httpOnlyForLocalHostsInDebug() {
        assertNull(ServerUrl.normalize("http://10.0.2.2:18080", false))
        assertNull(ServerUrl.normalize("http://localhost", false))
        assertEquals("http://10.0.2.2:18080", ServerUrl.normalize("http://10.0.2.2:18080", true))
        assertEquals("http://localhost:8080", ServerUrl.normalize("http://localhost:8080/", true))
        assertNull(ServerUrl.normalize("http://example.org", true))
        assertNull(ServerUrl.normalize("http://10.0.2.20", true))
        assertNull(ServerUrl.normalize("http://localhost.evil.org", true))
    }

    @Test
    fun garbageIsRejected() {
        val bad = listOf(
            "", "   ", "sync.example.org", "ftp://x", "https://", "https:// x", "https://user:pw@x",
            "https://x?a=1", "https://x#f", "not a url", "javascript:alert(1)",
        )
        for (input in bad) {
            assertNull(ServerUrl.normalize(input, true), "sollte abgelehnt werden: '$input'")
        }
    }
}
