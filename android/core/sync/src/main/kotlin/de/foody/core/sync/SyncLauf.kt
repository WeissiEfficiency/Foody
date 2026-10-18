package de.foody.core.sync

import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PushResult
import de.foody.api.protocol.PushStatus
import de.foody.api.protocol.SyncRecord
import de.foody.core.data.sync.Ausstehend
import de.foody.core.data.sync.Fassung
import de.foody.core.data.sync.SyncSpeicher
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Der Server liefert weitere Seiten, aber einen Cursor, der nicht vorrückt. */
private class CursorHaengt : Exception(ServerFehler.CURSOR_HAENGT)

/**
 * Ein vollständiger Sync-Lauf gegen den eigenen Server (Protokoll 3), wie `SyncEngine` der v1:
 * 1. Outbox in Paketen senden (höchstens [Protocol.MAX_PUSH_RECORDS] bzw. [Protocol.MAX_PUSH_BYTES]); fehlende Fotos
 *    vorher hochladen.
 * 2. Ergebnisse verbuchen (angenommen, zusammengeführt, abgelehnt; fehlende Verweisziele werden nachgereicht).
 * 3. Alle Seiten ab dem Cursor holen und einmal anwenden; `410` → Voll-Abgleich.
 * 4. Gewünschte Fotos laden und verknüpfen.
 *
 * Alles läuft unter [SyncSpeicher.laufSperre] (ein Lauf zur Zeit; Trennen wartet). [zugang] wird erst bei aktivem Sync
 * gebaut; `null` heißt: kein (lesbares) Token. In `sync_state.lastError` landen nur die festen Kennungen aus
 * [ServerFehler]; ein erfolgreicher Lauf setzt sie zurück. Das Token wird hier nie verworfen – auch nicht bei `401`.
 */
class SyncLauf(
    private val speicher: SyncSpeicher,
    private val zugang: () -> ServerZugang?,
) {
    suspend fun ausfuehren(): SyncErgebnis = speicher.laufSperre.withLock {
        try {
            if (!speicher.zustand().aktiv) return@withLock SyncErgebnis.Inaktiv
            // Ohne Token kann nichts gehen. Ob es fehlt oder der Keystore gerade klemmt, ist hier nicht zu unterscheiden:
            // „Anmeldung abgelaufen“ zeigt dem Nutzer den Weg, der nächste Auslöser versucht es ohnehin erneut. Ebenso,
            // wenn das Bauen des Zugangs scheitert (z. B. Token nicht lesbar).
            val server = try {
                zugang()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@withLock fehlgeschlagen(ServerFehler.ANMELDUNG_ABGELAUFEN, voruebergehend = false)
            val gesendet = pushen(server)
            val geholt = holen(server)
            fotosLaden(server)
            speicher.fehler(null)
            SyncErgebnis.Fertig(gesendet, geholt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerFehler) {
            fehlgeschlagen(e.kennung, voruebergehend(e))
        } catch (_: CursorHaengt) {
            fehlgeschlagen(ServerFehler.CURSOR_HAENGT, voruebergehend = true)
        } catch (_: Exception) {
            // Lokaler Fehler (Datenbank, Dateien) oder Unerwartetes: nur die feste Kennung, nie die Meldung.
            fehlgeschlagen(ServerFehler.SERVER_FEHLER, voruebergehend = false)
        }
    }

    private suspend fun fehlgeschlagen(kennung: String, voruebergehend: Boolean): SyncErgebnis {
        speicher.fehler(kennung)
        return SyncErgebnis.Fehlgeschlagen(kennung, voruebergehend)
    }

    /**
     * Kann ein späterer Versuch ohne Zutun gelingen? Netz, `5xx` (auch unlesbare Antworten, z. B. eine WLAN-Anmeldeseite),
     * `408`/`429` und ein Cursor-Problem ja; Anmeldung, Versionen und dauerhafte Ablehnungen nicht.
     */
    private fun voruebergehend(e: ServerFehler): Boolean = when (e) {
        is ServerFehler.Offline, is ServerFehler.Server, is ServerFehler.CursorAbgelaufen -> true
        is ServerFehler.Abgelehnt -> e.status == 408 || e.status == 429
        is ServerFehler.AnmeldungAbgelaufen, is ServerFehler.AppZuAlt, is ServerFehler.ServerZuAlt, is ServerFehler.ZuGross -> false
    }

    // ---- Push ----------------------------------------------------------------------------------------------

    /** Sendet die Outbox in Paketen; liefert die Zahl der angenommenen oder zusammengeführten Datensätze. */
    private suspend fun pushen(server: ServerZugang): Int {
        var gesendet = 0
        // In diesem Lauf schon gesendete Fassungen (z. B. abgelehnt und weiter vorgemerkt): nicht erneut.
        val schonGesendet = HashSet<Fassung>()
        repeat(MAX_PAKETE) {
            val paket = speicher.naechstesPaket(Protocol.MAX_PUSH_RECORDS, Protocol.MAX_PUSH_BYTES, schonGesendet)
            if (paket.isEmpty()) return gesendet
            schonGesendet += paket.map { it.schluessel }
            // Fotos vor den Rezepten hochladen: Kennt der Server ein Foto noch nicht, bliebe es beim Empfänger leer.
            val bereit = fotosHochladen(server, paket)
            if (bereit.isNotEmpty()) gesendet += paketSenden(server, bereit)
        }
        return gesendet
    }

    /**
     * Sendet [paket]. Antwortet der Server mit `413`, wird es halbiert und beide Hälften nacheinander gesendet (Eltern
     * vor Kindern bleibt erhalten); ein einzelner zu großer Datensatz bekommt das Problem `too_large`.
     */
    private suspend fun paketSenden(server: ServerZugang, paket: List<Ausstehend>): Int {
        val antwort = try {
            server.push(paket.map { it.record })
        } catch (_: ServerFehler.ZuGross) {
            if (paket.size > 1) {
                val haelfte = paket.size / 2
                return paketSenden(server, paket.subList(0, haelfte)) + paketSenden(server, paket.subList(haelfte, paket.size))
            }
            speicher.abgelehnt(paket.single(), ZU_GROSS)
            return 0
        }
        val nachSchluessel = paket.associateBy { it.record.type to it.record.id }
        var angenommen = 0
        for (ergebnis in antwort.results) {
            val a = nachSchluessel[ergebnis.type to ergebnis.id] ?: continue
            if (verbuchen(a, ergebnis)) angenommen++
        }
        return angenommen
    }

    /** Verbucht ein Push-Ergebnis; `true`, wenn der Server den Datensatz angenommen oder zusammengeführt hat. */
    private suspend fun verbuchen(a: Ausstehend, ergebnis: PushResult): Boolean {
        when (ergebnis.status) {
            PushStatus.ACCEPTED -> {
                val rev = ergebnis.rev
                // Ohne Revision (sollte nicht vorkommen) nur aus der Outbox nehmen, wie die v1.
                if (rev != null) speicher.erledigt(a, rev) else speicher.zusammengefuehrt(a, null, null, null)
            }
            PushStatus.MERGED -> speicher.zusammengefuehrt(a, ergebnis.rev, ergebnis.canonicalId, ergebnis.current)
            PushStatus.REJECTED -> speicher.abgelehnt(a, ablehnungsCode(ergebnis.code))
        }
        return ergebnis.status != PushStatus.REJECTED
    }

    private fun ablehnungsCode(code: ErrorCode?): String =
        code?.let { ErrorCode.serializer().descriptor.getElementName(it.ordinal) } ?: ABGELEHNT

    // ---- Fotos hoch ------------------------------------------------------------------------------------------

    /**
     * Lädt die Fotos der Rezepte in [paket] hoch, die der Server noch nicht hat, und liefert, was gesendet werden darf:
     * - Hash ohne eigene Datei (offener Fotowunsch): den hat der Server, von dem er stammt – das Rezept geht mit.
     * - Eigene Datei nicht mehr lesbar oder seit dem Hashen geändert (dann wird ihr gemerkter Hash verworfen): nur dieses
     *   Rezept wartet in der Outbox (der nächste Lauf prüft das Foto neu), statt mit einem Hash zu gehen, den der Server
     *   nie bekommt.
     * - Der Server lehnt das Foto dauerhaft ab (`413`, sonstige `4xx`): das Rezept geht ohne Foto mit dem Problem
     *   `photo_unsyncable` ([Ausstehend.ohneFoto]); sonst lüde jeder Lauf dieselben Bytes erneut hoch.
     * Netz- und Serverfehler brechen den Lauf ab; nichts aus dem Paket verlässt dann die Outbox.
     */
    private suspend fun fotosHochladen(server: ServerZugang, paket: List<Ausstehend>): List<Ausstehend> {
        val hashes = paket.mapNotNull { it.fotoHash }.distinct()
        if (hashes.isEmpty()) return paket
        val abgelehnt = HashSet<String>()
        val zurueck = HashSet<Fassung>()
        for (hash in server.fotosFehlend(hashes)) {
            val eigene = paket.filter { it.fotoHash == hash && it.fotoDatei != null }
            val datei = eigene.firstOrNull()?.fotoDatei ?: continue // nur Fotowünsche
            val bytes = try {
                withContext(Dispatchers.IO) { datei.readBytes() }
            } catch (_: IOException) {
                null
            }
            if (bytes == null || PhotoHash.of(bytes) != hash) {
                // Inhalt passt nicht mehr zum gemerkten Hash: Eintrag verwerfen, damit der nächste Lauf neu hasht.
                if (bytes != null) speicher.fotoHashVerwerfen(datei)
                zurueck += eigene.map { it.schluessel }
                continue
            }
            try {
                server.fotoHochladen(hash, bytes)
            } catch (_: ServerFehler.ZuGross) {
                abgelehnt += hash
            } catch (e: ServerFehler.Abgelehnt) {
                if (voruebergehend(e)) throw e
                abgelehnt += hash
            }
        }
        if (abgelehnt.isEmpty() && zurueck.isEmpty()) return paket
        return paket.filter { it.schluessel !in zurueck }.map { a ->
            // Nur eigene Fotos: ein Fotowunsch mit demselben Hash behält ihn (sonst überschriebe er den Server-Stand).
            if (a.fotoHash in abgelehnt && a.fotoDatei != null) a.ohneFoto() else a
        }
    }

    // ---- Pull ----------------------------------------------------------------------------------------------

    /** Holt alle Seiten ab dem Cursor und wendet sie einmal an; bei `410` Voll-Abgleich. Liefert die Zahl der Datensätze. */
    private suspend fun holen(server: ServerZugang): Int {
        val cursor = speicher.zustand().cursor
        return try {
            val (datensaetze, naechster) = seitenHolen(server, cursor)
            speicher.anwenden(datensaetze, naechster)
            datensaetze.size
        } catch (_: ServerFehler.CursorAbgelaufen) {
            val (datensaetze, naechster) = seitenHolen(server, 0)
            speicher.vollabgleich(datensaetze, naechster)
            datensaetze.size
        }
    }

    /**
     * Alle Seiten ab [von]. Ist eine Seite zu groß (`413` oder Antwort über der Lesegrenze), wird das Limit halbiert und
     * dieselbe Seite erneut geholt, bis [MIN_PULL_LIMIT]; sonst bliebe der Cursor dauerhaft stehen.
     */
    private suspend fun seitenHolen(server: ServerZugang, von: Long): Pair<List<SyncRecord>, Long> {
        val alle = ArrayList<SyncRecord>()
        var since = von
        var limit = Protocol.MAX_PULL_LIMIT
        while (true) {
            val seite = try {
                server.pull(since, limit)
            } catch (e: ServerFehler.ZuGross) {
                if (limit <= MIN_PULL_LIMIT) throw e
                limit = maxOf(limit / 2, MIN_PULL_LIMIT)
                continue
            }
            alle += seite.records
            // Ein Cursor, der trotz weiterer Seiten nicht vorrückt, würde endlos laufen.
            if (seite.hasMore && seite.nextCursor <= since) throw CursorHaengt()
            since = seite.nextCursor
            if (!seite.hasMore) return alle to since
        }
    }

    // ---- Fotos runter --------------------------------------------------------------------------------------

    /**
     * Lädt die Fotos, die Server-Rezepte brauchen ([SyncSpeicher.fotowuensche]). Nicht auf dem Server → der Wunsch bleibt
     * für den nächsten Lauf. Eine vorübergehende Störung hält die übrigen Wünsche nicht auf und wird danach gemeldet;
     * ein dauerhaft abgelehntes oder zu großes Foto wird übersprungen. Ein lokaler Ein-/Ausgabefehler beim Ablegen gilt als
     * vorübergehende Störung (Kennung `server_fehler`, es gibt keine eigene). Anmeldung und Versionen brechen sofort ab.
     */
    private suspend fun fotosLaden(server: ServerZugang) {
        var stoerung: ServerFehler? = null
        for ((rezeptId, sha256) in speicher.fotowuensche()) {
            val bytes = try {
                server.fotoLaden(sha256)
            } catch (e: ServerFehler) {
                when {
                    e is ServerFehler.AnmeldungAbgelaufen || e is ServerFehler.AppZuAlt || e is ServerFehler.ServerZuAlt -> throw e
                    voruebergehend(e) -> stoerung = e
                }
                null
            } ?: continue
            try {
                speicher.fotoErhalten(rezeptId, sha256, bytes)
            } catch (_: IOException) {
                // Ablegen gescheitert (z. B. Speicher voll): vorübergehend, der Wunsch bleibt; die übrigen weiter.
                if (stoerung == null) stoerung = ServerFehler.Server(0)
            }
        }
        stoerung?.let { throw it }
    }

    private companion object {
        /** Höchstens so viele Pakete je Lauf (wie v1); der Rest geht im nächsten Lauf. */
        const val MAX_PAKETE = 20

        /** Kleinstes Pull-Limit beim Halbieren. */
        const val MIN_PULL_LIMIT = 1

        /** Problem-Code: ein einzelner Datensatz ist dem Server zu groß. */
        const val ZU_GROSS = "too_large"

        /** Problem-Code einer Ablehnung ohne Fehlercode. */
        const val ABGELEHNT = "rejected"
    }
}
