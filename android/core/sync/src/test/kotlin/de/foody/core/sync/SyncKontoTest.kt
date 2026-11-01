package de.foody.core.sync

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecordType
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.sync.SyncSpeicher
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schlüsselablage im Speicher (unter Robolectric gibt es keinen `AndroidKeyStore`); [fehler] simuliert eine Störung. */
internal class SpeicherSchluessel : Schluesselablage {
    var schluessel: SecretKey? = null
    var fehler: Exception? = null

    override fun holen(): SecretKey? {
        fehler?.let { throw it }
        return schluessel
    }

    override fun erzeugen(): SecretKey {
        fehler?.let { throw it }
        return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { schluessel = it }
    }

    override fun loeschen() {
        schluessel = null
    }
}

/**
 * Verbinden, Aktivieren („Zusammenführen“/„Ersetzen“) und Trennen gegen den echten Sync-Server (Protokoll 3), mit Room
 * unter Robolectric und dem WorkManager im Testmodus (Aufträge werden nur eingeplant, nicht ausgeführt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SyncKontoTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)
    private val datenbanken = ArrayList<FoodyDatenbank>()
    private lateinit var bereich: CoroutineScope

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        bereich = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ordner.deleteRecursively()
        context.getSharedPreferences(TokenSpeicher.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        bereich.cancel()
        datenbanken.forEach { it.close() }
        ordner.deleteRecursively()
    }

    private fun neueDb(): FoodyDatenbank = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .build()
        .also { datenbanken += it }

    /** Ein Gerät mit allem, was das Konto braucht; [client] spricht mit dem Server. */
    private inner class Geraet(client: HttpClient, httpErlaubt: Boolean = true) {
        val db = neueDb()
        val ablage = FotoAblage(context)
        val speicher = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        val schluessel = SpeicherSchluessel()
        val token = TokenSpeicher(context, schluessel)

        /** (Adresse, Token) jeder Anfrage, die ein Token mitschickt. */
        val gesendet = ArrayList<Pair<String, String?>>()
        val fabrik = ZugangFabrik { adresse ->
            KtorServerZugang(adresse, client) { token.laden().also { synchronized(gesendet) { gesendet += adresse to it } } }
        }
        val ausloeser = SyncAusloeser(context, speicher, db, bereich).also { it.appOffen = true }
        val konto = SyncKonto(db, speicher, ablage, token, fabrik, ausloeser, SyncKonfiguration(lokalesHttpErlaubt = httpErlaubt))
        val lauf = SyncLauf(speicher, zugangFuerLauf(speicher, token, fabrik))
        val dao get() = db.syncDao()

        suspend fun rezeptMitZutat(id: String, zutatId: String, bild: String? = null) {
            if (db.zutatDao().nachId(zutatId) == null) {
                db.zutatDao().upsert(ZutatEntity(id = zutatId, canonicalName = "Zutat $zutatId", createdAt = 1, updatedAt = 1))
            }
            db.rezeptDao().upsert(rezept(id, bild))
            db.rezeptZutatDao().upsert(
                RezeptZutatEntity(
                    id = "$id-l", recipeId = id, ingredientId = zutatId, amount = BigDecimal.ONE, unit = MeasureUnit.GRAM,
                    sortOrder = 0, preparationNote = null, optional = false,
                ),
            )
        }
    }

    private fun rezept(id: String, bild: String? = null) = RezeptEntity(
        id = id, name = "Rezept $id", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = bild,
        notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
    )

    private fun eigeneDatei(name: String): String {
        ordner.mkdirs()
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        return Uri.fromFile(File(ordner, name).apply { writeBytes(bytes) }).toString()
    }

    /** Alles, was der Server für das Konto [name] hat (eigene Anmeldung als zweites Gerät). */
    private suspend fun serverStand(client: HttpClient, name: String): Set<Pair<RecordType, String>> {
        var t: String? = null
        val zugang = KtorServerZugang(TEST_BASIS, client) { t }
        t = zugang.anmelden(LoginRequest(name, TEST_PASSWORT, "Prüfgerät")).token
        return zugang.pull(0, Protocol.MAX_PULL_LIMIT).records.filter { !it.deleted }.map { it.type to it.id }.toSet()
    }

    private fun auftraege(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncAusloeser.AUFTRAG).get()

    @Test
    fun anmeldenUndZusammenfuehrenLaedtLokalesHoch() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        val g = Geraet(client)
        g.rezeptMitZutat("r1", "salz")
        assertTrue(g.konto.hatLokaleDaten())

        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden("$TEST_BASIS/", name, TEST_PASSWORT, "Testgerät"))
        assertNull(g.token.laden(), "Token erst beim Aktivieren gespeichert")
        assertFalse(g.speicher.zustand().aktiv, "Anmelden allein schaltet den Sync nicht ein")

        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN))
        assertNotNull(g.token.laden(), "Token gespeichert")
        val z = g.speicher.zustand()
        assertTrue(z.aktiv)
        assertEquals(TEST_BASIS, z.serverUrl)
        assertEquals(kontoId, z.kontoId)
        assertTrue(g.dao.outbox().isNotEmpty(), "lokale Daten zum Senden vorgemerkt")
        assertEquals(1, auftraege().count { it.state == WorkInfo.State.ENQUEUED }, "erster Lauf eingeplant")

        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        assertTrue(g.dao.outbox().isEmpty())
        val stand = serverStand(client, name)
        assertTrue(RecordType.RECIPE to "r1" in stand)
        assertTrue(RecordType.INGREDIENT to "salz" in stand)
    }

    @Test
    fun ersetzenVerwirftLokales() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        // Gerät B bringt die Daten des Servers mit.
        val dbB = neueDb()
        val ablageB = FotoAblage(context)
        val speicherB = SyncSpeicher(dbB, ablageB, FotoIndex(dbB.syncDao(), ablageB))
        var tokenB: String? = null
        val zugangB = KtorServerZugang(TEST_BASIS, client) { tokenB }
        tokenB = zugangB.anmelden(LoginRequest(name, TEST_PASSWORT, "B")).token
        speicherB.aktivieren(TEST_BASIS, kontoId, allesHochladen = false)
        dbB.zutatDao().upsert(ZutatEntity(id = "mehl", canonicalName = "Mehl", createdAt = 1, updatedAt = 1))
        dbB.rezeptDao().upsert(rezept("s1"))
        assertIs<SyncErgebnis.Fertig>(SyncLauf(speicherB) { zugangB }.ausfuehren())

        // Gerät A hat eigene Daten, die verworfen werden sollen.
        val a = Geraet(client)
        val foto = eigeneDatei("a.jpg")
        a.rezeptMitZutat("r1", "pfeffer", foto)
        a.db.planEintragDao().upsert(
            PlanEintragEntity("p1", LocalDate.of(2026, 10, 18), Mahlzeit.ABENDESSEN.name, "r1", servings = 2, createdAt = 1, updatedAt = 1),
        )
        a.db.einkaufslisteDao().upsert(EinkaufslisteEntity("l1", "Woche", createdAt = 1, updatedAt = 1))
        assertTrue(a.konto.hatLokaleDaten())

        assertEquals(Ergebnis.Erfolg(Unit), a.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "A"))
        a.konto.aktivieren(ErsterSync.ERSETZEN)

        assertFalse(a.konto.hatLokaleDaten(), "Rezepte, Plan und Listen verworfen")
        assertFalse(File(Uri.parse(foto).path!!).exists(), "eigenes Foto des verworfenen Rezepts entfernt")
        assertNotNull(a.db.zutatDao().nachId("pfeffer"), "Zutaten (Startzutaten) bleiben")
        assertTrue(a.dao.outbox().isEmpty(), "nichts zum Senden vorgemerkt")
        assertTrue(a.speicher.zustand().aktiv)

        assertIs<SyncErgebnis.Fertig>(a.lauf.ausfuehren())
        assertNotNull(a.db.rezeptDao().nachId("s1"), "Server-Daten geholt")
        assertNull(a.db.rezeptDao().nachId("r1"))
        assertTrue(a.dao.outbox().isEmpty())
        val stand = serverStand(client, name)
        assertFalse(RecordType.RECIPE to "r1" in stand, "Verworfenes geht nie zum Server")
        assertFalse(RecordType.INGREDIENT to "pfeffer" in stand)
        assertTrue(RecordType.RECIPE to "s1" in stand)
    }

    @Test
    fun trennenMeldetAbUndLeert() = syncServerTest { server ->
        val (name, _) = server.kontoAnlegen()
        val g = Geraet(client)
        g.rezeptMitZutat("r1", "salz")
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Testgerät"))
        g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN)
        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        g.rezeptMitZutat("r2", "salz")
        assertTrue(g.dao.outbox().isNotEmpty())
        val altesToken = assertNotNull(g.token.laden())

        g.konto.trennen()

        assertNull(g.token.laden(), "Token lokal gelöscht")
        assertTrue(g.dao.outbox().isEmpty())
        val z = g.speicher.zustand()
        assertFalse(z.aktiv)
        assertNull(z.serverUrl)
        assertTrue(g.konto.hatLokaleDaten(), "lokale Daten bleiben")
        assertTrue(auftraege().none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })
        // Auf dem Server widerrufen: das alte Token gilt nicht mehr.
        val alt = KtorServerZugang(TEST_BASIS, client) { altesToken }
        assertFailsWith<ServerFehler.AnmeldungAbgelaufen> { alt.pull(0, 10) }
        // Ohne Anmeldung lässt sich nicht erneut aktivieren.
        assertFailsWith<IllegalStateException> { g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN) }
    }

    @Test
    fun trennenOhneNetzLeertTrotzdem() = runBlocking {
        val g = Geraet(standardHttpClient()) // auf Port 1 hört niemand
        g.token.speichern("altes-token")
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.rezeptMitZutat("r1", "salz")
        assertTrue(g.dao.outbox().isNotEmpty())

        g.konto.trennen()

        assertNull(g.token.laden())
        assertTrue(g.dao.outbox().isEmpty())
        assertFalse(g.speicher.zustand().aktiv)
        assertTrue(g.konto.hatLokaleDaten())
    }

    @Test
    fun fehlgeschlageneAnmeldungSpeichertNichts() = syncServerTest { server ->
        val (name, _) = server.kontoAnlegen()
        val g = Geraet(client)
        assertEquals(Ergebnis.Fehler(SyncKonto.FALSCHE_ZUGANGSDATEN), g.konto.anmelden(TEST_BASIS, name, "falsch-falsch", "Test"))
        assertEquals(Ergebnis.Fehler(SyncKonto.UNGUELTIGE_ADRESSE), g.konto.anmelden("ftp://localhost", name, TEST_PASSWORT, "Test"))
        assertNull(g.token.laden())
        assertFalse(g.speicher.zustand().aktiv)
        assertFailsWith<IllegalStateException> { g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN) }

        // Release-Build: http nur in Debug-Builds.
        val release = Geraet(client, httpErlaubt = false)
        assertEquals(Ergebnis.Fehler(SyncKonto.UNGUELTIGE_ADRESSE), release.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Test"))
    }

    @Test
    fun tokenNichtSpeicherbarWirdGemeldet() = syncServerTest { server ->
        val (name, _) = server.kontoAnlegen()
        val g = Geraet(client)
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Test"))
        g.schluessel.fehler = java.security.KeyStoreException("Keystore gestört")
        assertEquals(Ergebnis.Fehler(SyncKonto.TOKEN_NICHT_GESPEICHERT), g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN))
        g.schluessel.fehler = null
        assertNull(g.token.laden())
        assertFalse(g.speicher.zustand().aktiv, "nichts eingeschaltet")
        // Die Anmeldung bleibt bis zum Erfolg erhalten: erneut versuchen geht ohne neue Anmeldung.
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN))
        assertNotNull(g.token.laden())
        assertTrue(g.speicher.zustand().aktiv)
    }

    /** Verbindet [g] mit dem Konto [name] (Zusammenführen) und gleicht einmal ab; liefert das gespeicherte Token. */
    private suspend fun verbinden(g: Geraet, name: String): String {
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Testgerät"))
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN))
        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        return assertNotNull(g.token.laden())
    }

    @Test
    fun istWiederanmeldungNurBeiGleichemKontoUndAdresse() = syncServerTest { server ->
        val (nameA, _) = server.kontoAnlegen()
        val (nameB, _) = server.kontoAnlegen()
        val g = Geraet(client)
        assertFalse(g.konto.istWiederanmeldung(), "ohne Anmeldung")
        verbinden(g, nameA)
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, nameA, TEST_PASSWORT, "Testgerät"))
        assertTrue(g.konto.istWiederanmeldung(), "gleiches Konto, gleiche Adresse")
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, nameB, TEST_PASSWORT, "Testgerät"))
        assertFalse(g.konto.istWiederanmeldung(), "anderes Konto")
    }

    @Test
    fun anmeldungAnAnderemKontoVermischtNichts() = syncServerTest { server ->
        val (nameA, kontoA) = server.kontoAnlegen()
        val (nameB, kontoB) = server.kontoAnlegen()
        val g = Geraet(client)
        g.rezeptMitZutat("r1", "salz")
        val tokenA = verbinden(g, nameA)

        // Zweiter Verbinden-Dialog: Anmeldung an Konto B, noch nicht aktiviert.
        g.rezeptMitZutat("r2", "salz")
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, nameB, TEST_PASSWORT, "Testgerät"))
        assertEquals(tokenA, g.token.laden(), "Token von A bleibt")
        g.gesendet.clear()
        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        assertTrue(g.gesendet.isNotEmpty())
        assertTrue(g.gesendet.all { it.second == tokenA }, "ein Lauf schickt nur das Token von A")
        assertTrue(RecordType.RECIPE to "r2" in serverStand(client, nameA))
        assertTrue(serverStand(client, nameB).isEmpty(), "nichts landet bei B")

        // Erst das Aktivieren wechselt: danach geht nur noch das Token von B hinaus, ans Konto B.
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ZUSAMMENFUEHREN))
        val tokenB = assertNotNull(g.token.laden())
        assertNotEquals(tokenA, tokenB)
        assertEquals(kontoB, g.speicher.zustand().kontoId)
        assertNotEquals(kontoA, kontoB)
        g.gesendet.clear()
        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        assertTrue(g.gesendet.isNotEmpty())
        assertTrue(g.gesendet.all { it.second == tokenB }, "das alte Konto bekommt das neue Token nie")
        assertTrue(RecordType.RECIPE to "r1" in serverStand(client, nameB))
    }

    @Test
    fun abgebrochenesAnmeldenLaesstAltesTokenUnveraendert() = syncServerTest { server ->
        val (nameA, kontoA) = server.kontoAnlegen()
        val (nameB, _) = server.kontoAnlegen()
        val g = Geraet(client)
        g.rezeptMitZutat("r1", "salz")
        val tokenA = verbinden(g, nameA)
        val vorher = g.speicher.zustand()

        // Anmeldung an B, dann bricht der Nutzer den Dialog ab (kein aktivieren).
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, nameB, TEST_PASSWORT, "Testgerät"))

        assertEquals(tokenA, g.token.laden())
        assertEquals(vorher, g.speicher.zustand())
        assertEquals(kontoA, vorher.kontoId)
        // Trennen meldet das gespeicherte Token (A) ab, nicht die verworfene Anmeldung.
        g.konto.trennen()
        val alt = KtorServerZugang(TEST_BASIS, client) { tokenA }
        assertFailsWith<ServerFehler.AnmeldungAbgelaufen> { alt.pull(0, 10) }
    }

    @Test
    fun wiederanmeldungUebernimmtTokenCursorBleibt() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        val g = Geraet(client)
        g.rezeptMitZutat("r1", "salz")
        val altesToken = verbinden(g, name)
        // Token auf dem Server widerrufen: „Anmeldung abgelaufen“.
        KtorServerZugang(TEST_BASIS, client) { altesToken }.abmelden()
        assertEquals(
            SyncErgebnis.Fehlgeschlagen(ServerFehler.ANMELDUNG_ABGELAUFEN, voruebergehend = false),
            g.lauf.ausfuehren(),
        )
        g.rezeptMitZutat("r2", "salz")
        val vorher = g.speicher.zustand()
        assertTrue(vorher.cursor > 0)
        val outboxVorher = g.dao.outbox()

        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Testgerät"))
        assertEquals(altesToken, g.token.laden(), "vor dem Aktivieren unverändert")
        // Auch „Ersetzen“ verwirft bei einer Wiederanmeldung nichts.
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ERSETZEN))

        val neuesToken = assertNotNull(g.token.laden())
        assertNotEquals(altesToken, neuesToken)
        val z = g.speicher.zustand()
        assertEquals(vorher.cursor, z.cursor, "Cursor bleibt")
        assertEquals(kontoId, z.kontoId)
        assertTrue(z.aktiv)
        assertNull(z.letzterFehler)
        assertEquals(outboxVorher, g.dao.outbox(), "Outbox unverändert, nichts neu vorgemerkt")
        assertTrue(g.konto.hatLokaleDaten())
        assertIs<SyncErgebnis.Fertig>(g.lauf.ausfuehren())
        assertTrue(RecordType.RECIPE to "r2" in serverStand(client, name))
    }

    @Test
    fun wiederanmeldungNachDemUpdateBehaeltOutbox() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        val g = Geraet(client)
        // Zustand nach Migration 9 → 10: aktiv, Adresse aus der v1 (mit „/“), keine Konto-ID, Cursor, ungesendete Outbox.
        g.dao.upsertZustand(
            SyncZustandEntity(active = true, serverUrl = "$TEST_BASIS/", kontoId = null, cursor = 5, lastError = "anmeldung_abgelaufen"),
        )
        g.rezeptMitZutat("r1", "salz")
        val outboxVorher = g.dao.outbox()
        assertTrue(outboxVorher.isNotEmpty())

        assertEquals(Ergebnis.Erfolg(Unit), g.konto.anmelden(TEST_BASIS, name, TEST_PASSWORT, "Testgerät"))
        assertEquals(Ergebnis.Erfolg(Unit), g.konto.aktivieren(ErsterSync.ERSETZEN))

        assertEquals(outboxVorher, g.dao.outbox(), "ungesendete Outbox der v1 bleibt")
        assertNotNull(g.db.rezeptDao().nachId("r1"), "„Ersetzen“ wirkt bei einer Wiederanmeldung nicht")
        val z = g.speicher.zustand()
        assertTrue(z.aktiv)
        assertEquals(kontoId, z.kontoId)
        assertEquals(0, z.cursor, "ohne Konto-ID einmal alles neu holen")
        assertNull(z.letzterFehler)
        assertNotNull(g.token.laden(), "neues Token gespeichert")
    }
}
