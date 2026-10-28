package de.foody.core.data.sync

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import de.foody.api.protocol.IngredientPayload
import de.foody.api.protocol.MealSlotPayload
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.api.protocol.decode
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncFotoGewuenschtEntity
import de.foody.core.data.db.SyncProblemEntity
import de.foody.core.data.db.SyncRevisionEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.startdaten.Startdaten
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sync-Speicher: Outbox in Pakete, Push-Ergebnisse, Server-Daten anwenden (Fotoregel, Trigger-Ruhe), Voll-Abgleich und
 * Trennen. Robolectric mit echter Grafik, damit der Verkleinerungsversuch unlesbare Bilder wirklich ablehnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncSpeicherTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatenbank
    private lateinit var ablage: FotoAblage
    private lateinit var index: FotoIndex
    private lateinit var speicher: SyncSpeicher
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)
    private val dao get() = db.syncDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries()
            .build()
        ablage = FotoAblage(context)
        index = FotoIndex(dao, ablage)
        speicher = SyncSpeicher(db, ablage, index)
        ordner.deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        ordner.deleteRecursively()
    }

    // --- Hilfen ---

    private fun zutat(id: String, name: String = "Zutat $id") = ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)

    private fun rezept(id: String, bild: String? = null) = RezeptEntity(
        id = id, name = "Rezept $id", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = bild,
        notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
    )

    private fun zeile(id: String, rezeptId: String, zutatId: String) = RezeptZutatEntity(
        id = id, recipeId = rezeptId, ingredientId = zutatId, amount = BigDecimal.ONE, unit = MeasureUnit.GRAM,
        sortOrder = 0, preparationNote = null, optional = false,
    )

    private fun zutatRecord(id: String, name: String, rev: Long = 1) = SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 100, rev = rev,
        payload = json(IngredientPayload.serializer(), IngredientPayload(name = name)),
    )

    private fun rezeptRecord(id: String, foto: String?, zutaten: List<String> = emptyList(), rev: Long = 1) = SyncRecord(
        id = id, type = RecordType.RECIPE, updatedAt = 100, rev = rev,
        payload = json(
            RecipePayload.serializer(),
            RecipePayload(
                name = "Server $id", servings = 2, photo = foto, tags = "", favorite = false,
                lines = zutaten.mapIndexed { i, z ->
                    RecipePayload.Line(id = "$id-l$i", ingredientId = z, amount = "1", unit = "GRAM", sortOrder = i, optional = false)
                },
                steps = emptyList(),
            ),
        ),
    )

    private fun planRecord(id: String, rezeptId: String) = SyncRecord(
        id = id, type = RecordType.MEAL_SLOT, updatedAt = 100, rev = 1,
        payload = json(MealSlotPayload.serializer(), MealSlotPayload(date = "2026-10-18", slotType = "DINNER", recipeId = rezeptId, servings = 2)),
    )

    private fun <T> json(s: kotlinx.serialization.KSerializer<T>, p: T) = Protocol.json.encodeToJsonElement(s, p) as JsonObject

    private fun fotoVon(record: SyncRecord): String? = (RecordType.RECIPE.decode(record.payload!!) as RecipePayload).photo

    /** Eigene Fotodatei im Fotoordner mit [bytes]; liefert den Link wie die Ablage ihn speichert. */
    private fun eigeneDatei(name: String, bytes: ByteArray): String {
        ordner.mkdirs()
        val datei = File(ordner, name).apply { writeBytes(bytes) }
        return Uri.fromFile(datei).toString()
    }

    /** Kleiner Inhalt mit JPEG-Signatur (`FF D8 FF`) – mehr prüft der Sync nicht. */
    private fun jpeg(kennung: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), kennung.toByte(), 1, 2, 3)

    private suspend fun aktiv(allesHochladen: Boolean = false) = speicher.aktivieren("https://foody.example", "konto-1", allesHochladen)

    private suspend fun paket() = speicher.naechstesPaket(Protocol.MAX_PUSH_RECORDS, Protocol.MAX_PUSH_BYTES)

    // --- Aktivieren, Pakete, Push-Ergebnisse ---

    @Test
    fun inaktiverSyncTutNichts() = runTest {
        db.zutatDao().upsert(zutat("z1"))
        db.rezeptDao().upsert(rezept("r1"))
        dao.upsertFotoGewuenscht(SyncFotoGewuenschtEntity("r1", "a".repeat(64)))

        val zustand = speicher.zustand()
        assertFalse(zustand.aktiv)
        assertTrue(dao.outbox().isEmpty())
        assertTrue(paket().isEmpty())
        assertTrue(speicher.fotowuensche().isEmpty())
    }

    @Test
    fun allesHochladenMerktAllesVor() = runTest {
        db.zutatDao().upsert(zutat("z1"))
        db.rezeptDao().upsert(rezept("r1"))
        db.rezeptZutatDao().upsert(zeile("l1", "r1", "z1"))
        db.einkaufslisteDao().upsert(EinkaufslisteEntity(id = "e1", name = "Liste", createdAt = 1, updatedAt = 1))

        aktiv(allesHochladen = true)

        val zustand = speicher.zustand()
        assertTrue(zustand.aktiv)
        assertEquals("https://foody.example", zustand.serverUrl)
        assertEquals("konto-1", zustand.kontoId)
        assertEquals(0L, zustand.cursor)
        assertEquals(
            setOf("ingredient" to "z1", "recipe" to "r1", "shopping_list" to "e1"),
            dao.outbox().map { it.type to it.recordId }.toSet(),
        )
        // Abhängigkeiten zuerst: Zutat vor Rezept vor Liste
        assertEquals(listOf("z1", "r1", "e1"), paket().map { it.record.id })
    }

    @Test
    fun allesHochladenLaesstUnveraenderteStartzutatenAus() = runTest {
        // R-K1: Unveränderte Startzutaten (feste IDs, Zeitpunkt der Installation) überschrieben sonst die Server-Zutaten.
        val start = Startdaten.zutaten(1_000).associateBy { it.id }
        db.zutatDao().upsert(start.getValue("seed-reis"))
        db.zutatDao().upsert(start.getValue("seed-mehl").copy(category = "Backzutaten", updatedAt = 2_000))
        db.zutatDao().upsert(start.getValue("seed-salz").copy(salt = BigDecimal("100.0")))

        aktiv(allesHochladen = true)

        assertEquals(setOf("seed-mehl"), dao.outbox().map { it.recordId }.toSet())
    }

    @Test
    fun nurHerunterladenMerktNichtsVor() = runTest {
        db.zutatDao().upsert(zutat("z1"))
        db.rezeptDao().upsert(rezept("r1"))

        aktiv(allesHochladen = false)

        assertTrue(speicher.zustand().aktiv)
        assertTrue(dao.outbox().isEmpty())
        assertTrue(paket().isEmpty())
        // Danach gehen neue lokale Änderungen in die Outbox
        db.zutatDao().upsert(zutat("z2"))
        assertEquals(listOf("z2"), paket().map { it.record.id })
    }

    @Test
    fun paketHaeltGrenzenEin() = runTest {
        aktiv()
        db.withTransaction { repeat(600) { db.zutatDao().upsert(zutat("z$it")) } }

        val voll = paket()
        assertEquals(Protocol.MAX_PUSH_RECORDS, voll.size)

        // Byte-Grenze: ein Paket bleibt darunter (Hülle `{"records":[…]}` eingerechnet)
        val grenze = 2_000L
        val klein = speicher.naechstesPaket(Protocol.MAX_PUSH_RECORDS, grenze)
        assertTrue(klein.isNotEmpty() && klein.size < 100, "Paketgröße ${klein.size}")
        val bytes = Protocol.json.encodeToString(
            de.foody.api.protocol.PushRequest.serializer(),
            de.foody.api.protocol.PushRequest(klein.map { it.record }),
        ).toByteArray().size
        assertTrue(bytes <= grenze, "Paket hat $bytes Bytes")

        // Gesendete Fassungen lassen sich ausnehmen, damit der nächste Teil drankommt
        val rest = speicher.naechstesPaket(Protocol.MAX_PUSH_RECORDS, Protocol.MAX_PUSH_BYTES, voll.map { it.schluessel }.toSet())
        assertEquals(100, rest.size)
        assertTrue(rest.none { r -> voll.any { it.record.id == r.record.id } })
    }

    @Test
    fun geaendertWaehrendGesendetBleibtInOutbox() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1"))
        db.zutatDao().upsert(zutat("z2"))
        val (a1, a2) = paket()

        // z1 wird geändert, während das Paket unterwegs ist
        db.zutatDao().upsert(zutat("z1", name = "Neu"))
        speicher.erledigt(a1, rev = 7)
        speicher.erledigt(a2, rev = 8)

        assertEquals(listOf("z1"), dao.outbox().map { it.recordId })
        assertEquals(7L, dao.revision("ingredient", "z1")?.rev)
        assertEquals(8L, dao.revision("ingredient", "z2")?.rev)
        // Der nächste Push trägt die bestätigte Revision als baseRev
        assertEquals(7L, paket().single().record.baseRev)
    }

    @Test
    fun abgelehntWirdProblem() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1"))
        val a = paket().single()

        speicher.abgelehnt(a, "invalid_payload")

        assertEquals("invalid_payload", dao.problem("ingredient", "z1")?.code)
        assertTrue(dao.outbox().isEmpty())
    }

    @Test
    fun fehlendesVerweiszielBleibtUndWirdNachgereicht() = runTest {
        // Zutat lokal vorhanden, aber nie gesendet (nur herunterladen); das Rezept verweist darauf
        db.zutatDao().upsert(zutat("z1"))
        aktiv()
        db.rezeptDao().upsert(rezept("r1"))
        db.rezeptZutatDao().upsert(zeile("l1", "r1", "z1"))
        val a = paket().single()

        speicher.abgelehnt(a, "missing_reference")

        assertEquals("missing_reference", dao.problem("recipe", "r1")?.code)
        assertEquals(setOf("recipe" to "r1", "ingredient" to "z1"), dao.outbox().map { it.type to it.recordId }.toSet())
    }

    @Test
    fun zusammengefuehrtWendetServerStandAn() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1", name = "Milch"))
        val a = paket().single()

        speicher.zusammengefuehrt(a, rev = 5, kanonischeId = null, aktuell = zutatRecord("z1", "Milch (Server)", rev = 5))

        assertTrue(dao.outbox().isEmpty())
        assertEquals(5L, dao.revision("ingredient", "z1")?.rev)
        assertEquals("Milch (Server)", db.zutatDao().nachId("z1")?.canonicalName)
        assertEquals(0L, speicher.zustand().cursor)
    }

    @Test
    fun zusammengefuehrtInAnderenDatensatzMerktKeineRevision() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1", name = "Milch"))
        val a = paket().single()

        // Der Server hat z1 in seine z9 überführt und liefert deren Stand
        speicher.zusammengefuehrt(a, rev = 5, kanonischeId = "z9", aktuell = zutatRecord("z9", "Milch", rev = 5))

        assertNull(dao.revision("ingredient", "z1"))
        assertEquals(5L, dao.revision("ingredient", "z9")?.rev)
        assertNull(db.zutatDao().nachId("z1"))
        assertEquals("Milch", db.zutatDao().nachId("z9")?.canonicalName)
        assertTrue(dao.outbox().isEmpty())
    }

    @Test
    fun zusammengefuehrtNachLokalerAenderungBehaeltLokalenStand() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1", name = "Milch"))
        dao.upsertRevision(SyncRevisionEntity("ingredient", "z1", 2))
        val a = paket().single()
        db.zutatDao().upsert(zutat("z1", name = "Milch lokal"))

        speicher.zusammengefuehrt(a, rev = 5, kanonischeId = null, aktuell = zutatRecord("z1", "Milch (Server)", rev = 5))

        // Lokale Änderung gewinnt und geht im nächsten Lauf hinaus; die alte Revision bleibt
        assertEquals("Milch lokal", db.zutatDao().nachId("z1")?.canonicalName)
        assertEquals(listOf("z1"), dao.outbox().map { it.recordId })
        assertEquals(2L, dao.revision("ingredient", "z1")?.rev)
    }

    @Test
    fun ohneFotoEntferntFotoUndVermerktProblem() = runTest {
        aktiv()
        val link = eigeneDatei("eigen.jpg", jpeg(7))
        db.rezeptDao().upsert(rezept("r1", bild = link))
        db.zutatDao().upsert(zutat("z1"))
        val paket = paket()
        val rezept = paket.single { it.record.type == RecordType.RECIPE }
        assertNotNull(fotoVon(rezept.record))

        val ohne = rezept.ohneFoto()

        assertNull(fotoVon(ohne.record))
        assertEquals("photo_unsyncable", ohne.fotoProblem)
        assertNull(ohne.fotoHash)
        assertNull(ohne.fotoDatei)
        assertEquals(rezept.vorgemerktUm, ohne.vorgemerktUm)
        assertEquals("Rezept r1", (RecordType.RECIPE.decode(ohne.record.payload!!) as RecipePayload).name)
        // Andere Typen bleiben unverändert
        val zutat = paket.single { it.record.type == RecordType.INGREDIENT }
        assertEquals(zutat, zutat.ohneFoto())
    }

    // --- Anwenden ---

    @Test
    fun anwendenSchreibtKeineOutbox() = runTest {
        aktiv()
        speicher.anwenden(
            listOf(zutatRecord("z1", "Mehl"), rezeptRecord("r1", foto = null, zutaten = listOf("z1")), planRecord("m1", "r1")),
            neuerCursor = 42,
        )

        assertEquals("Mehl", db.zutatDao().nachId("z1")?.canonicalName)
        assertEquals(1, db.rezeptZutatDao().nachRezept("r1").size)
        assertNotNull(db.planEintragDao().nachId("m1"))
        assertTrue(dao.outbox().isEmpty())
        assertEquals(3, dao.revisionen().size)
        val zustand = speicher.zustand()
        assertEquals(42L, zustand.cursor)
        assertNotNull(zustand.letzterSync)
        assertFalse(dao.zustand()!!.applyingRemote)
        // Trigger wirken danach wieder
        db.zutatDao().upsert(zutat("z2"))
        assertEquals(listOf("z2"), dao.outbox().map { it.recordId })
    }

    @Test
    fun anwendenUeberspringtOffeneLokaleAenderung() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1", name = "Lokal"))

        speicher.anwenden(listOf(zutatRecord("z1", "Server")), neuerCursor = 1)

        assertEquals("Lokal", db.zutatDao().nachId("z1")?.canonicalName)
        assertEquals(listOf("z1"), dao.outbox().map { it.recordId })
    }

    @Test
    fun ungueltigerDatensatzWirdProblemUndBrichtNichtAb() = runTest {
        aktiv()
        val kaputt = zutatRecord("z1", "")
        val ohneZiel = rezeptRecord("r1", foto = null, zutaten = listOf("fehlt"))

        speicher.anwenden(listOf(kaputt, ohneZiel, zutatRecord("z2", "Salz")), neuerCursor = 9)

        assertEquals("invalid_payload", dao.problem("ingredient", "z1")?.code)
        assertEquals("missing_reference", dao.problem("recipe", "r1")?.code)
        assertNull(db.rezeptDao().nachId("r1"))
        assertEquals("Salz", db.zutatDao().nachId("z2")?.canonicalName)
        assertEquals(9L, speicher.zustand().cursor)
    }

    @Test
    fun gleichnamigeZutatWirdZusammengefuehrt() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("lokal", name = "Mehl").copy(category = "Backen"))
        db.rezeptDao().upsert(rezept("r1"))
        db.rezeptZutatDao().upsert(zeile("l1", "r1", "lokal"))
        dao.loeschenOutbox("ingredient", "lokal")
        dao.loeschenOutbox("recipe", "r1")

        speicher.anwenden(listOf(zutatRecord("server", "Mehl")), neuerCursor = 3)

        assertNull(db.zutatDao().nachId("lokal"))
        val server = db.zutatDao().nachId("server")!!
        assertEquals("Mehl", server.canonicalName)
        assertEquals("Backen", server.category)
        assertEquals("server", db.rezeptZutatDao().nachId("l1")?.ingredientId)
        // Umgehängtes Rezept und ergänzte Server-Zutat gehen hinaus, die lokale Zutat nicht
        assertEquals(
            setOf("recipe" to "r1", "ingredient" to "server"),
            dao.outbox().map { it.type to it.recordId }.toSet(),
        )
    }

    @Test
    fun zusammenfuehrenSchuetztOffeneRezeptaenderung() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("lokal", name = "Mehl"))
        db.rezeptDao().upsert(rezept("r1"))
        db.rezeptZutatDao().upsert(zeile("l1", "r1", "lokal"))
        dao.loeschenOutbox("ingredient", "lokal")
        // Offene Nutzeränderung am Rezept
        db.rezeptDao().upsert(db.rezeptDao().nachId("r1")!!.copy(name = "Lokal geändert"))
        assertEquals(listOf("r1"), dao.outbox().map { it.recordId })

        // Dieselbe Pull-Seite: kollidierende Zutat (Zusammenführung hängt r1 um) und ein neuerer Server-Stand von r1
        speicher.anwenden(listOf(zutatRecord("server", "Mehl"), rezeptRecord("r1", null, listOf("server"), rev = 2)), 4)

        val r1 = db.rezeptDao().nachId("r1")!!
        assertEquals("Lokal geändert", r1.name)
        assertEquals(listOf("l1"), db.rezeptZutatDao().nachRezept("r1").map { it.id })
        assertEquals("server", db.rezeptZutatDao().nachId("l1")?.ingredientId)
        assertNotNull(dao.outboxEintrag("recipe", "r1"))
        // Der nächste Push trägt den lokalen Stand
        assertEquals("Lokal geändert", (RecordType.RECIPE.decode(paket().single { it.record.id == "r1" }.record.payload!!) as RecipePayload).name)
    }

    @Test
    fun zusammenfuehrenHaengtArtikelUndTagebuchUm() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("lokal", name = "Mehl"))
        db.einkaufslisteDao().upsert(EinkaufslisteEntity(id = "e1", name = "Liste", createdAt = 1, updatedAt = 1))
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a1", listId = "e1", ingredientId = "lokal", name = "Mehl", sortOrder = 0))
        db.tagebuchDao().upsert(
            TagebuchEintragEntity(
                id = "t1", datum = java.time.LocalDate.of(2026, 10, 18), mahlzeit = "DINNER", art = TagebuchArt.ZUTAT,
                name = "Mehl", zutatId = "lokal", createdAt = 1, updatedAt = 1,
            ),
        )
        dao.outbox().forEach { dao.loeschenOutbox(it.type, it.recordId) }

        speicher.anwenden(listOf(zutatRecord("server", "Mehl")), 5)

        assertNull(db.zutatDao().nachId("lokal"))
        assertEquals("server", db.einkaufsartikelDao().nachId("a1")?.ingredientId)
        assertEquals("server", db.tagebuchDao().nachId("t1")?.zutatId)
        // Umgehängte Datensätze gehen hinaus; die Server-Zutat wurde nicht ergänzt und die lokale verschwindet still
        assertEquals(
            setOf("shopping_item" to "a1", "tagebuch_eintrag" to "t1"),
            dao.outbox().map { it.type to it.recordId }.toSet(),
        )
    }

    @Test
    fun loeschungEinerZutatLoestEinkaufsartikel() = runTest {
        aktiv()
        speicher.anwenden(listOf(zutatRecord("z1", "Mehl")), neuerCursor = 2)
        db.einkaufslisteDao().upsert(EinkaufslisteEntity("e1", "Einkauf", createdAt = 1, updatedAt = 1))
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a1", listId = "e1", ingredientId = "z1", name = "Mehl", sortOrder = 0))
        dao.outbox().forEach { dao.loeschenOutbox(it.type, it.recordId) }

        speicher.anwenden(
            listOf(SyncRecord(id = "z1", type = RecordType.INGREDIENT, deleted = true, updatedAt = 200, rev = 3)),
            neuerCursor = 3,
        )

        assertNull(db.zutatDao().nachId("z1"))
        assertNull(db.einkaufsartikelDao().nachId("a1")?.ingredientId)
        // Beim Anwenden von Server-Daten geht nichts hinaus
        assertTrue(dao.outbox().isEmpty())
    }

    @Test
    fun loeschungEinerBenutztenZutatBelebtSieWieder() = runTest {
        aktiv()
        speicher.anwenden(listOf(zutatRecord("z1", "Mehl"), rezeptRecord("r1", null, listOf("z1"))), neuerCursor = 2)

        speicher.anwenden(
            listOf(SyncRecord(id = "z1", type = RecordType.INGREDIENT, deleted = true, updatedAt = 200, rev = 3)),
            neuerCursor = 3,
        )

        assertNotNull(db.zutatDao().nachId("z1"))
        assertEquals(3L, dao.revision("ingredient", "z1")?.rev)
        val eintrag = dao.outboxEintrag("ingredient", "z1")
        assertNotNull(eintrag)
        assertFalse(eintrag.deleted)
    }

    @Test
    fun bekanntesFotoWirdVerknuepft() = runTest {
        aktiv()
        val bytes = jpeg(1)
        val link = eigeneDatei("vorhanden.jpg", bytes)
        val hash = index.hash(link)!!

        speicher.anwenden(listOf(rezeptRecord("r1", foto = hash)), neuerCursor = 1)

        assertEquals(link, db.rezeptDao().nachId("r1")?.imageUri)
        assertNull(dao.fotoGewuenscht("r1"))
        assertTrue(speicher.fotowuensche().isEmpty())
    }

    @Test
    fun unbekanntesFotoWirdGewuenscht() = runTest {
        aktiv()
        val bytes = jpeg(2)
        val hash = PhotoHash.of(bytes)

        speicher.anwenden(listOf(rezeptRecord("r1", foto = hash)), neuerCursor = 1)

        assertNull(db.rezeptDao().nachId("r1")?.imageUri)
        assertEquals(listOf("r1" to hash), speicher.fotowuensche())
        // Solange das Foto fehlt, sendet das Gerät den Server-Hash weiter (nicht „kein Foto“)
        db.rezeptDao().upsert(db.rezeptDao().nachId("r1")!!.copy(name = "Geändert"))
        assertEquals(hash, fotoVon(paket().single().record))
        val vorgemerkt = dao.outbox().single().queuedAt

        // Falsche Bytes: Problem, der Wunsch bleibt
        speicher.fotoErhalten("r1", hash, jpeg(3))
        assertEquals("photo_mismatch", dao.problem("recipe", "r1")?.code)
        assertNotNull(dao.fotoGewuenscht("r1"))

        speicher.fotoErhalten("r1", hash, bytes)
        val link = db.rezeptDao().nachId("r1")?.imageUri
        assertNotNull(link)
        assertEquals(bytes.toList(), ablage.datei(link)!!.readBytes().toList())
        assertNull(dao.fotoGewuenscht("r1"))
        assertEquals(hash, index.hash(link))
        // Das Verknüpfen ist keine lokale Änderung
        assertEquals(vorgemerkt, dao.outbox().single().queuedAt)
        assertNull(dao.problem("recipe", "r1"))
    }

    @Test
    fun nullFotoBehaeltGalerieLink() = runTest {
        aktiv()
        val galerie = "content://media/external/images/media/17"
        db.rezeptDao().upsert(rezept("r1", bild = galerie))
        dao.loeschenOutbox("recipe", "r1")

        speicher.anwenden(listOf(rezeptRecord("r1", foto = null)), neuerCursor = 1)

        assertEquals("Server r1", db.rezeptDao().nachId("r1")?.name)
        assertEquals(galerie, db.rezeptDao().nachId("r1")?.imageUri)
    }

    @Test
    fun nullFotoEntferntEigenesFoto() = runTest {
        aktiv()
        val link = eigeneDatei("eigen.jpg", jpeg(4))
        db.rezeptDao().upsert(rezept("r1", bild = link))
        dao.loeschenOutbox("recipe", "r1")

        speicher.anwenden(listOf(rezeptRecord("r1", foto = null)), neuerCursor = 1)

        assertNull(db.rezeptDao().nachId("r1")?.imageUri)
        assertTrue(dao.outbox().isEmpty())
    }

    // --- Fotos beim Senden ---

    @Test
    fun eigenesFotoWirdMitHashGesendet() = runTest {
        aktiv()
        val bytes = jpeg(5)
        val link = eigeneDatei("eigen.jpg", bytes)
        db.rezeptDao().upsert(rezept("r1", bild = link))

        val a = paket().single()

        assertEquals(PhotoHash.of(bytes), fotoVon(a.record))
        assertEquals(PhotoHash.of(bytes), a.fotoHash)
        assertEquals(ablage.datei(link), a.fotoDatei)
        assertNull(a.fotoProblem)
    }

    @Test
    fun galerieLinkWirdNichtGesendet() = runTest {
        aktiv()
        val galerie = "content://com.android.providers.media.documents/document/image%3A99"
        db.rezeptDao().upsert(rezept("r1", bild = galerie))

        val a = paket().single()

        assertNull(fotoVon(a.record))
        assertNull(a.fotoHash)
        assertNull(a.fotoDatei)
        assertNull(a.fotoProblem)
        speicher.erledigt(a, rev = 1)
        // Das Echo des eigenen Pushs (ohne Foto) lässt den Galerie-Link stehen
        speicher.anwenden(listOf(a.record.copy(rev = 2)), neuerCursor = 2)
        assertEquals(galerie, db.rezeptDao().nachId("r1")?.imageUri)
    }

    @Test
    fun fehlendeEigeneDateiSendetKeinFoto() = runTest {
        aktiv()
        ordner.mkdirs()
        val link = Uri.fromFile(File(ordner, "weg.jpg")).toString()
        db.rezeptDao().upsert(rezept("r1", bild = link))

        val a = paket().single()

        assertNull(fotoVon(a.record))
        assertNull(a.fotoHash)
        assertNull(a.fotoDatei)
        assertNull(a.fotoProblem)
    }

    @Test
    fun uebergrosseEigeneDateiWirdOhneFotoGesendet() = runTest {
        aktiv()
        // Über der Server-Grenze und nicht dekodierbar (auch Verkleinern hilft nicht)
        val gross = ByteArray((Protocol.MAX_PHOTO_BYTES + 1).toInt()).also { jpeg(6).copyInto(it) }
        val link = eigeneDatei("gross.jpg", gross)
        db.rezeptDao().upsert(rezept("r1", bild = link))
        // Kein JPEG (etwa ein unverändert übernommenes PNG unter .jpg): ebenso nicht übertragbar
        val png = eigeneDatei("png.jpg", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2))
        db.rezeptDao().upsert(rezept("r2", bild = png))

        val paket = paket()

        for (a in paket) {
            assertNull(fotoVon(a.record), a.record.id)
            assertNull(a.fotoHash)
            assertNull(a.fotoDatei)
            assertEquals("photo_unsyncable", a.fotoProblem)
        }
        assertEquals(setOf("r1", "r2"), paket.map { it.record.id }.toSet())
        paket.forEach { speicher.erledigt(it, rev = 1) }
        assertEquals("photo_unsyncable", dao.problem("recipe", "r1")?.code)
        // Das eigene Foto bleibt lokal, auch wenn das Echo ohne Foto zurückkommt
        speicher.anwenden(paket.map { it.record.copy(rev = 2) }, neuerCursor = 2)
        assertEquals(link, db.rezeptDao().nachId("r1")?.imageUri)
        assertEquals("photo_unsyncable", dao.problem("recipe", "r1")?.code)
    }

    // --- Voll-Abgleich ---

    @Test
    fun vollabgleichBehaeltBenutzteZutat() = runTest {
        aktiv()
        speicher.anwenden(listOf(zutatRecord("z1", "Mehl"), zutatRecord("z2", "Zucker"), rezeptRecord("r1", null, listOf("z1"))), 5)

        // Der Server kennt z1 und z2 nicht mehr, das Rezept verweist aber auf z1
        speicher.vollabgleich(listOf(rezeptRecord("r1", null, listOf("z1"), rev = 6)), cursor = 50)

        assertNotNull(db.zutatDao().nachId("z1"))
        assertNull(db.zutatDao().nachId("z2"))
        assertNotNull(db.rezeptDao().nachId("r1"))
        assertEquals(listOf("ingredient" to "z1"), dao.outbox().map { it.type to it.recordId })
        assertEquals(50L, speicher.zustand().cursor)
    }

    @Test
    fun vollabgleichBehaeltVerwiesenesRezept() = runTest {
        aktiv()
        speicher.anwenden(listOf(rezeptRecord("r1", null), rezeptRecord("r2", null)), 5)

        speicher.vollabgleich(listOf(planRecord("m1", "r1")), cursor = 60)

        assertNotNull(db.rezeptDao().nachId("r1"))
        assertNull(db.rezeptDao().nachId("r2"))
        assertNotNull(db.planEintragDao().nachId("m1"))
        assertEquals(listOf("recipe" to "r1"), dao.outbox().map { it.type to it.recordId })
    }

    @Test
    fun vollabgleichBehaeltListeMitWartendemEintrag() = runTest {
        aktiv()
        db.einkaufslisteDao().upsert(EinkaufslisteEntity(id = "e1", name = "Liste", createdAt = 1, updatedAt = 1))
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a1", listId = "e1", name = "Milch", sortOrder = 0))
        // Die Liste selbst ist schon gesendet, nur der Artikel wartet
        dao.loeschenOutbox("shopping_list", "e1")

        speicher.vollabgleich(emptyList(), cursor = 70)

        assertNotNull(db.einkaufslisteDao().nachId("e1"))
        assertNotNull(db.einkaufsartikelDao().nachId("a1"))
        assertEquals(
            setOf("shopping_list" to "e1", "shopping_item" to "a1"),
            dao.outbox().map { it.type to it.recordId }.toSet(),
        )
    }

    @Test
    fun vollabgleichBehaeltAbgelehntenDatensatz() = runTest {
        aktiv()
        db.zutatDao().upsert(zutat("z1"))
        db.zutatDao().upsert(zutat("z2"))
        dao.loeschenOutbox("ingredient", "z1")
        dao.loeschenOutbox("ingredient", "z2")
        dao.upsertProblem(SyncProblemEntity("ingredient", "z1", "invalid_payload", 1))
        dao.upsertRevision(SyncRevisionEntity("ingredient", "z2", 1))

        speicher.vollabgleich(emptyList(), cursor = 80)

        assertNotNull(db.zutatDao().nachId("z1"))
        assertNull(db.zutatDao().nachId("z2"))
        assertTrue(dao.outbox().isEmpty())
        assertEquals(80L, speicher.zustand().cursor)
    }

    // --- Trennen, Fehler ---

    @Test
    fun deaktivierenWartetAufLaufUndLeertFotowuensche() = runTest {
        aktiv()
        speicher.anwenden(listOf(rezeptRecord("r1", foto = "b".repeat(64))), 3)
        db.zutatDao().upsert(zutat("z1"))
        dao.upsertProblem(SyncProblemEntity("ingredient", "z9", "invalid_payload", 1))
        speicher.fehler("offline")

        speicher.laufSperre.lock()
        val trennen = launch { speicher.deaktivieren() }
        yield() // Trennen startet und wartet an der Sperre
        assertTrue(trennen.isActive)
        assertTrue(speicher.zustand().aktiv)
        speicher.laufSperre.unlock()
        trennen.join()

        val zustand = speicher.zustand()
        assertFalse(zustand.aktiv)
        assertNull(zustand.serverUrl)
        assertNull(zustand.kontoId)
        assertEquals(0L, zustand.cursor)
        assertNull(zustand.letzterFehler)
        assertTrue(dao.outbox().isEmpty())
        assertTrue(dao.revisionen().isEmpty())
        assertTrue(dao.probleme().isEmpty())
        assertTrue(dao.fotosGewuenscht().isEmpty())
        // Lokale Daten bleiben
        assertNotNull(db.rezeptDao().nachId("r1"))
        assertNotNull(db.zutatDao().nachId("z1"))
    }

    @Test
    fun fehlerkennungWirdGemerkt() = runTest {
        aktiv()
        speicher.fehler("server_fehler")
        assertEquals("server_fehler", speicher.zustand().letzterFehler)
        speicher.fehler(null)
        assertNull(speicher.zustand().letzterFehler)
    }
}
