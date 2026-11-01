package de.foody.core.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.PushResponse
import de.foody.api.protocol.PushResult
import de.foody.api.protocol.PushStatus
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.api.protocol.decode
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.baueDatenbank
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncRevisionEntity
import de.foody.core.data.db.SyncTrigger
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.startdaten.Startdaten
import de.foody.core.data.sync.SyncSpeicher
import de.foody.core.data.zutaten.ZutatErgebnis
import de.foody.core.data.zutaten.ZutatenRepository
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import de.foody.server.sync.Compactor
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Zwischenschicht vor dem echten Server-Zugang: protokolliert Aufrufe und lässt einzelne Aufrufe gezielt scheitern
 * oder anders antworten (Zustände, die der echte Server nicht liefert).
 */
private class StoerZugang(private val echt: ServerZugang) : ServerZugang by echt {
    val aufrufe = ArrayList<String>()
    val pulls = ArrayList<Pair<Long, Int>>()
    val pushes = ArrayList<List<SyncRecord>>()
    var beiPush: suspend (List<SyncRecord>) -> PushResponse = { echt.push(it) }
    var beiPull: suspend (Long, Int) -> PullResponse = { since, limit -> echt.pull(since, limit) }
    var beiFotosFehlend: suspend (List<String>) -> List<String> = { echt.fotosFehlend(it) }
    var beiFotoHochladen: suspend (String, ByteArray) -> Unit = { sha, bytes -> echt.fotoHochladen(sha, bytes) }
    var beiFotoLaden: suspend (String) -> ByteArray? = { echt.fotoLaden(it) }

    override suspend fun push(datensaetze: List<SyncRecord>): PushResponse {
        aufrufe += "push"
        pushes += datensaetze
        return beiPush(datensaetze)
    }

    override suspend fun pull(since: Long, limit: Int): PullResponse {
        aufrufe += "pull"
        pulls += since to limit
        return beiPull(since, limit)
    }

    override suspend fun fotosFehlend(hashes: List<String>): List<String> {
        aufrufe += "fotosFehlend"
        return beiFotosFehlend(hashes)
    }

    override suspend fun fotoHochladen(sha256: String, bytes: ByteArray) {
        aufrufe += "fotoHochladen"
        beiFotoHochladen(sha256, bytes)
    }

    override suspend fun fotoLaden(sha256: String): ByteArray? {
        aufrufe += "fotoLaden"
        return beiFotoLaden(sha256)
    }
}

/**
 * Sync-Lauf gegen den echten Sync-Server (Protokoll 3) mit Room unter Robolectric: die Verhaltensliste der v1
 * (`SyncEngineTest`) für Protokoll 3 neu geschrieben, dazu Umstieg aus der v1 (Migration 9 → 10, alter Token und Cursor)
 * und die festen Fehlerkennungen. Echte Grafik, damit der Verkleinerungsversuch unlesbare Bilder wirklich ablehnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncLaufTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)
    private val datenbanken = ArrayList<FoodyDatenbank>()

    @Before
    fun setUp() {
        ordner.deleteRecursively()
        context.deleteDatabase(V9_DB)
    }

    @After
    fun tearDown() {
        datenbanken.forEach { it.close() }
        ordner.deleteRecursively()
        context.deleteDatabase(V9_DB)
    }

    // --- Geräte ---

    /** Ein Gerät: eigene Datenbank, Sync-Speicher und Sync-Lauf mit [zugang]. */
    private inner class Geraet(val db: FoodyDatenbank, val zugang: StoerZugang) {
        val ablage = FotoAblage(context)
        val speicher = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        val lauf = SyncLauf(speicher) { zugang }
        val dao get() = db.syncDao()

        suspend fun ausfuehren() = lauf.ausfuehren()
    }

    private fun neueDb(): FoodyDatenbank = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .build()
        .also { datenbanken += it }

    /** Meldet ein Gerät beim Konto [name] an und liefert den Zugang mit seinem Token. */
    private suspend fun ApplicationTestBuilder.anmelden(name: String): KtorServerZugang {
        var token: String? = null
        val zugang = KtorServerZugang(TEST_BASIS, client) { token }
        token = zugang.anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät")).token
        return zugang
    }

    /** Neues angemeldetes Gerät mit aktivem Sync (ohne vorhandene Daten vorzumerken). */
    private suspend fun ApplicationTestBuilder.geraet(name: String, kontoId: String, db: FoodyDatenbank = neueDb()): Geraet {
        val g = Geraet(db, StoerZugang(anmelden(name)))
        g.speicher.aktivieren(TEST_BASIS, kontoId, allesHochladen = false)
        return g
    }

    /** Alles, was der Server für das Konto hat (Schlüssel: Typ und ID). */
    private suspend fun serverStand(zugang: ServerZugang): Map<Pair<RecordType, String>, SyncRecord> =
        zugang.pull(0, Protocol.MAX_PULL_LIMIT).records.associateBy { it.type to it.id }

    // --- Daten ---

    private fun zutat(id: String, name: String = "Zutat $id") = ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)

    private fun rezept(id: String, bild: String? = null) = RezeptEntity(
        id = id, name = "Rezept $id", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = bild,
        notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
    )

    /** Rezept [id] mit einer Zeile auf Zutat [zutatId] (die Zutat wird bei Bedarf angelegt). */
    private suspend fun Geraet.rezeptMitZutat(id: String, zutatId: String = "salz", bild: String? = null) {
        if (db.zutatDao().nachId(zutatId) == null) db.zutatDao().upsert(zutat(zutatId, "Salz"))
        db.rezeptDao().upsert(rezept(id, bild))
        db.rezeptZutatDao().upsert(
            RezeptZutatEntity(
                id = "$id-l", recipeId = id, ingredientId = zutatId, amount = BigDecimal.ONE, unit = MeasureUnit.GRAM,
                sortOrder = 0, preparationNote = null, optional = false,
            ),
        )
    }

    /** Kleiner Inhalt mit JPEG-Signatur (`FF D8 FF`) – mehr prüfen Client und Server nicht. */
    private fun jpeg(kennung: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), kennung.toByte(), 1, 2, 3)

    /** Eigene Fotodatei im Fotoordner; liefert den Link, wie die Ablage ihn speichert. */
    private fun eigeneDatei(name: String, bytes: ByteArray): String {
        ordner.mkdirs()
        return Uri.fromFile(File(ordner, name).apply { writeBytes(bytes) }).toString()
    }

    private fun fotoVon(record: SyncRecord?): String? = (RecordType.RECIPE.decode(requireNotNull(record).payload!!) as RecipePayload).photo

    // --- Tests ---

    @Test
    fun inaktiverSyncErfolgreichOhneAnfrage() = syncServerTest { _ ->
        var gefragt = 0
        val db = neueDb()
        val ablage = FotoAblage(context)
        val lauf = SyncLauf(SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))) {
            gefragt++
            null
        }
        db.zutatDao().upsert(zutat("a"))
        assertEquals(SyncErgebnis.Inaktiv, lauf.ausfuehren())
        assertEquals(0, gefragt, "ohne aktiven Sync wird kein Server-Zugang gebaut")
        assertNull(db.syncDao().zustand()!!.lastError)
    }

    @Test
    fun gescheiterteAbholungHoltDerLaufErneut() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.rezeptMitZutat("r1")
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        // B bekommt das Rezept einmal unlesbar: Problem vermerkt, der Cursor ist trotzdem vorbei.
        val b = geraet(name, konto)
        b.zugang.beiPull = { since, limit ->
            val echt = a.zugang.pull(since, limit)
            echt.copy(records = echt.records.map { if (it.type == RecordType.RECIPE) it.copy(payload = JsonObject(emptyMap())) else it })
        }
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals("invalid_payload", b.dao.problem("recipe", "r1")?.code)
        assertNull(b.db.rezeptDao().nachId("r1"))

        // Einen Tag später: Der Lauf holt alles ab 0 erneut und übernimmt das Rezept; der Cursor bleibt.
        b.dao.upsertProblem(b.dao.problem("recipe", "r1")!!.copy(at = 0))
        val cursor = b.dao.zustand()!!.cursor
        b.zugang.beiPull = { since, limit -> a.zugang.pull(since, limit) }
        b.zugang.pulls.clear()
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf(cursor, 0L), b.zugang.pulls.map { it.first })
        assertEquals("Rezept r1", b.db.rezeptDao().nachId("r1")?.name)
        assertNull(b.dao.problem("recipe", "r1"))
        assertEquals(cursor, b.dao.zustand()!!.cursor)

        // Ohne wartende Probleme kein zweites Holen.
        b.zugang.pulls.clear()
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf(cursor), b.zugang.pulls.map { it.first })
    }

    @Test
    fun vollerSpeicherBrichtAbOhneDatenAufzugeben() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        (1..300).forEach { a.db.zutatDao().upsert(zutat("z$it", "Zutat mit langem Namen Nummer $it")) }
        a.speicher.aktivieren(TEST_BASIS, konto, allesHochladen = true)
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val b = geraet(name, konto)
        val sql = b.db.openHelper.writableDatabase
        val seiten = sql.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
        sql.query("PRAGMA max_page_count = $seiten").use { it.moveToFirst() }
        assertEquals(SyncErgebnis.Fehlgeschlagen(ServerFehler.SPEICHER_VOLL, voruebergehend = true), b.ausfuehren())
        assertEquals(emptyList(), b.dao.probleme())
        assertEquals(0L, b.dao.zustand()!!.cursor)

        // Wieder Platz: Der nächste Lauf holt alles.
        sql.query("PRAGMA max_page_count = 1073741823").use { it.moveToFirst() }
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertNotNull(b.db.zutatDao().nachId("z300"))
        assertEquals(emptyList(), b.dao.probleme())
    }

    @Test
    fun rundreiseZweiGeraete() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.rezeptMitZutat("r1")
        a.db.planEintragDao().upsert(
            PlanEintragEntity("p1", LocalDate.of(2026, 10, 18), Mahlzeit.ABENDESSEN.name, "r1", servings = 2, createdAt = 1, updatedAt = 1),
        )
        a.db.einkaufslisteDao().upsert(EinkaufslisteEntity("l1", "Woche", createdAt = 1, updatedAt = 1))
        a.db.einkaufsartikelDao().upsert(EinkaufsartikelEntity("e1", "l1", name = "Kerzen", manual = true, sortOrder = 0, updatedAt = 1))
        a.db.tagebuchDao().upsert(
            TagebuchEintragEntity(
                "t1", LocalDate.of(2026, 10, 18), Mahlzeit.ABENDESSEN.name, TagebuchArt.FREI, "Apfel",
                createdAt = 1, updatedAt = 1,
            ),
        )
        val ergebnisA = a.ausfuehren()
        assertEquals(SyncErgebnis.Fertig(gesendet = 6, geholt = 6), ergebnisA) // Echo des eigenen Pushs
        assertEquals(emptyList(), a.dao.outbox())

        val b = geraet(name, konto)
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 6), b.ausfuehren())
        assertEquals("Rezept r1", b.db.rezeptDao().nachId("r1")!!.name)
        assertEquals(listOf("r1-l"), b.db.rezeptZutatDao().nachRezept("r1").map { it.id })
        assertNotNull(b.db.planEintragDao().nachId("p1"))
        assertEquals("Kerzen", b.db.einkaufsartikelDao().nachId("e1")!!.name)
        assertEquals("Apfel", b.db.tagebuchDao().nachId("t1")!!.name)
        assertEquals(emptyList(), b.dao.outbox(), "Angewendetes geht nicht als lokale Änderung zurück")

        // B ändert und löscht, A holt es.
        b.db.zutatDao().upsert(b.db.zutatDao().nachId("salz")!!.copy(category = "Gewürz", updatedAt = 5))
        b.db.planEintragDao().loeschen("p1")
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(emptyList(), b.dao.outbox())
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals("Gewürz", a.db.zutatDao().nachId("salz")!!.category)
        assertNull(a.db.planEintragDao().nachId("p1"))
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(emptyList(), a.dao.probleme() + b.dao.probleme())
        assertNull(a.speicher.zustand().letzterFehler)
        assertNotNull(a.speicher.zustand().letzterSync)
    }

    @Test
    fun fehlendeFotosVorDemPushHochladen() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val bytes = jpeg(1)
        a.rezeptMitZutat("r1", bild = eigeneDatei("eigen.jpg", bytes))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        val hash = PhotoHash.of(bytes)
        assertEquals(listOf("fotosFehlend", "fotoHochladen", "push"), a.zugang.aufrufe.take(3))
        assertTrue(server.deps.photos.exists(konto, hash))
        assertEquals(hash, fotoVon(serverStand(a.zugang)[RecordType.RECIPE to "r1"]))
        assertEquals(emptyList(), a.dao.outbox())
    }

    @Test
    fun uploadFehlerBehaeltOutboxVoruebergehend() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.rezeptMitZutat("r1", bild = eigeneDatei("eigen.jpg", jpeg(2)))
        a.zugang.beiFotoHochladen = { _, _ -> throw ServerFehler.Offline() }
        assertEquals(SyncErgebnis.Fehlgeschlagen("offline", voruebergehend = true), a.ausfuehren())
        assertTrue(a.dao.outbox().any { it.type == "recipe" })
        assertTrue(a.zugang.pushes.isEmpty(), "nichts aus dem Paket geht ohne sein Foto hinaus")
        assertEquals("offline", a.speicher.zustand().letzterFehler)
    }

    @Test
    fun gewuenschtesFotoWirdGeladenOhneOutboxEcho() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val bytes = jpeg(3)
        a.rezeptMitZutat("r1", bild = eigeneDatei("eigen.jpg", bytes))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val b = geraet(name, konto)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        val link = assertNotNull(b.db.rezeptDao().nachId("r1")!!.imageUri)
        assertNotEquals(a.db.rezeptDao().nachId("r1")!!.imageUri, link, "eigene Datei des Geräts B")
        assertTrue(bytes.contentEquals(b.ablage.datei(link)!!.readBytes()))
        assertEquals(emptyList(), b.dao.fotosGewuenscht())
        assertEquals(emptyList(), b.dao.outbox())
        assertEquals(emptyList(), b.dao.probleme())
        assertEquals(1, b.zugang.aufrufe.count { it == "fotoLaden" })
    }

    @Test
    fun fotoNichtAufServerBleibtGewuenscht() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val bytes = jpeg(4)
        val hash = PhotoHash.of(bytes)
        a.rezeptMitZutat("r1", bild = eigeneDatei("eigen.jpg", bytes))
        // Das Rezept kommt an, das Foto (noch) nicht – etwa weil A' Upload später kommt.
        a.zugang.beiFotosFehlend = { emptyList() }
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertFalse(server.deps.photos.exists(konto, hash))

        val b = geraet(name, konto)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertNull(b.db.rezeptDao().nachId("r1")!!.imageUri)
        assertEquals(listOf(hash), b.dao.fotosGewuenscht().map { it.sha256 })

        // Später ist es da: der nächste Lauf holt es nach.
        a.zugang.fotoHochladen(hash, bytes)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertNotNull(b.db.rezeptDao().nachId("r1")!!.imageUri)
        assertEquals(emptyList(), b.dao.fotosGewuenscht())
        assertEquals(emptyList(), b.dao.outbox())
    }

    @Test
    fun nichtJpegWirdOhneFotoGesendet() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val link = eigeneDatei("kaputt.jpg", byteArrayOf(1, 2, 3, 4))
        a.rezeptMitZutat("r1", bild = link)
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertNull(fotoVon(serverStand(a.zugang)[RecordType.RECIPE to "r1"]))
        assertFalse("fotoHochladen" in a.zugang.aufrufe)
        assertEquals(listOf("r1" to SyncSpeicher.FOTO_NICHT_UEBERTRAGBAR), a.dao.probleme().map { it.recordId to it.code })
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(link, a.db.rezeptDao().nachId("r1")!!.imageUri, "das eigene Foto bleibt lokal")
    }

    @Test
    fun uploadClientFehlerBetrifftNurDiesesRezept() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val schlecht = jpeg(5)
        val gut = jpeg(6)
        a.rezeptMitZutat("r1", bild = eigeneDatei("schlecht.jpg", schlecht))
        a.rezeptMitZutat("r2", bild = eigeneDatei("gut.jpg", gut))
        var ablehnungen = 0
        val echt = a.zugang.beiFotoHochladen
        a.zugang.beiFotoHochladen = { sha, bytes ->
            // z. B. ein Reverse-Proxy mit kleinerem Body-Limit oder ein 4xx des Servers
            if (sha == PhotoHash.of(schlecht)) {
                ablehnungen++
                throw ServerFehler.Abgelehnt(400, ErrorCode.INVALID_INPUT)
            }
            echt(sha, bytes)
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 3, geholt = 3), a.ausfuehren())
        val stand = serverStand(a.zugang)
        assertNull(fotoVon(stand[RecordType.RECIPE to "r1"]))
        assertEquals(PhotoHash.of(gut), fotoVon(stand[RecordType.RECIPE to "r2"]))
        assertEquals(listOf("r1" to SyncSpeicher.FOTO_NICHT_UEBERTRAGBAR), a.dao.probleme().map { it.recordId to it.code })
        assertEquals(emptyList(), a.dao.outbox())
        assertNotNull(a.db.rezeptDao().nachId("r1")!!.imageUri, "das eigene Foto bleibt lokal")

        // Der nächste Lauf lädt das abgelehnte Foto nicht erneut hoch.
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(1, ablehnungen)
    }

    @Test
    fun verschwundeneDateiHaeltNurDiesesRezeptZurueck() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val weg = eigeneDatei("weg.jpg", jpeg(7))
        a.rezeptMitZutat("r1", bild = weg)
        a.rezeptMitZutat("r2")
        val echt = a.zugang.beiFotosFehlend
        // Die Datei verschwindet zwischen Paketbau und Upload.
        a.zugang.beiFotosFehlend = { hashes ->
            a.ablage.datei(weg)!!.delete()
            echt(hashes)
        }
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf("r2"), a.zugang.pushes.flatten().filter { it.type == RecordType.RECIPE }.map { it.id })
        assertEquals(listOf("r1"), a.dao.outbox().map { it.recordId }, "bleibt in der Outbox")
        assertEquals(emptyList(), a.dao.probleme())

        // Der nächste Lauf schickt es ohne das fehlende Foto.
        a.zugang.beiFotosFehlend = echt
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(emptyList(), a.dao.outbox())
        assertNull(fotoVon(serverStand(a.zugang)[RecordType.RECIPE to "r1"]))
    }

    @Test
    fun zusammenfuehrenAufFrischemGeraetBehaeltKorrigierteStartzutaten() = syncServerTest { server ->
        // R-K1: A hat eine Startzutat im Katalog korrigiert; B wird neu installiert (Startzutaten später angelegt) und
        // verbindet sich mit „Zusammenführen“. Die Korrektur bleibt auf dem Server und kommt auf B an.
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val mehl = Startdaten.zutaten(1_000).single { it.id == "seed-mehl" }
        a.db.zutatDao().upsert(mehl.copy(category = "Backzutaten", energyKj = BigDecimal("1460"), updatedAt = 2_000))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val bDb = neueDb()
        for (z in Startdaten.zutaten(9_000)) bDb.zutatDao().upsert(z)
        val b = Geraet(bDb, StoerZugang(anmelden(name)))
        b.speicher.aktivieren(TEST_BASIS, konto, allesHochladen = true)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)

        val aufServer = serverStand(a.zugang).getValue(RecordType.INGREDIENT to "seed-mehl").payload!!
        assertEquals(JsonPrimitive("Backzutaten"), aufServer["category"])
        assertEquals("Backzutaten", b.db.zutatDao().nachId("seed-mehl")!!.category)
        assertEquals(0, BigDecimal("1460").compareTo(b.db.zutatDao().nachId("seed-mehl")!!.energyKj))
        assertEquals(emptyList(), b.dao.outbox())
        assertEquals(emptyList(), b.dao.probleme())
    }

    @Test
    fun cursorAbgelaufenLoestVollabgleichAus() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("x"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        val b = geraet(name, konto)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        val alterCursor = b.speicher.zustand().cursor
        assertTrue(alterCursor > 0)

        // A legt an und löscht wieder; die Löschmarkierung wird kompaktiert – B hat sie verpasst.
        a.db.zutatDao().upsert(zutat("y"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        a.db.zutatDao().loeschen("y")
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        server.uhr.vorstellen(Duration.ofDays(91))
        assertEquals(1, Compactor(server.deps.db, server.uhr).run())

        // B: eine früher vom Server bestätigte Zutat, die er nicht mehr kennt (veraltet), eine nie gesendete
        // (unveränderte Startzutat nach „Zusammenführen“) und eine neue.
        b.db.zutatDao().upsert(zutat("veraltet"))
        b.dao.loeschenOutbox("ingredient", "veraltet")
        b.dao.upsertRevision(SyncRevisionEntity("ingredient", "veraltet", 1))
        b.db.zutatDao().upsert(zutat("nie-gesendet"))
        b.dao.loeschenOutbox("ingredient", "nie-gesendet")
        b.db.zutatDao().upsert(zutat("neu"))
        b.zugang.pulls.clear()
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf(alterCursor, 0L), b.zugang.pulls.map { it.first })
        assertNotNull(b.db.zutatDao().nachId("x"))
        assertNotNull(b.db.zutatDao().nachId("neu"), "vor dem Abgleich gesendet, also beim Server")
        assertNull(b.db.zutatDao().nachId("veraltet"))
        assertNotNull(b.db.zutatDao().nachId("nie-gesendet"), "der Server kannte sie nie, hat sie also nicht gelöscht (N8)")
        assertNull(b.db.zutatDao().nachId("y"))
        assertEquals(emptyList(), b.dao.outbox(), "das Löschen im Abgleich merkt nichts vor")
        assertFalse(b.dao.zustand()!!.applyingRemote)
        assertTrue(b.speicher.zustand().cursor > alterCursor)
        assertNull(b.speicher.zustand().letzterFehler)
    }

    @Test
    fun vollabgleichUeberstehtKaputtenServerDatensatz() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val b = geraet(name, konto)
        b.db.zutatDao().upsert(zutat("veraltet"))
        b.dao.loeschenOutbox("ingredient", "veraltet")
        b.dao.upsertRevision(SyncRevisionEntity("ingredient", "veraltet", 1))
        b.dao.upsertZustand(b.dao.zustand()!!.copy(cursor = 5))
        val kaputt = SyncRecord(
            id = "kaputt", type = RecordType.RECIPE, updatedAt = 10, rev = 3,
            payload = buildJsonObject { put("unbekannt", JsonPrimitive(1)) },
        )
        b.zugang.beiPull = { since, _ ->
            if (since != 0L) throw ServerFehler.CursorAbgelaufen()
            PullResponse(listOf(kaputt), 11, false)
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 1), b.ausfuehren())
        assertEquals(11L, b.speicher.zustand().cursor)
        assertNull(b.db.zutatDao().nachId("veraltet"))
        assertEquals(listOf("kaputt" to SyncSpeicher.UNGUELTIGER_PAYLOAD), b.dao.probleme().map { it.recordId to it.code })
    }

    /** D4: Nach dem Einspielen einer älteren Server-Sicherung bringt das Gerät zurück, was dem Server fehlt. */
    @Test
    fun nachWiederherstellungSendetDasGeraetFehlendesErneut() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("alt"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig) // Revision 1: Stand der Sicherung
        a.db.zutatDao().upsert(zutat("neu"))
        a.db.zutatDao().upsert(zutat("alt", name = "Alt, geändert"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig) // Revisionen 2 und 3: fehlen nach dem Einspielen

        // Sicherung mit Stand 1 einspielen, dann `foody-admin wiederhergestellt`
        server.deps.db.tx { c ->
            c.createStatement().use { s ->
                s.executeUpdate("DELETE FROM sync_record WHERE id = 'neu'")
                s.executeUpdate("UPDATE sync_record SET rev = 1, payload = json_set(payload, '$.name', 'Zutat alt') WHERE id = 'alt'")
                s.executeUpdate("UPDATE user SET last_rev = 1")
            }
        }
        server.deps.sync.wiederhergestellt()

        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig) // 410, Voll-Abgleich: beides wird vorgemerkt
        assertEquals("Alt, geändert", a.db.zutatDao().nachId("alt")?.canonicalName)
        assertNotNull(a.db.zutatDao().nachId("neu"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig) // und gesendet

        val b = geraet(name, konto)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals("Alt, geändert", b.db.zutatDao().nachId("alt")?.canonicalName)
        assertNotNull(b.db.zutatDao().nachId("neu"))
    }

    @Test
    fun festhaengenderCursorIstVoruebergehend() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.zugang.beiPull = { since, _ -> PullResponse(emptyList(), since, true) }
        assertEquals(SyncErgebnis.Fehlgeschlagen("cursor_haengt", voruebergehend = true), a.ausfuehren())
        assertEquals(1, a.zugang.pulls.size)
        assertEquals("cursor_haengt", a.speicher.zustand().letzterFehler)
    }

    @Test
    fun zuGrossePullSeiteHalbiertDasLimit() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        for (i in 1..3) a.db.zutatDao().upsert(zutat("z$i"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val b = geraet(name, konto)
        val echt = b.zugang.beiPull
        b.zugang.beiPull = { since, limit ->
            // z. B. ein Proxy mit kleinerem Antwort-Limit: erst ab 200 Datensätzen je Seite passt es
            if (limit > 200) throw ServerFehler.ZuGross()
            echt(since, limit)
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 3), b.ausfuehren())
        assertEquals(listOf(500, 250, 125), b.zugang.pulls.map { it.second })
        assertEquals(3, b.db.zutatDao().alle().size)

        // Schon das kleinste Limit ist zu groß: Fehler statt Endlosschleife, der Cursor bleibt.
        val cursor = b.speicher.zustand().cursor
        b.zugang.beiPull = { _, _ -> throw ServerFehler.ZuGross() }
        assertEquals(SyncErgebnis.Fehlgeschlagen("server_fehler", voruebergehend = false), b.ausfuehren())
        assertEquals(1, b.zugang.pulls.last().second)
        assertEquals(cursor, b.speicher.zustand().cursor)
    }

    @Test
    fun zuGrossesPaketWirdGeteilt() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        for (i in 1..5) a.db.zutatDao().upsert(zutat("z$i"))
        val echt = a.zugang.beiPush
        a.zugang.beiPush = { records ->
            if (records.size > 2 || records.any { it.id == "z3" }) throw ServerFehler.ZuGross()
            echt(records)
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 4, geholt = 4), a.ausfuehren())
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(listOf("z3" to "too_large"), a.dao.probleme().map { it.recordId to it.code })
        assertEquals(setOf("z1", "z2", "z4", "z5"), serverStand(a.zugang).keys.map { it.second }.toSet())
    }

    @Test
    fun fehlendesVerweiszielWirdNachgereicht() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        // Die Zutat kennt nur dieses Gerät, sie wurde nie vorgemerkt (z. B. Startzutat nach „nur herunterladen“).
        a.rezeptMitZutat("r1")
        a.dao.loeschenOutbox("ingredient", "salz")
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf("r1"), a.dao.outbox().map { it.recordId }, "abgelehnt, wartet auf sein Verweisziel")
        assertEquals(listOf(SyncSpeicher.FEHLENDER_VERWEIS), a.dao.probleme().map { it.code })
        assertTrue((RecordType.INGREDIENT to "salz") in serverStand(a.zugang), "Zutat nachgereicht")

        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertTrue((RecordType.RECIPE to "r1") in serverStand(a.zugang), "Rezept angekommen")
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(emptyList(), a.dao.probleme())
    }

    @Test
    fun abgelehntWirdProblem() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("a"))
        a.zugang.beiPush = { records ->
            PushResponse(records.map { PushResult(it.id, it.type, PushStatus.REJECTED, code = ErrorCode.INVALID_PAYLOAD) })
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 0), a.ausfuehren())
        assertEquals(listOf("a" to "invalid_payload"), a.dao.probleme().map { it.recordId to it.code })
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(1, a.zugang.pushes.size, "kein erneuter Versuch im selben Lauf")
        assertNotNull(a.db.zutatDao().nachId("a"), "die lokale Fassung bleibt")
    }

    @Test
    fun anmeldungAbgelaufenSetztKennung() = syncServerTest { server ->
        val (_, konto) = server.kontoAnlegen()
        val zugang = StoerZugang(KtorServerZugang(TEST_BASIS, client) { "widerrufen" })
        val a = Geraet(neueDb(), zugang)
        a.speicher.aktivieren(TEST_BASIS, konto, allesHochladen = false)
        a.db.zutatDao().upsert(zutat("a"))
        assertEquals(SyncErgebnis.Fehlgeschlagen("anmeldung_abgelaufen", voruebergehend = false), a.ausfuehren())
        assertEquals("anmeldung_abgelaufen", a.speicher.zustand().letzterFehler)
        assertEquals(listOf("a"), a.dao.outbox().map { it.recordId })
        assertTrue(zugang.pulls.isEmpty(), "nach dem 401 beim Push wird nichts mehr gefragt")

        // Kein Token (oder gerade nicht lesbar): dieselbe Kennung, ohne Anfrage.
        val ohneToken = SyncLauf(a.speicher) { null }
        assertEquals(SyncErgebnis.Fehlgeschlagen("anmeldung_abgelaufen", voruebergehend = false), ohneToken.ausfuehren())
        assertEquals(1, zugang.aufrufe.size)
    }

    @Test
    fun appZuAltSetztKennung() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val echt = a.zugang.beiPull
        a.zugang.beiPull = { _, _ -> throw ServerFehler.AppZuAlt() }
        assertEquals(SyncErgebnis.Fehlgeschlagen("app_zu_alt", voruebergehend = false), a.ausfuehren())
        assertEquals("app_zu_alt", a.speicher.zustand().letzterFehler)
        a.zugang.beiPull = { _, _ -> throw ServerFehler.ServerZuAlt() }
        assertEquals(SyncErgebnis.Fehlgeschlagen("server_zu_alt", voruebergehend = false), a.ausfuehren())
        a.zugang.beiPull = { _, _ -> throw ServerFehler.Server(503) }
        assertEquals(SyncErgebnis.Fehlgeschlagen("server_fehler", voruebergehend = true), a.ausfuehren())
        // Unerwartet: wiederholen (Backoff), ins Log nur Klasse und Stelle, nie die Meldung
        ShadowLog.clear()
        a.zugang.beiPull = { _, _ -> throw IllegalStateException("geheimer Inhalt") }
        assertEquals(SyncErgebnis.Fehlgeschlagen("server_fehler", voruebergehend = true), a.ausfuehren())
        assertEquals("server_fehler", a.speicher.zustand().letzterFehler)
        val log = ShadowLog.getLogsForTag("SyncLauf").single()
        assertTrue("java.lang.IllegalStateException" in log.msg, log.msg)
        assertFalse("geheimer Inhalt" in log.msg)
        assertNull(log.throwable)

        // Ein erfolgreicher Lauf setzt die Kennung zurück.
        a.zugang.beiPull = echt
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertNull(a.speicher.zustand().letzterFehler)
    }

    /**
     * Review-Fokus 1: Ein Handy mit v1 hat ungesendete Änderungen samt einer Löschung. Nach Migration 9 → 10 gehen sie
     * über Protokoll 3 hinaus und verschwinden aus der Outbox; der Vorrats-Eintrag ist mit der Migration weg.
     */
    @Test
    fun outboxAusVEinsWirdNachUpdateGesendet() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val zugang = anmelden(name)
        // Die v1 hatte „weg“ schon gesendet; danach wurde die Zutat lokal gelöscht.
        val weg = zugang.push(listOf(zutatRecord("weg", "Alt"))).results.single()
        assertEquals(PushStatus.ACCEPTED, weg.status)
        val cursor = zugang.pull(0, 10).nextCursor

        legeV9An(cursor = cursor, revWeg = weg.rev!!)
        val db = baueDatenbank(context, V9_DB).also { datenbanken += it }
        val g = Geraet(db, StoerZugang(zugang))
        assertEquals(
            listOf("ingredient:i1:false", "ingredient:weg:true", "recipe:r1:false"),
            g.dao.outbox().map { "${it.type}:${it.recordId}:${it.deleted}" }.sorted(),
        )

        assertEquals(SyncErgebnis.Fertig(gesendet = 3, geholt = 3), g.ausfuehren())
        assertEquals(listOf(cursor), g.zugang.pulls.map { it.first }.take(1), "kein Voll-Abgleich")
        assertEquals(emptyList(), g.dao.outbox())
        assertEquals(emptyList(), g.dao.probleme())
        val stand = serverStand(zugang)
        assertTrue(stand[RecordType.INGREDIENT to "weg"]!!.deleted, "Löschung angekommen")
        assertFalse(stand[RecordType.INGREDIENT to "i1"]!!.deleted)
        assertEquals("Pfannkuchen", stand[RecordType.RECIPE to "r1"]!!.payload!!["name"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("i1"),
            stand[RecordType.RECIPE to "r1"]!!.payload!!["lines"]!!.jsonArray.map { it.jsonObject["ingredientId"]!!.jsonPrimitive.content },
        )
        // Genau die drei Änderungen der v1 – der Vorrat ging mit der Migration nicht mit.
        assertEquals(
            setOf(RecordType.INGREDIENT to "weg", RecordType.INGREDIENT to "i1", RecordType.RECIPE to "r1"),
            stand.keys,
        )
    }

    /** Umstieg mit altem Token und Cursor > 0: kein Voll-Abgleich, nur Neues wird geholt. */
    @Test
    fun alterTokenUndCursorReichen() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("alt"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        val cursor = a.speicher.zustand().cursor

        // Gerät B hatte schon synchronisiert (Token, Cursor, Daten); eine lokale Zutat ohne Gegenstück beim Server bleibt.
        val b = geraet(name, konto)
        b.dao.upsertZustand(b.dao.zustand()!!.copy(cursor = cursor))
        b.db.zutatDao().upsert(zutat("alt"))
        b.db.zutatDao().upsert(zutat("nurLokal"))
        b.dao.outboxLeeren()
        a.db.zutatDao().upsert(zutat("neu"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 1), b.ausfuehren())
        assertEquals(listOf(cursor), b.zugang.pulls.map { it.first })
        assertNotNull(b.db.zutatDao().nachId("neu"))
        assertNotNull(b.db.zutatDao().nachId("nurLokal"), "kein Voll-Abgleich")
        assertEquals(emptyList(), b.dao.outbox())
    }

    @Test
    fun mehrereSeitenWerdenErstNachDerLetztenAngewendet() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        for (i in 1..3) a.db.zutatDao().upsert(zutat("z$i"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val b = geraet(name, konto)
        val echt = b.zugang.beiPull
        val cursorGesehen = ArrayList<Long>()
        val zutatenGesehen = ArrayList<Int>()
        b.zugang.beiPull = { since, _ ->
            cursorGesehen += b.speicher.zustand().cursor
            zutatenGesehen += b.db.zutatDao().alle().size
            echt(since, 1) // eine Seite je Datensatz
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 3), b.ausfuehren())
        assertEquals(3, b.zugang.pulls.size)
        // Vor dem Ende der letzten Seite ist weder etwas angewendet noch der Cursor bewegt.
        assertEquals(listOf(0L, 0L, 0L), cursorGesehen)
        assertEquals(listOf(0, 0, 0), zutatenGesehen)
        assertEquals(3, b.db.zutatDao().alle().size)
        assertEquals(a.speicher.zustand().cursor, b.speicher.zustand().cursor)
    }

    /** Gerät A lädt Rezepte mit Fotos hoch; liefert Gerät B (noch ohne Lauf) und die Hashes je Rezept. */
    private suspend fun ApplicationTestBuilder.fotosBeimServer(server: TestServer, anzahl: Int): Pair<Geraet, List<String>> {
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val hashes = (1..anzahl).map { i ->
            val bytes = jpeg(20 + i)
            a.rezeptMitZutat("r$i", bild = eigeneDatei("a$i.jpg", bytes))
            PhotoHash.of(bytes)
        }
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        return geraet(name, konto) to hashes
    }

    @Test
    fun voruebergehendeStoerungBeimFotoLadenHaeltDieUebrigenNichtAuf() = syncServerTest { server ->
        val (b, hashes) = fotosBeimServer(server, 2)
        val echt = b.zugang.beiFotoLaden
        b.zugang.beiFotoLaden = { sha -> if (sha == hashes[0]) throw ServerFehler.Offline() else echt(sha) }
        // Wünsche laufen nach Rezept-ID: der gestörte kommt zuerst.
        assertEquals(SyncErgebnis.Fehlgeschlagen("offline", voruebergehend = true), b.ausfuehren())
        assertNull(b.db.rezeptDao().nachId("r1")!!.imageUri)
        assertNotNull(b.db.rezeptDao().nachId("r2")!!.imageUri, "zweiter Wunsch wurde trotzdem geladen")
        assertEquals(listOf("r1"), b.dao.fotosGewuenscht().map { it.recipeId })
        assertEquals("offline", b.speicher.zustand().letzterFehler)
        assertEquals(1, b.zugang.pulls.size, "der Pull lief vorher durch")
    }

    @Test
    fun zuGrossesOderAbgelehntesFotoWirdUebersprungen() = syncServerTest { server ->
        val (b, hashes) = fotosBeimServer(server, 3)
        val echt = b.zugang.beiFotoLaden
        b.zugang.beiFotoLaden = { sha ->
            when (sha) {
                hashes[0] -> throw ServerFehler.ZuGross() // z. B. falsche Länge eines Proxys
                hashes[1] -> throw ServerFehler.Abgelehnt(400, ErrorCode.INVALID_INPUT)
                else -> echt(sha)
            }
        }
        assertEquals(SyncErgebnis.Fertig(gesendet = 0, geholt = 4), b.ausfuehren())
        assertNull(b.speicher.zustand().letzterFehler)
        assertNotNull(b.db.rezeptDao().nachId("r3")!!.imageUri)
        assertEquals(listOf("r1", "r2"), b.dao.fotosGewuenscht().map { it.recipeId }, "die Wünsche bleiben")
    }

    @Test
    fun lokalerFehlerBeimAblegenIstVoruebergehend() = syncServerTest { server ->
        val (b, hashes) = fotosBeimServer(server, 2)
        val echt = b.zugang.beiFotoLaden
        b.zugang.beiFotoLaden = { sha ->
            // Erstes Foto: an Stelle des Fotoordners liegt eine Datei, das Ablegen scheitert. Zweites: Ordner wieder da.
            ordner.deleteRecursively()
            if (sha == hashes[0]) ordner.writeText("kein Ordner") else ordner.mkdirs()
            echt(sha)
        }
        assertEquals(SyncErgebnis.Fehlgeschlagen("server_fehler", voruebergehend = true), b.ausfuehren())
        assertNull(b.db.rezeptDao().nachId("r1")!!.imageUri)
        assertNotNull(b.db.rezeptDao().nachId("r2")!!.imageUri, "zweiter Wunsch wurde trotzdem abgelegt")
        assertEquals(listOf("r1"), b.dao.fotosGewuenscht().map { it.recipeId })
        assertEquals("server_fehler", b.speicher.zustand().letzterFehler)
    }

    /**
     * Zusammengeführt in einen anderen Datensatz: Beide Geräte legten „Salz“ an. Der Server führt Bs Zutat in die von A
     * zusammen; B übernimmt die Server-Zutat, hängt sein Rezept um und sendet es im selben Lauf erneut.
     */
    @Test
    fun zusammengefuehrtInAndereIdWirdUebernommen() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("salz-a", "Salz"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)

        val b = geraet(name, konto)
        b.db.zutatDao().upsert(zutat("salz-b", "Salz"))
        b.rezeptMitZutat("r1", zutatId = "salz-b")
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(listOf("salz-a"), b.db.zutatDao().alle().map { it.id })
        assertEquals(listOf("salz-a"), b.db.rezeptZutatDao().nachRezept("r1").map { it.ingredientId })
        assertNull(b.dao.revision("ingredient", "salz-b"), "die Revision gehört nicht zur lokalen ID")
        assertEquals(emptyList(), b.dao.outbox())
        assertEquals(emptyList(), b.dao.probleme())
        val stand = serverStand(b.zugang)
        assertEquals(setOf(RecordType.INGREDIENT to "salz-a", RecordType.RECIPE to "r1"), stand.keys)
        assertEquals(
            "salz-a",
            stand[RecordType.RECIPE to "r1"]!!.payload!!["lines"]!!.jsonArray.single().jsonObject["ingredientId"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun geaenderterInhaltBeimUploadVerwirftDenHash() = syncServerTest { server ->
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        val link = eigeneDatei("eigen.jpg", jpeg(30))
        a.rezeptMitZutat("r1", bild = link)
        val datei = a.ablage.datei(link)!!
        val neu = jpeg(31) // gleiche Größe
        val echt = a.zugang.beiFotosFehlend
        // Inhalt ändert sich zwischen Paketbau und Upload; Größe und Änderungszeit bleiben gleich.
        a.zugang.beiFotosFehlend = { hashes ->
            val zeit = datei.lastModified()
            datei.writeBytes(neu)
            datei.setLastModified(zeit)
            echt(hashes)
        }
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertTrue(a.dao.outbox().any { it.recordId == "r1" }, "bleibt in der Outbox")
        assertNull(a.dao.fotoLokal(link), "gemerkter Hash verworfen")

        a.zugang.beiFotosFehlend = echt
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals(emptyList(), a.dao.outbox())
        assertEquals(PhotoHash.of(neu), fotoVon(serverStand(a.zugang)[RecordType.RECIPE to "r1"]))
        assertTrue(server.deps.photos.exists(konto, PhotoHash.of(neu)))
    }

    @Test
    fun fehlerBeimBauenDesZugangsIstAnmeldungAbgelaufen() = syncServerTest { server ->
        val (_, konto) = server.kontoAnlegen()
        val db = neueDb()
        val ablage = FotoAblage(context)
        val speicher = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        speicher.aktivieren(TEST_BASIS, konto, allesHochladen = false)
        val kaputt = SyncLauf(speicher) { throw IllegalStateException("Keystore") }
        assertEquals(SyncErgebnis.Fehlgeschlagen("anmeldung_abgelaufen", voruebergehend = false), kaputt.ausfuehren())
        assertEquals("anmeldung_abgelaufen", speicher.zustand().letzterFehler)

        val abgebrochen = SyncLauf(speicher) { throw CancellationException("abgebrochen") }
        assertFailsWith<CancellationException> { abgebrochen.ausfuehren() }
        assertFalse(speicher.laufSperre.isLocked)
    }

    // --- v9-Datenbank wie auf dem Handy mit v1 ---

    private fun zutatRecord(id: String, name: String) = SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 1,
        payload = Protocol.json.encodeToJsonElement(
            de.foody.api.protocol.IngredientPayload.serializer(),
            de.foody.api.protocol.IngredientPayload(name = name),
        ) as JsonObject,
    )

    /**
     * Legt `foody.db` in Version 9 an – Tabellen und Indizes aus dem exportierten Schema 9 (`core/data/schemas`), Daten
     * und Sync-Zustand wie auf dem Handy: Sync aktiv mit [cursor], Trigger der v1 ([v9Trigger]), Outbox mit Rezept und
     * Löschung sowie – von den v1-Triggern vorgemerkt – Zutat und Vorrat.
     */
    private fun legeV9An(cursor: Long, revWeg: Long) {
        val schema = File(SCHEMA_9).readText()
        val datenbank = Protocol.json.parseToJsonElement(schema).jsonObject["database"]!!.jsonObject
        val datei = context.getDatabasePath(V9_DB).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(datei, null).use { db ->
            for (e in datenbank["entities"]!!.jsonArray.map { it.jsonObject }) {
                val tabelle = e["tableName"]!!.jsonPrimitive.content
                db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tabelle))
                for (i in (e["indices"] as? JsonArray).orEmpty()) {
                    db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tabelle))
                }
            }
            for (q in datenbank["setupQueries"]!!.jsonArray) db.execSQL(q.jsonPrimitive.content)
            listOf(
                "INSERT INTO ingredient (id, canonicalName, createdAt, updatedAt, version) VALUES ('i1', 'Mehl', 1, 1, 1)",
                "INSERT INTO recipe (id, name, defaultServings, tags, createdAt, updatedAt, version, favorite) " +
                    "VALUES ('r1', 'Pfannkuchen', 4, '', 1, 1, 1, 0)",
                "INSERT INTO recipe_ingredient (id, recipeId, ingredientId, amount, unit, sortOrder, optional) " +
                    "VALUES ('ri1', 'r1', 'i1', '250', 'GRAM', 0, 0)",
                "INSERT INTO pantry_item (id, ingredientId, amount, unit, bestBeforeDate, updatedAt) VALUES ('p1', 'i1', '1000', 'GRAM', NULL, 1)",
                "INSERT INTO sync_state (id, active, applyingRemote, serverUrl, householdId, cursor, lastSyncAt, lastError) " +
                    "VALUES (1, 1, 0, '$TEST_BASIS', 'haushalt-1', $cursor, 1700000000000, NULL)",
                "INSERT INTO sync_record_rev (type, recordId, rev) VALUES ('ingredient', 'weg', $revWeg)",
                "INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('recipe', 'r1', 0, 1000)",
                "INSERT INTO sync_outbox (type, recordId, deleted, queuedAt) VALUES ('ingredient', 'weg', 1, 1002)",
            ).forEach(db::execSQL)
            v9Trigger().forEach(db::execSQL)
            // Letzte Änderungen in der v1, vorgemerkt von ihren Triggern (Zutat und Vorrat).
            db.execSQL("UPDATE ingredient SET category = 'Backen' WHERE id = 'i1'")
            db.execSQL("UPDATE pantry_item SET amount = '900' WHERE id = 'p1'")
            db.version = 9
        }
    }

    /**
     * Trigger einer v9-Datenbank wie in `MigrationTest` (`:core:data`, androidTest): dieselben Namen wie Version 10,
     * dazu die Vorrats-Trigger und ein veralteter Name; jeder trägt die Markierung `v9alt`.
     */
    private fun v9Trigger(): List<String> {
        val markiert = SyncTrigger.anweisungen.map { it.replace(" BEGIN\n", " BEGIN\nSELECT 'v9alt';\n") }
        val vorrat = markiert.filter { Regex("""EXISTS sync_ingredient_a[iud] """).containsMatchIn(it) }
            .map { it.replace("ingredient", "pantry_item") }
        check(vorrat.size == 3)
        val veraltet = "CREATE TRIGGER sync_recipe_veraltet AFTER UPDATE ON recipe BEGIN\nSELECT 'v9alt';\nEND"
        return markiert + vorrat + veraltet
    }

    // --- Zutatenkatalog: Zusammenführen kommt auf dem zweiten Gerät an (Phase 6e2) ---

    /** A und B mit den Zutaten „Zwiebeln“ (`z-alt`) und „Zwiebel“ (`z-neu`) und Rezept r1 auf `z-alt`. */
    private suspend fun ApplicationTestBuilder.zweiGeraeteMitDublette(server: TestServer): Pair<Geraet, Geraet> {
        val (name, konto) = server.kontoAnlegen()
        val a = geraet(name, konto)
        a.db.zutatDao().upsert(zutat("z-alt", "Zwiebeln"))
        a.db.zutatDao().upsert(zutat("z-neu", "Zwiebel"))
        a.rezeptMitZutat("r1", "z-alt")
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        val b = geraet(name, konto)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)
        assertEquals("z-alt", b.db.rezeptZutatDao().nachRezept("r1").single().ingredientId)
        return a to b
    }

    @Test
    fun zusammenfuehrenKommtAufZweitemGeraetAn() = syncServerTest { server ->
        val (a, b) = zweiGeraeteMitDublette(server)
        assertEquals(ZutatErgebnis.Zusammengefuehrt("z-neu", "Zwiebel"), ZutatenRepository(a.db) { 50L }.zusammenfuehren("z-alt", "z-neu"))
        assertTrue(a.ausfuehren() is SyncErgebnis.Fertig)
        assertTrue(b.ausfuehren() is SyncErgebnis.Fertig)

        assertEquals("z-neu", b.db.rezeptZutatDao().nachRezept("r1").single().ingredientId)
        assertNull(b.db.zutatDao().nachId("z-alt"))
        assertEquals(emptyList(), a.dao.outbox() + b.dao.outbox())
        assertEquals(emptyList(), a.dao.probleme() + b.dao.probleme())
    }

    @Test
    fun zusammenfuehrenMitOffenerAenderungAufB() = syncServerTest { server ->
        val (a, b) = zweiGeraeteMitDublette(server)
        // B ändert r1, bevor die Zusammenführung ankommt – die Änderung ist noch nicht gesendet
        b.db.rezeptDao().upsert(b.db.rezeptDao().nachId("r1")!!.copy(name = "Rezept r1 B", updatedAt = 7))
        ZutatenRepository(a.db) { 50L }.zusammenfuehren("z-alt", "z-neu")
        a.ausfuehren()
        b.ausfuehren()
        assertNotNull(b.db.zutatDao().nachId("z-alt"), "B braucht die Quelle noch: nicht gelöscht")
        a.ausfuehren()

        for (g in listOf(a, b)) {
            val zeilen = g.db.rezeptZutatDao().nachRezept("r1")
            assertTrue(zeilen.isNotEmpty())
            for (z in zeilen) assertNotNull(g.db.zutatDao().nachId(z.ingredientId), "Zeile zeigt auf vorhandene Zutat")
        }
    }

    private companion object {
        const val V9_DB = "foody-v9-test.db"

        /** Exportiertes Schema 9 aus `:core:data` (Arbeitsverzeichnis der Tests ist das Modul `core/sync`). */
        const val SCHEMA_9 = "../data/schemas/de.foody.core.data.db.FoodyDatenbank/9.json"
    }
}
