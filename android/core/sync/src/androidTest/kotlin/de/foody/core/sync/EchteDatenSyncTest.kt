package de.foody.core.sync

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecordType
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.baueDatenbank
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.sync.SyncSpeicher
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sync-Lauf mit einer echten, nach Version 10 migrierten v1-Datenbank gegen den Test-Server. Läuft nur mit
 * Instrumentation-Argumenten; ohne `echteDb` kehrt er sofort zurück (gilt dann als bestanden). Ist `echteDb` gesetzt,
 * müssen auch die Server-Argumente da sein, sonst scheitert er:
 * - `echteDb`: absoluter Pfad der v1-`foody.db` (Version 9) auf dem Testgerät, für die Test-App lesbar (z. B. unter
 *   `/sdcard/Android/data/de.foody.core.sync.test/files/`); danebenliegende `-wal`/`-shm` werden mitkopiert.
 * - `testServer`: Adresse des Test-Servers, auf dem Emulator `http://10.0.2.2:8081` (SSH-Tunnel). Die in `sync_state`
 *   gespeicherte Adresse ist die echte und wird **nie** benutzt.
 * - `testBenutzer`, `testPasswort`: Zugang zum Test-Server. Das Token des Handys liegt im Hardware-Keystore und ist nicht
 *   kopierbar; der Test meldet sich deshalb neu an, behält aber Sync-Zustand und Cursor der echten Datenbank (kein
 *   `aktivieren`, das den Cursor auf 0 setzen würde). Das Token bleibt nur im Speicher, wird nie geloggt und am Ende
 *   beim Server widerrufen.
 * - `echteFotos` (optional): Ordner mit einer Kopie von `recipe_images` des Handys. Ohne ihn zeigen die Foto-Links der
 *   echten Datenbank in den Ordner der Handy-App, gelten hier als fremd, und vorgemerkte Rezepte gingen ohne Foto
 *   hinaus (auf dem Test-Server verlöre das Rezept dann sein Foto). Mit ihm werden die Dateien in den Fotoordner der
 *   Test-App kopiert und die Links passender Dateien umgeschrieben (ohne Outbox-Eintrag).
 *
 * Erwartung: Lauf `Fertig` (weitere Läufe, solange die Outbox mehr als die Pakete eines Laufs hält), danach Outbox leer,
 * keine `sync_problem`-Zeilen, kein `lastError`, und die lebenden Datensätze (Typ + ID) lokal = Server
 * (`GET /sync/pull?since=0`, alle Seiten, ohne Löschungen), keine doppelten IDs. Fehlermeldungen nennen nur Typen, IDs
 * und Anzahlen, nie Inhalte. Ein echter Lauf schreibt `geprüft: …` ins Log (Tag `EchteDaten`).
 *
 * Arbeitskopie und Fotos liegen im Speicher der Test-App und werden danach gelöscht; echte Daten nie ins Repo.
 */
@RunWith(AndroidJUnit4::class)
class EchteDatenSyncTest {
    private val kontext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ziel get() = kontext.getDatabasePath(DB)
    private val fotoOrdner get() = File(kontext.filesDir, Einstellungsschluessel.FOTO_ORDNER)

    @After
    fun aufraeumen() {
        kontext.deleteDatabase(DB)
        fotoOrdner.deleteRecursively()
    }

    @Test
    fun echteDatenbankSynchronisiertMitTestServer(): Unit = runBlocking<Unit> {
        val argumente = InstrumentationRegistry.getArguments()
        val quelle = argumente.getString(ARG_DB)
        if (quelle == null) {
            // Kein assumeTrue: Der Managed-Device-Lauf (UTP) wertet eine verletzte Annahme als Fehlschlag.
            Log.i(TAG, "keine Echtdaten-Argumente – Test übersprungen")
            return@runBlocking
        }
        // Ab hier ist ein Echtdaten-Lauf gemeint: Jedes fehlende Stück ist ein Fehler, nie ein stilles Bestehen.
        val serverText = assertNotNull(argumente.getString(ARG_SERVER), "Argument $ARG_SERVER fehlt, obwohl $ARG_DB gesetzt ist")
        val benutzer = assertNotNull(argumente.getString(ARG_BENUTZER), "Argument $ARG_BENUTZER fehlt, obwohl $ARG_DB gesetzt ist")
        val passwort = assertNotNull(argumente.getString(ARG_PASSWORT), "Argument $ARG_PASSWORT fehlt, obwohl $ARG_DB gesetzt ist")
        // Wie ein Debug-Build: http nur für 10.0.2.2 und localhost.
        val server = assertNotNull(ServerAdresse.pruefen(serverText, debug = true), "$ARG_SERVER ist keine erlaubte Adresse")
        val datei = File(quelle)
        assertTrue(datei.isFile && datei.canRead(), "$ARG_DB ist keine lesbare Datei: ${datei.path}")
        val fotos = argumente.getString(ARG_FOTOS)?.let(::File)
        if (fotos != null) assertTrue(fotos.isDirectory && fotos.canRead(), "$ARG_FOTOS ist kein lesbarer Ordner: ${fotos.path}")

        kopiere(datei, ziel)
        val room = baueDatenbank(kontext, DB) // normale Fabrik: MIGRATION_9_10, Callback, kein destruktiver Fallback
        val client = standardHttpClient()
        // Das Token lebt nur hier im Speicher.
        var token: String? = null
        val zugang = KtorServerZugang(server, client) { token }
        try {
            room.openHelper.writableDatabase // läuft die Migration
            val umgeschrieben = fotos?.let { fotosUebernehmen(room, it) }
            val dao = room.syncDao()
            val ablage = FotoAblage(kontext)
            val speicher = SyncSpeicher(room, ablage, FotoIndex(dao, ablage))

            val zustand = speicher.zustand()
            assertTrue(zustand.aktiv, "Sync ist in der echten Datenbank nicht aktiv")
            val cursorVorher = zustand.cursor
            val outboxVorher = dao.outbox().size

            token = zugang.anmelden(LoginRequest(benutzer, passwort, GERAET)).token

            // Immer der Test-Server, nie die gespeicherte Adresse.
            val lauf = SyncLauf(speicher) { zugang }
            var laeufe = 0
            var gesendet = 0
            var geholt = 0
            do {
                laeufe++
                when (val ergebnis = lauf.ausfuehren()) {
                    is SyncErgebnis.Fertig -> {
                        gesendet += ergebnis.gesendet
                        geholt += ergebnis.geholt
                    }
                    else -> fail("Lauf $laeufe: $ergebnis statt Fertig")
                }
                // Ein Lauf sendet höchstens 20 Pakete; wie der Auslöser danach erneut laufen, solange etwas wartet.
            } while (dao.outbox().isNotEmpty() && laeufe < MAX_LAEUFE)

            val outbox = dao.outbox()
            assertTrue(outbox.isEmpty(), "Outbox nach $laeufe Läufen nicht leer: ${outbox.size} Einträge, ${auszug(outbox.map { it.type to it.recordId })}")
            val probleme = dao.probleme()
            assertTrue(probleme.isEmpty(), "sync_problem: ${probleme.size} Zeilen, ${auszug(probleme.map { it.type to "${it.recordId} (${it.code})" })}")
            assertEquals(null, speicher.zustand().letzterFehler, "lastError nach dem Lauf")

            val lokal = lokaleDatensaetze(room)
            val (serverLebend, serverGesamt) = serverDatensaetze(zugang)
            val nurLokal = lokal.values.flatten().toSet() - serverLebend.toSet()
            val nurServer = serverLebend.toSet() - lokal.values.flatten().toSet()
            assertTrue(
                nurLokal.isEmpty() && nurServer.isEmpty(),
                "Lebende Datensätze lokal ≠ Server: nur lokal ${nurLokal.size} ${auszug(nurLokal.map { it.first.wire to it.second })}, " +
                    "nur Server ${nurServer.size} ${auszug(nurServer.map { it.first.wire to it.second })}",
            )
            val doppelt = serverGesamt.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue(doppelt.isEmpty(), "Server liefert doppelte IDs: ${doppelt.size} ${auszug(doppelt.map { it.first.wire to it.second })}")
            for ((typ, ids) in lokal) {
                assertEquals(ids.size, ids.toSet().size, "doppelte lokale IDs bei ${typ.wire}")
            }

            val jeTyp = RecordType.entries.joinToString { "${it.wire}=${lokal.getValue(it).size}" }
            Log.i(
                TAG,
                "geprüft: $laeufe Läufe, gesendet $gesendet, geholt $geholt, Outbox vorher $outboxVorher, " +
                    "Cursor $cursorVorher → ${speicher.zustand().cursor}, Fotos umgeschrieben ${umgeschrieben ?: "–"}, lebend $jeTyp",
            )
        } finally {
            // Test-Token widerrufen; scheitert das, verfällt es auf dem Test-Server.
            if (token != null) {
                try {
                    zugang.abmelden()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServerFehler) {
                    Log.w(TAG, "Abmelden am Test-Server gescheitert: ${e.kennung}")
                }
            }
            token = null
            client.close()
            room.close()
        }
    }

    /**
     * Kopiert die Fotos aus [ordner] in den Fotoordner der Test-App und schreibt die `file:`-Links der Rezepte, deren
     * Dateiname dort liegt, auf die Kopie um – mit `applyingRemote = 1`, damit die Trigger nichts vormerken. Liefert die
     * Zahl der umgeschriebenen Links.
     */
    private fun fotosUebernehmen(room: FoodyDatenbank, ordner: File): Int {
        fotoOrdner.mkdirs()
        val namen = ordner.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }.orEmpty().map { f ->
            f.copyTo(File(fotoOrdner, f.name), overwrite = true)
            f.name
        }.toSet()
        val db = room.openHelper.writableDatabase
        var zahl = 0
        db.beginTransaction()
        try {
            db.execSQL("UPDATE sync_state SET applyingRemote = 1 WHERE id = 1")
            val links = db.query("SELECT id, imageUri FROM recipe WHERE imageUri LIKE 'file:%'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
            for ((id, link) in links) {
                val name = Uri.parse(link).lastPathSegment ?: continue
                if (name !in namen) continue
                db.execSQL("UPDATE recipe SET imageUri = ? WHERE id = ?", arrayOf<Any>(Uri.fromFile(File(fotoOrdner, name)).toString(), id))
                zahl++
            }
            db.execSQL("UPDATE sync_state SET applyingRemote = 0 WHERE id = 1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return zahl
    }

    /** Lebende lokale Datensätze je Typ (die IDs der Wurzeltabellen). */
    private suspend fun lokaleDatensaetze(room: FoodyDatenbank): Map<RecordType, List<Pair<RecordType, String>>> {
        val dao = room.syncDao()
        return RecordType.entries.associateWith { typ ->
            when (typ) {
                RecordType.INGREDIENT -> dao.zutatIds()
                RecordType.RECIPE -> dao.rezeptIds()
                RecordType.MEAL_SLOT -> dao.planEintragIds()
                RecordType.SHOPPING_LIST -> dao.einkaufslisteIds()
                RecordType.SHOPPING_ITEM -> dao.einkaufsartikelIds()
                RecordType.TAGEBUCH_EINTRAG -> dao.tagebuchIds()
            }.map { typ to it }
        }
    }

    /** Alle Seiten ab 0: (lebende Datensätze, alle gelieferten Schlüssel samt Löschungen – für die Doppelt-Prüfung). */
    private suspend fun serverDatensaetze(zugang: ServerZugang): Pair<List<Pair<RecordType, String>>, List<Pair<RecordType, String>>> {
        val lebend = ArrayList<Pair<RecordType, String>>()
        val alle = ArrayList<Pair<RecordType, String>>()
        var since = 0L
        while (true) {
            val seite = zugang.pull(since, Protocol.MAX_PULL_LIMIT)
            for (r in seite.records) {
                alle += r.type to r.id
                if (!r.deleted) lebend += r.type to r.id
            }
            if (!seite.hasMore) return lebend to alle
            assertTrue(seite.nextCursor > since, "Server-Cursor rückt nicht vor ($since → ${seite.nextCursor})")
            since = seite.nextCursor
        }
    }

    /** Höchstens [MAX_AUSZUG] Schlüssel `typ/id` für Fehlermeldungen – nie Inhalte. */
    private fun auszug(schluessel: Collection<Pair<String, String>>): String =
        schluessel.take(MAX_AUSZUG).joinToString(prefix = "[", postfix = if (schluessel.size > MAX_AUSZUG) ", …]" else "]") { "${it.first}/${it.second}" }

    /** Kopiert die Datenbank samt WAL: ohne `-wal` fehlten noch nicht übertragene Änderungen. */
    private fun kopiere(quelle: File, ziel: File) {
        kontext.deleteDatabase(ziel.name)
        ziel.parentFile!!.mkdirs()
        for (endung in listOf("", "-wal", "-shm")) {
            val datei = File(quelle.path + endung)
            if (datei.isFile) datei.copyTo(File(ziel.path + endung), overwrite = true)
        }
    }

    private companion object {
        const val DB = "echte-sync.db"
        const val ARG_DB = "echteDb"
        const val ARG_SERVER = "testServer"
        const val ARG_BENUTZER = "testBenutzer"
        const val ARG_PASSWORT = "testPasswort"
        const val ARG_FOTOS = "echteFotos"
        const val GERAET = "EchteDatenSyncTest"
        const val TAG = "EchteDaten"

        /** Sicherung gegen eine Outbox, die nie leer wird (20 Pakete je Lauf × 500 Datensätze). */
        const val MAX_LAEUFE = 10
        const val MAX_AUSZUG = 20
    }
}
