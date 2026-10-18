package de.foody.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Das Modul ist eingerichtet und bringt die v1-Schemata 1–9 für die Migration nach 10 mit. */
class GeruestTest {
    @Test
    fun schemaNeunIstDa() {
        // Unit-Tests laufen im Modulordner (android/core/data)
        val schema = File("schemas/de.foody.core.data.db.FoodyDatenbank/9.json")
        assertTrue(schema.isFile, "Schema 9 fehlt: ${schema.absolutePath}")
        assertTrue(schema.readText().contains("\"version\": 9"), "Schema 9 hat nicht die Version 9")
    }
}
