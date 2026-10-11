package de.foody.server.web

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitTest {
    @Test fun zeitzoneAusUmgebung() {
        assertEquals(ZoneId.of("Europe/Berlin"), standardZone(null))
        assertEquals(ZoneId.of("Europe/Vienna"), standardZone(" Europe/Vienna "))
        assertEquals(ZoneId.of("Europe/Berlin"), standardZone("Mars/Nirgendwo"), "ungültig → Standard statt Absturz")
    }
}
