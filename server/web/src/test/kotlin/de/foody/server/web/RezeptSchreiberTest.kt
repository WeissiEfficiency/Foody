package de.foody.server.web

import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class RezeptSchreiberTest {
    private fun formular(
        name: String = "Curry", id: String? = null, rev: Long? = null, erzwingen: Boolean = false,
        zeilen: List<FormZeile> = listOf(FormZeile("200", "GRAM", "Reis", "", false)),
        fest: Boolean = false, mahlzeiten: Set<Mahlzeit> = emptySet(),
    ) = RezeptFormular(id, rev, erzwingen, name, "2", "", "30", "Abend", "", "", "Kochen\nServieren", fest, mahlzeiten, emptySet<Gang>(), zeilen)

    private fun roh(env: WebUmgebung, h: String, id: String, typ: RecordType = RecordType.RECIPE): SyncRecord? =
        env.deps.db.tx { c -> RecordStore().get(c, h, typ, id) }

    private fun rezeptPayload(env: WebUmgebung, h: String, id: String) =
        Protocol.json.decodeFromJsonElement(RecipePayload.serializer(), roh(env, h, id)!!.payload!!)

    private fun zutatenNamen(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h).zutaten.values.map { it.name }.sorted()

    private fun schreiber(env: WebUmgebung) = RezeptSchreiber(env.deps, HaushaltsDaten(env.deps.db))

    @Test fun neuesRezeptMitNeuerUndVorhandenerZutat() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis())
        val ok = assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(zeilen = listOf(
            FormZeile("200", "GRAM", "Reis", "", false), FormZeile("1", "PIECE", "Zwiebel", "gewürfelt", false),
        ))))
        val p = rezeptPayload(env, h, ok.id)
        assertEquals("Curry", p.name)
        assertEquals(30, p.cook)
        assertEquals(listOf("Kochen", "Servieren"), p.steps.sortedBy { it.position }.map { it.text })
        assertEquals("reis", p.lines[0].ingredientId)
        assertEquals(listOf("Reis", "Zwiebel"), zutatenNamen(env, h))
        assertEquals("gewürfelt", p.lines[1].note)
    }

    @Test fun grossKleinschreibungNutztVorhandeneZutat() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis())
        schreiber(env).speichern(h, formular(zeilen = listOf(FormZeile("1", "GRAM", "reis", "", false))))
        assertEquals(listOf("Reis"), zutatenNamen(env, h))
    }

    @Test fun mehlWirdWeizenmehl() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, datensatz("wm", RecordType.INGREDIENT, IngredientPayload(name = "Weizenmehl")))
        val ok = assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(zeilen = listOf(FormZeile("250", "GRAM", "Mehl", "", false)))))
        assertEquals("wm", rezeptPayload(env, h, ok.id).lines.single().ingredientId)
        assertEquals(listOf("Weizenmehl"), zutatenNamen(env, h))
    }

    @Test fun gleicheNeueZutatNurEinmal() = webServer { env ->
        val (_, _, h) = env.haushalt()
        val ok = assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(zeilen = listOf(
            FormZeile("100", "GRAM", "Tofu", "", false), FormZeile("50", "GRAM", "Tofu", "geräuchert", false),
        ))))
        assertEquals(listOf("Tofu"), zutatenNamen(env, h))
        val ids = rezeptPayload(env, h, ok.id).lines.map { it.ingredientId }.toSet()
        assertEquals(1, ids.size)
    }

    @Test fun ohneZutatenErlaubt() = webServer { env ->
        val (_, _, h) = env.haushalt()
        val ok = assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(zeilen = emptyList())))
        assertTrue(rezeptPayload(env, h, ok.id).lines.isEmpty())
    }

    @Test fun bearbeitenBehaeltFotoFavoritUndUnbekanntesFeld() = webServer { env ->
        val (_, _, h) = env.haushalt()
        val alt = rezept("r1", "Curry").let { r ->
            r.copy(payload = JsonObject(r.payload!! + mapOf("photo" to JsonPrimitive("a".repeat(64)), "favorite" to JsonPrimitive(true), "zukunft" to JsonPrimitive(1))))
        }
        env.push(h, reis(), alt)
        val rev = roh(env, h, "r1")!!.rev
        assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(name = "Curry scharf", id = "r1", rev = rev)))
        val neu = roh(env, h, "r1")!!.payload!!
        assertEquals("\"Curry scharf\"", neu["name"].toString())
        assertEquals("\"" + "a".repeat(64) + "\"", neu["photo"].toString())
        assertEquals("true", neu["favorite"].toString())
        assertEquals("1", neu["zukunft"].toString())
    }

    @Test fun einordnungVermutenSetztNull() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis())
        val ok = assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(fest = false)))
        assertNull(rezeptPayload(env, h, ok.id).mahlzeiten)
    }

    @Test fun unbekannteEinordnungBleibt() = webServer { env ->
        val (_, _, h) = env.haushalt()
        val alt = rezept("r1", "Curry").let { r -> r.copy(payload = JsonObject(r.payload!! + ("mahlzeiten" to JsonPrimitive("ABENDESSEN,BRUNCH")))) }
        env.push(h, reis(), alt)
        val rev = roh(env, h, "r1")!!.rev
        schreiber(env).speichern(h, formular(id = "r1", rev = rev, fest = true, mahlzeiten = setOf(Mahlzeit.ABENDESSEN)))
        assertEquals("ABENDESSEN,BRUNCH", rezeptPayload(env, h, "r1").mahlzeiten)
    }

    @Test fun konfliktSpeichertNichts() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val gesehen = roh(env, h, "r1")!!.rev
        env.push(h, rezept("r1", "Curry vom Handy").copy(baseRev = gesehen)) // Handy ändert zwischendurch
        val ergebnis = schreiber(env).speichern(h, formular(name = "Curry aus dem Browser", id = "r1", rev = gesehen))
        assertIs<Speichern.Konflikt>(ergebnis)
        assertEquals("Curry vom Handy", rezeptPayload(env, h, "r1").name)
    }

    @Test fun erzwingenSpeichert() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val gesehen = roh(env, h, "r1")!!.rev
        env.push(h, rezept("r1", "Curry vom Handy").copy(baseRev = gesehen))
        val aktuell = roh(env, h, "r1")!!.rev
        assertIs<Speichern.Ok>(schreiber(env).speichern(h, formular(name = "Curry aus dem Browser", id = "r1", rev = aktuell, erzwingen = true)))
        assertEquals("Curry aus dem Browser", rezeptPayload(env, h, "r1").name)
    }

    @Test fun geloeschtWaehrendBearbeitungIstKonflikt() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val gesehen = roh(env, h, "r1")!!.rev
        env.push(h, SyncRecord(id = "r1", type = RecordType.RECIPE, deleted = true, updatedAt = 2, baseRev = gesehen))
        assertIs<Speichern.Konflikt>(schreiber(env).speichern(h, formular(id = "r1", rev = gesehen)))
        assertTrue(roh(env, h, "r1")!!.deleted, "Löschung bleibt")
    }

    @Test fun fremdeIdIstNichtGefunden() = webServer { env ->
        val (_, _, meins) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("geheim", "Geheim"))
        assertIs<Speichern.NichtGefunden>(schreiber(env).speichern(meins, formular(id = "geheim", rev = 1)))
        assertEquals("Geheim", rezeptPayload(env, fremd, "geheim").name)
    }

    @Test fun archivierenUndWiederherstellen() = webServer { env ->
        val (_, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val s = schreiber(env)
        assertTrue(s.archivieren(h, "r1", true))
        assertTrue(rezeptPayload(env, h, "r1").archivedAt != null)
        assertTrue(s.archivieren(h, "r1", false))
        assertNull(rezeptPayload(env, h, "r1").archivedAt)
        assertEquals(false, s.archivieren(h, "gibtsnicht", true))
    }

    @Test fun fehlerhaftesFormularSchreibtNichts() = webServer { env ->
        val (_, _, h) = env.haushalt()
        val f = assertIs<Speichern.Fehler>(schreiber(env).speichern(h, formular(name = "")))
        assertTrue("name" in f.meldungen)
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).rezepte.isEmpty())
    }
}
