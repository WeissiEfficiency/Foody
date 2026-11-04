package de.foody.server

import kotlin.test.Test
import kotlin.test.assertFalse

class ServerConfigTest {
    @Test
    fun passwortNichtImToString() {
        val text = ServerConfig.fromEnv(mapOf("FOODY_ADMIN_PASSWORD" to "pw-geheim")).toString()
        assertFalse("pw-geheim" in text, text)
    }
}
