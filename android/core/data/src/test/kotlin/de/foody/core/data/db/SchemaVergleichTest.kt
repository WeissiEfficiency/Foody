package de.foody.core.data.db

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Schema 10 muss aus der echten v1-Datenbank (Schema 9) per Migration entstehen können. Deshalb darf es sich nur in
 * den zwei gewollten Punkten unterscheiden: `pantry_item` entfällt, `sync_state.householdId` heißt `kontoId`.
 * Verglichen wird strukturiert (Spalten, Primärschlüssel, Indizes, Fremdschlüssel), nicht die `createSql`-Texte.
 */
class SchemaVergleichTest {
    private data class Spalte(val name: String, val affinitaet: String, val notNull: Boolean, val standardwert: String?)

    private data class Index(val name: String, val spalten: List<String>, val eindeutig: Boolean, val reihenfolge: List<String>)

    private data class Fremdschluessel(
        val tabelle: String,
        val spalten: List<String>,
        val zielSpalten: List<String>,
        val beimLoeschen: String,
        val beimAendern: String,
    )

    private data class Tabelle(
        val spalten: Set<Spalte>,
        val primaerschluessel: List<String>,
        val autoGenerate: Boolean,
        val indizes: Set<Index>,
        val fremdschluessel: Set<Fremdschluessel>,
    ) {
        fun spalteUmbenannt(alt: String, neu: String): Tabelle {
            require(spalten.any { it.name == alt }) { "Spalte $alt fehlt" }
            return copy(spalten = spalten.map { if (it.name == alt) it.copy(name = neu) else it }.toSet())
        }
    }

    private val ordner = File("schemas/de.foody.core.data.db.FoodyDatenbank")

    private fun datenbank(datei: String): JsonObject {
        val f = File(ordner, datei)
        assertTrue(f.isFile, "Schema fehlt: ${f.absolutePath}")
        return Json.parseToJsonElement(f.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun JsonObject.text(schluessel: String): String = getValue(schluessel).jsonPrimitive.content

    private fun JsonObject.textListe(schluessel: String): List<String> =
        (get(schluessel) as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

    private fun tabellen(datei: String): Map<String, Tabelle> =
        datenbank(datei).getValue("entities").jsonArray.associate { e ->
            val t = e.jsonObject
            val pk = t.getValue("primaryKey").jsonObject
            t.text("tableName") to Tabelle(
                spalten = t.getValue("fields").jsonArray.map { f ->
                    val o = f.jsonObject
                    Spalte(
                        name = o.text("columnName"),
                        affinitaet = o.text("affinity"),
                        notNull = o["notNull"]?.jsonPrimitive?.booleanOrNull ?: false,
                        standardwert = o["defaultValue"]?.jsonPrimitive?.content,
                    )
                }.toSet(),
                primaerschluessel = pk.textListe("columnNames"),
                autoGenerate = pk["autoGenerate"]?.jsonPrimitive?.booleanOrNull ?: false,
                indizes = (t["indices"] as? JsonArray).orEmpty().map { i ->
                    val o = i.jsonObject
                    Index(
                        name = o.text("name"),
                        spalten = o.textListe("columnNames"),
                        eindeutig = o["unique"]?.jsonPrimitive?.booleanOrNull ?: false,
                        reihenfolge = o.textListe("orders"),
                    )
                }.toSet(),
                fremdschluessel = (t["foreignKeys"] as? JsonArray).orEmpty().map { k ->
                    val o = k.jsonObject
                    Fremdschluessel(
                        tabelle = o.text("table"),
                        spalten = o.textListe("columns"),
                        zielSpalten = o.textListe("referencedColumns"),
                        beimLoeschen = o.text("onDelete"),
                        beimAendern = o.text("onUpdate"),
                    )
                }.toSet(),
            )
        }

    @Test
    fun schemaZehnIstSchemaNeunOhneVorrat() {
        val neun = tabellen("9.json")
        val zehn = tabellen("10.json")
        val erwartet = neun - "pantry_item" +
            ("sync_state" to neun.getValue("sync_state").spalteUmbenannt("householdId", "kontoId"))
        assertEquals(erwartet.keys, zehn.keys, "Tabellen")
        for (name in erwartet.keys) assertEquals(erwartet.getValue(name), zehn.getValue(name), "Tabelle $name")
    }

    @Test
    fun schemaZehnHatVersionZehnUndKeineViews() {
        val zehn = datenbank("10.json")
        assertEquals(10, zehn.getValue("version").jsonPrimitive.int)
        assertTrue((zehn["views"] as? JsonArray).isNullOrEmpty(), "Schema 10 hat Views")
    }
}
