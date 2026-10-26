package de.foody.core.data.sync

import android.database.SQLException
import androidx.room.withTransaction
import de.foody.api.protocol.IngredientPayload
import de.foody.api.protocol.PayloadValidator
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.RecipePayload
import de.foody.api.protocol.RecordType
import de.foody.api.protocol.SyncRecord
import de.foody.api.protocol.decode
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncFotoGewuenschtEntity
import de.foody.core.data.db.SyncFotoLokalEntity
import de.foody.core.data.db.SyncOutboxEntity
import de.foody.core.data.db.SyncProblemEntity
import de.foody.core.data.db.SyncRevisionEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.zutaten.zusammenfuehrenSchritte
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Sync-Zustand für Sync-Lauf und Oberfläche (aus `sync_state`). [letzterFehler] ist eine feste Kennung. */
data class SyncZustand(
    val aktiv: Boolean,
    val serverUrl: String?,
    val kontoId: String?,
    val cursor: Long,
    val letzterSync: Long?,
    val letzterFehler: String?,
)

/**
 * Lokale Seite des Syncs, die einzige Schnittstelle zwischen `foody.db` und dem Sync-Client: Sync ein- und ausschalten,
 * die Outbox in Pakete fassen, Push-Ergebnisse verbuchen, Server-Daten anwenden, Voll-Abgleich, Fotowünsche.
 *
 * Die Outbox füllen die Trigger ([de.foody.core.data.db.SyncTrigger]). Server-Daten werden mit `applyingRemote = 1`
 * geschrieben, damit sie nicht als lokale Änderung zurückgehen. Lokale Änderungen mit offenem Outbox-Eintrag gewinnen
 * gegen den Server-Stand; sie gehen mit dem nächsten Push hinaus.
 *
 * Fotos: Nur eigene Dateien im Fotoordner werden übertragen. Fremde Links (`content:` aus der Galerie) gehen nie hinaus
 * und bleiben stehen, wenn ein Server-Datensatz ohne Foto kommt.
 */
class SyncSpeicher(
    private val db: FoodyDatenbank,
    private val ablage: FotoAblage,
    private val index: FotoIndex,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.syncDao()

    /**
     * Ein Sync-Lauf zur Zeit. [deaktivieren] wartet darauf, damit ein laufender Abgleich nach dem Aufräumen keine
     * Revisionen, Probleme oder Fotowünsche zurückschreibt.
     */
    val laufSperre = Mutex()

    suspend fun zustand(): SyncZustand {
        val z = dao.zustand() ?: SyncZustandEntity()
        return SyncZustand(z.active, z.serverUrl, z.kontoId, z.cursor, z.lastSyncAt, z.lastError)
    }

    /**
     * Schaltet den Sync ein (Cursor 0: der nächste Lauf holt alles). Mit [allesHochladen] („Zusammenführen“) werden alle
     * vorhandenen Datensätze zum Senden vorgemerkt. Eine Transaktion.
     */
    suspend fun aktivieren(serverUrl: String, kontoId: String, allesHochladen: Boolean) {
        db.withTransaction {
            val z = dao.zustand() ?: SyncZustandEntity()
            dao.upsertZustand(z.copy(active = true, serverUrl = serverUrl, kontoId = kontoId, cursor = 0, lastError = null))
            if (allesHochladen) dao.allesVormerken(uhr())
        }
    }

    /**
     * Schaltet den Sync aus und verwirft Outbox, Revisionen, Probleme und Fotowünsche (ein späteres Verbinden holt alles
     * ab Cursor 0 und legt die Wünsche neu an). Die lokalen Daten bleiben. Wartet auf einen laufenden Abgleich.
     */
    suspend fun deaktivieren() {
        laufSperre.withLock {
            db.withTransaction {
                val z = dao.zustand() ?: SyncZustandEntity()
                dao.upsertZustand(
                    z.copy(active = false, applyingRemote = false, serverUrl = null, kontoId = null, cursor = 0, lastError = null),
                )
                dao.outboxLeeren()
                dao.revisionenLeeren()
                dao.problemeLeeren()
                dao.fotowuenscheLeeren()
            }
        }
    }

    /** Merkt die feste Fehlerkennung des letzten Laufs (`null` = kein Fehler). Nie Token oder Antwort-Bodies. */
    suspend fun fehler(kennung: String?) {
        db.withTransaction { dao.upsertZustand((dao.zustand() ?: SyncZustandEntity()).copy(lastError = kennung)) }
    }

    // ---- Pakete ----------------------------------------------------------------------------------------------

    /**
     * Datensätze für den nächsten Push: lebende in [RecordType]-Reihenfolge (Abhängigkeiten zuerst), danach Löschungen
     * in umgekehrter Reihenfolge; höchstens [maxDatensaetze] und – als `{"records":[…]}` kodiert – [maxBytes] (ein
     * einzelner größerer Datensatz geht allein, der Server lehnt ihn dann mit `413` ab). Eine lebend vorgemerkte Zeile,
     * die inzwischen fehlt, geht als Löschung hinaus. Fassungen aus [ausser] (in diesem Lauf schon gesendet) werden
     * übersprungen. Die Outbox bleibt unverändert; bei inaktivem Sync ist das Paket leer.
     */
    suspend fun naechstesPaket(maxDatensaetze: Int, maxBytes: Long, ausser: Set<Fassung> = emptySet()): List<Ausstehend> {
        if (!zustand().aktiv) return emptyList()
        // Fotos vor der Lese-Transaktion prüfen und hashen: Datei-I/O darf keine Transaktion halten.
        val fotos = fotosPruefen(ausser)
        return db.withTransaction { paketBauen(maxDatensaetze, maxBytes, ausser, fotos) }
    }

    private enum class FotoLage { VORHANDEN, FEHLT, NICHT_UEBERTRAGBAR, UNLESBAR }

    /** Foto eines vorgemerkten Rezepts zum Zeitpunkt der Prüfung. */
    private class GeprueftesFoto(val link: String, val lage: FotoLage, val hash: String? = null, val datei: File? = null)

    /** Prüft und hasht die eigenen Fotos der lebend vorgemerkten Rezepte (Schlüssel: Rezept-ID). Ohne Transaktion. */
    private suspend fun fotosPruefen(ausser: Set<Fassung>): Map<String, GeprueftesFoto> {
        val ergebnis = HashMap<String, GeprueftesFoto>()
        for (e in dao.outbox()) {
            if (e.type != RecordType.RECIPE.wire || e.deleted) continue
            if (Fassung(RecordType.RECIPE, e.recordId, e.queuedAt) in ausser) continue
            val link = db.rezeptDao().nachId(e.recordId)?.imageUri ?: continue
            val datei = ablage.datei(link) ?: continue // fremder Link: wird nie gesendet
            if (!datei.isFile) {
                ergebnis[e.recordId] = GeprueftesFoto(link, FotoLage.FEHLT)
                continue
            }
            // Was der Server dauerhaft ablehnt (zu groß, kein JPEG), erst verkleinern; hilft das nicht, geht das Rezept
            // ohne Foto hinaus (Problem photo_unsyncable), statt den Abgleich zu blockieren.
            var uebertragbar = withContext(Dispatchers.IO) { uebertragbar(datei) }
            if (uebertragbar == false && withContext(Dispatchers.IO) { ablage.verkleinern(datei) }) {
                uebertragbar = withContext(Dispatchers.IO) { uebertragbar(datei) }
            }
            ergebnis[e.recordId] = when (uebertragbar) {
                null -> GeprueftesFoto(link, FotoLage.UNLESBAR)
                false -> GeprueftesFoto(link, FotoLage.NICHT_UEBERTRAGBAR)
                true -> index.hash(link)?.let { GeprueftesFoto(link, FotoLage.VORHANDEN, it, datei) }
                    ?: GeprueftesFoto(link, FotoLage.UNLESBAR)
            }
        }
        return ergebnis
    }

    /**
     * Nimmt der Server die Datei an? Nicht leer, höchstens [Protocol.MAX_PHOTO_BYTES] und mit JPEG-Signatur `FF D8 FF`.
     * `null` = gerade nicht lesbar (kein Dauerzustand: zurückstellen).
     */
    private fun uebertragbar(datei: File): Boolean? = try {
        val groesse = datei.length()
        groesse in 1..Protocol.MAX_PHOTO_BYTES && datei.inputStream().use { ein ->
            val kopf = ByteArray(3)
            ein.read(kopf) == 3 && kopf[0] == 0xFF.toByte() && kopf[1] == 0xD8.toByte() && kopf[2] == 0xFF.toByte()
        }
    } catch (_: IOException) {
        null
    }

    private sealed interface Gebaut {
        data class Fertig(val ausstehend: Ausstehend) : Gebaut
        data object Weg : Gebaut
        data object Zurueckgestellt : Gebaut
    }

    private suspend fun paketBauen(
        maxDatensaetze: Int,
        maxBytes: Long,
        ausser: Set<Fassung>,
        fotos: Map<String, GeprueftesFoto>,
    ): List<Ausstehend> {
        // Innerhalb eines Typs bleibt die Outbox-Reihenfolge (queuedAt) erhalten.
        val eintraege = dao.outbox().mapNotNull { e ->
            val typ = RecordType.entries.firstOrNull { it.wire == e.type } ?: return@mapNotNull null
            (typ to e).takeIf { Fassung(typ, e.recordId, e.queuedAt) !in ausser }
        }
        val lebend = eintraege.filter { !it.second.deleted }.sortedBy { it.first.ordinal }
        val geloescht = eintraege.filter { it.second.deleted }

        val paket = ArrayList<Ausstehend>()
        var bytes = HUELLE_BYTES
        /** Nimmt [a] auf, wenn es noch passt; sonst ist das Paket voll. */
        fun aufnehmen(a: Ausstehend): Boolean {
            if (paket.size >= maxDatensaetze) return false
            val groesse = Protocol.json.encodeToString(SyncRecord.serializer(), a.record).toByteArray().size + 1L
            if (paket.isNotEmpty() && bytes + groesse > maxBytes) return false
            bytes += groesse
            paket += a
            return true
        }

        val verschwunden = ArrayList<Pair<RecordType, SyncOutboxEntity>>()
        for ((typ, e) in lebend) {
            if (paket.size >= maxDatensaetze) break
            when (val g = bauen(typ, e, fotos)) {
                is Gebaut.Fertig -> if (!aufnehmen(g.ausstehend)) return paket
                Gebaut.Weg -> verschwunden += typ to e
                Gebaut.Zurueckgestellt -> Unit // Foto gerade nicht bestimmbar: bleibt in der Outbox
            }
        }
        val loeschungen = (geloescht + verschwunden).sortedWith(
            compareByDescending<Pair<RecordType, SyncOutboxEntity>> { it.first.ordinal }.thenBy { it.second.queuedAt },
        )
        for ((typ, e) in loeschungen) {
            val record = Abbildung.zuSyncRecord(db, typ, e.recordId, geloescht = true) ?: continue
            if (!aufnehmen(Ausstehend(record, e.queuedAt, null, null, null))) break
        }
        return paket
    }

    /** Baut den lebenden Datensatz (ein Schnappschuss in der Transaktion des Paketbaus) samt Foto. */
    private suspend fun bauen(typ: RecordType, e: SyncOutboxEntity, fotos: Map<String, GeprueftesFoto>): Gebaut {
        val id = e.recordId
        val record = Abbildung.zuSyncRecord(db, typ, id, geloescht = false) ?: return Gebaut.Weg
        fun fertig(r: SyncRecord = record, problem: String? = null, hash: String? = null, datei: File? = null) =
            Gebaut.Fertig(Ausstehend(r, e.queuedAt, problem, hash, datei))
        if (typ != RecordType.RECIPE) return fertig()

        // Ein offener Fotowunsch geht vor: das Gerät hat das Foto noch nicht geladen und darf den Server-Stand nicht
        // mit „kein Foto“ überschreiben.
        dao.fotoGewuenscht(id)?.let { return fertig(mitFoto(record, it.sha256), hash = it.sha256) }
        val link = db.rezeptDao().nachId(id)?.imageUri
        if (link == null || !ablage.istEigeneDatei(link)) return fertig() // kein Foto oder fremder Link
        // Seit der Prüfung geändert oder nicht lesbar: zurückstellen – nie `photo = null` für ein eigenes Foto senden.
        val foto = fotos[id]?.takeIf { it.link == link } ?: return Gebaut.Zurueckgestellt
        return when (foto.lage) {
            FotoLage.FEHLT -> fertig() // das Gerät hat das Foto nicht (mehr)
            FotoLage.NICHT_UEBERTRAGBAR -> fertig(problem = FOTO_NICHT_UEBERTRAGBAR)
            FotoLage.UNLESBAR -> Gebaut.Zurueckgestellt
            FotoLage.VORHANDEN -> fertig(mitFoto(record, foto.hash!!), hash = foto.hash, datei = foto.datei)
        }
    }

    private fun mitFoto(record: SyncRecord, hash: String): SyncRecord {
        val payload = RecordType.RECIPE.decode(requireNotNull(record.payload)) as RecipePayload
        val neu = Protocol.json.encodeToJsonElement(RecipePayload.serializer(), payload.copy(photo = hash)) as JsonObject
        return record.copy(payload = neu)
    }

    // ---- Push-Ergebnisse --------------------------------------------------------------------------------------

    /** Der Server hat [a] angenommen (Revision [rev]): Revision merken, aus der Outbox nehmen, falls unverändert. */
    suspend fun erledigt(a: Ausstehend, rev: Long) {
        val typ = a.record.type.wire
        val id = a.record.id
        db.withTransaction {
            dao.upsertRevision(SyncRevisionEntity(typ, id, rev))
            dao.loeschenProblem(typ, id)
            a.fotoProblem?.let { dao.upsertProblem(SyncProblemEntity(typ, id, it, uhr())) }
            entfernenFallsUnveraendert(a)
        }
    }

    /**
     * Der Server hat [a] mit seinem Stand zusammengeführt. Wurde der Eintrag inzwischen lokal geändert, gewinnt die lokale
     * Änderung (geht im nächsten Lauf hinaus) und die alte Revision bleibt, damit der Server erneut zusammenführt. Sonst
     * wird [rev] gemerkt (nur, wenn nicht in einen anderen Datensatz [kanonischeId] zusammengeführt wurde) und der
     * Server-Stand [aktuell] angewendet – ohne den Cursor zu verschieben.
     */
    suspend fun zusammengefuehrt(a: Ausstehend, rev: Long?, kanonischeId: String?, aktuell: SyncRecord?) {
        val typ = a.record.type.wire
        val id = a.record.id
        var anzuwenden: SyncRecord? = null
        db.withTransaction {
            dao.loeschenProblem(typ, id)
            a.fotoProblem?.let { dao.upsertProblem(SyncProblemEntity(typ, id, it, uhr())) }
            if (entfernenFallsUnveraendert(a)) {
                if (kanonischeId == null || kanonischeId == id) rev?.let { dao.upsertRevision(SyncRevisionEntity(typ, id, it)) }
                anzuwenden = aktuell
            }
        }
        anzuwenden?.let { anwendenIntern(listOf(it), neuerCursor = null, bekannteUeberspringen = false) }
    }

    /**
     * Der Server hat [a] abgelehnt: Problem [code] vermerken. Fehlt ein Verweisziel (`missing_reference`), bleibt der
     * Eintrag und lokal vorhandene, aber nicht vorgemerkte Ziele werden vorgemerkt (etwa eine Startzutat, die nach „nur
     * herunterladen“ nie gesendet wurde); sonst verlässt er die Outbox, falls unverändert.
     */
    suspend fun abgelehnt(a: Ausstehend, code: String) {
        db.withTransaction {
            dao.upsertProblem(SyncProblemEntity(a.record.type.wire, a.record.id, code, uhr()))
            if (code == FEHLENDER_VERWEIS) verweiszieleVormerken(a.record) else entfernenFallsUnveraendert(a)
        }
    }

    private suspend fun verweiszieleVormerken(record: SyncRecord) {
        val verweise = try {
            PayloadValidator.references(record)
        } catch (_: IllegalArgumentException) {
            return // auch SerializationException
        }
        for ((typ, id) in verweise) {
            if (dao.outboxEintrag(typ.wire, id) != null || !existiert(typ, id)) continue
            dao.upsertOutbox(SyncOutboxEntity(typ.wire, id, deleted = false, queuedAt = uhr()))
        }
    }

    /** Entfernt den Outbox-Eintrag, wenn er seit dem Paketbau nicht erneut vorgemerkt wurde. */
    private suspend fun entfernenFallsUnveraendert(a: Ausstehend): Boolean {
        val typ = a.record.type.wire
        if (dao.outboxEintrag(typ, a.record.id)?.queuedAt != a.vorgemerktUm) return false
        dao.loeschenOutbox(typ, a.record.id)
        return true
    }

    // ---- Anwenden ----------------------------------------------------------------------------------------------

    /**
     * Wendet vom Server geholte Datensätze an und setzt danach den Cursor auf [neuerCursor]. Jeder Datensatz läuft in
     * einer eigenen Transaktion mit `applyingRemote = 1` (Trigger aus), das in `finally` zurückgesetzt wird: Scheitert
     * einer (ungültiger Payload, fehlendes Verweisziel, Constraint), wird nur er zurückgerollt und als Problem vermerkt,
     * die übrigen werden übernommen. Der Cursor rückt erst ganz am Ende vor; bricht der Lauf vorher ab, wird dieselbe
     * Seite erneut geholt, und das Anwenden ist idempotent.
     */
    suspend fun anwenden(records: List<SyncRecord>, neuerCursor: Long) {
        anwendenIntern(records, neuerCursor, bekannteUeberspringen = true)
    }

    /**
     * Mit [bekannteUeberspringen] werden lebende Datensätze übersprungen, deren Revision lokal schon bekannt ist und die
     * es lokal gibt – etwa das Echo des eigenen Pushs. Sonst überschriebe das Echo lokale Besonderheiten (ein nicht
     * übertragbares eigenes Foto, das ohne Foto gesendet wurde). [neuerCursor] `null` lässt den Cursor stehen.
     */
    private suspend fun anwendenIntern(records: List<SyncRecord>, neuerCursor: Long?, bekannteUeberspringen: Boolean) {
        val jetzt = uhr()
        val lebend = records.filter { !it.deleted }.sortedWith(compareBy({ it.type.ordinal }, { it.rev ?: 0L }))
        val loeschungen = records.filter { it.deleted }.sortedWith(compareBy({ -it.type.ordinal }, { it.rev ?: 0L }))
        // Von einer Zutaten-Zusammenführung selbst vorgemerkte Einträge sind keine fremden lokalen Änderungen.
        val selbstVorgemerkt = HashMap<Pair<String, String>, Long>()
        for (r in lebend) einzeln(r, jetzt, selbstVorgemerkt, bekannteUeberspringen) { lebendAnwenden(r, jetzt, selbstVorgemerkt) }
        for (r in loeschungen) einzeln(r, jetzt, selbstVorgemerkt, false) { loeschungAnwenden(r) }
        db.withTransaction {
            val z = (dao.zustand() ?: SyncZustandEntity()).copy(applyingRemote = false)
            dao.upsertZustand(if (neuerCursor != null) z.copy(cursor = neuerCursor, lastSyncAt = jetzt) else z)
        }
    }

    private suspend fun einzeln(
        r: SyncRecord,
        jetzt: Long,
        selbstVorgemerkt: MutableMap<Pair<String, String>, Long>,
        bekannteUeberspringen: Boolean,
        block: suspend () -> Unit,
    ) {
        val vorher = HashMap(selbstVorgemerkt)
        val code = try {
            db.withTransaction {
                if (offeneLokaleAenderung(r, selbstVorgemerkt)) return@withTransaction
                if (bekannteUeberspringen && !r.deleted && bekannteRevision(r)) return@withTransaction
                dao.upsertZustand((dao.zustand() ?: SyncZustandEntity()).copy(applyingRemote = true))
                try {
                    block()
                } finally {
                    dao.applyingRemoteSetzen(false)
                }
            }
            null
        } catch (_: SQLException) {
            ANWENDEN_FEHLGESCHLAGEN
        } catch (_: IllegalArgumentException) {
            UNGUELTIGER_PAYLOAD // auch SerializationException, NumberFormatException
        } catch (_: DateTimeException) {
            UNGUELTIGER_PAYLOAD
        } ?: return
        // Der Datensatz wurde zurückgerollt, auch seine Spuren in selbstVorgemerkt.
        selbstVorgemerkt.clear()
        selbstVorgemerkt.putAll(vorher)
        db.withTransaction { problem(r, code, jetzt) }
    }

    /**
     * Offene lokale Änderung? Ausgenommen ist nur ein Eintrag, den die Zutaten-Zusammenführung selbst vorgemerkt hat
     * und dessen `queuedAt` unverändert ist; eine spätere Änderung hebt die Ausnahme auf.
     */
    private suspend fun offeneLokaleAenderung(r: SyncRecord, selbstVorgemerkt: Map<Pair<String, String>, Long>): Boolean {
        val vorgemerktUm = dao.outboxEintrag(r.type.wire, r.id)?.queuedAt ?: return false
        return selbstVorgemerkt[r.type.wire to r.id] != vorgemerktUm
    }

    /** Ist diese Revision (oder eine neuere) schon bekannt, und gibt es den Datensatz lokal? */
    private suspend fun bekannteRevision(r: SyncRecord): Boolean {
        val rev = r.rev ?: return false
        val bekannt = dao.revision(r.type.wire, r.id)?.rev ?: return false
        return rev <= bekannt && existiert(r.type, r.id)
    }

    private suspend fun problem(r: SyncRecord, code: String, jetzt: Long) {
        dao.upsertProblem(SyncProblemEntity(r.type.wire, r.id, code, jetzt))
    }

    private suspend fun lebendAnwenden(r: SyncRecord, jetzt: Long, selbstVorgemerkt: MutableMap<Pair<String, String>, Long>) {
        if (PayloadValidator.validate(r) != null) return problem(r, UNGUELTIGER_PAYLOAD, jetzt)
        if (PayloadValidator.references(r).any { (typ, id) -> !existiert(typ, id) }) return problem(r, FEHLENDER_VERWEIS, jetzt)
        val problemBehalten = when (r.type) {
            RecordType.INGREDIENT -> {
                zutatSchreiben(r, selbstVorgemerkt)
                false
            }
            RecordType.RECIPE -> rezeptSchreiben(r)
            else -> {
                Abbildung.anwenden(db, r)
                false
            }
        }
        dao.upsertRevision(SyncRevisionEntity(r.type.wire, r.id, r.rev ?: 0L))
        if (!problemBehalten) dao.loeschenProblem(r.type.wire, r.id)
    }

    /**
     * Schreibt ein Server-Rezept samt Fotoregel:
     * - ohne Foto: ein fremder Link (`content:`) bleibt, ein eigenes Foto wird entfernt – außer es ist als nicht
     *   übertragbar vermerkt (dann ging es nie zum Server und bleibt lokal);
     * - Foto lokal vorhanden (Hash bekannt): verknüpfen;
     * - Foto unbekannt: Link bleibt, Fotowunsch anlegen (der Abruf folgt später).
     * Gibt zurück, ob das Problem „Foto nicht übertragbar“ stehen bleibt (solange der Server-Stand kein Foto hat).
     */
    private suspend fun rezeptSchreiben(r: SyncRecord): Boolean {
        val p = RecordType.RECIPE.decode(requireNotNull(r.payload)) as RecipePayload
        val vorher = db.rezeptDao().nachId(r.id)
        val bekannt = p.photo?.let { index.link(it) }
        val nichtUebertragbar = hatProblem(r.id, FOTO_NICHT_UEBERTRAGBAR)
        val eigenesEntfernen = !nichtUebertragbar && vorher?.imageUri?.let(ablage::istEigeneDatei) == true
        val bild = when {
            p.photo == null -> if (eigenesEntfernen) null else vorher?.imageUri
            bekannt != null -> bekannt
            else -> vorher?.imageUri
        }
        Abbildung.anwenden(db, r) // übernimmt imageUri der vorhandenen Zeile
        if (bild != vorher?.imageUri) dao.rezeptFotoSetzen(r.id, bild)
        val gewuenscht = p.photo?.takeIf { bekannt == null }
        if (gewuenscht != null) dao.upsertFotoGewuenscht(SyncFotoGewuenschtEntity(r.id, gewuenscht)) else dao.loeschenFotoGewuenscht(r.id)
        return p.photo == null && bild != null && nichtUebertragbar
    }

    private suspend fun hatProblem(rezeptId: String, code: String) = dao.problem(RecordType.RECIPE.wire, rezeptId)?.code == code

    /**
     * Schreibt eine Server-Zutat. Gibt es lokal eine andere Zutat mit gleichem Namen (ohne Groß-/Kleinschreibung), wird
     * sie in die Server-Zutat zusammengeführt (Server-ID gewinnt, fehlende Angaben kommen aus der lokalen Zutat).
     * Der Namensindex ist eindeutig: ohne das scheiterte das Schreiben an einer Constraint-Verletzung.
     */
    private suspend fun zutatSchreiben(r: SyncRecord, selbstVorgemerkt: MutableMap<Pair<String, String>, Long>) {
        val name = (RecordType.INGREDIENT.decode(requireNotNull(r.payload)) as IngredientPayload).name
        // Erst exakt (passt zum eindeutigen Index), sonst ohne Beachtung der Groß-/Kleinschreibung
        val lokal = dao.zutatNachNameExakt(name)?.takeIf { it.id != r.id } ?: dao.zutatNachName(name)
        if (lokal == null || lokal.id == r.id) {
            Abbildung.anwenden(db, r)
            return
        }
        // Lokale Zutat vorübergehend umbenennen, dann die Server-Zutat schreiben
        val umbenannt = lokal.copy(canonicalName = lokal.canonicalName + "#" + lokal.id)
        db.zutatDao().upsert(umbenannt)
        Abbildung.anwenden(db, r)
        val server = requireNotNull(db.zutatDao().nachId(r.id))
        // Mit aktiven Triggern zusammenführen, damit umgehängte Rezepte, Artikel und Einträge in die Outbox kommen
        val vorher = dao.outbox().associate { (it.type to it.recordId) to it.queuedAt }
        dao.applyingRemoteSetzen(false)
        val geaendert = try {
            zusammenfuehrenSchritte(db, quelle = umbenannt, ziel = server, jetzt = uhr())
        } finally {
            dao.applyingRemoteSetzen(true)
        }
        // Als selbst vorgemerkt gilt nur, was vorher nicht in der Outbox stand oder schon selbst vorgemerkt war. Ein
        // Eintrag mit offener Nutzeränderung bekommt durch das Umhängen ein neues queuedAt, bleibt aber geschützt –
        // sonst überschriebe ein späterer Server-Stand derselben Seite die lokale Änderung (bewusst strenger als v1).
        for (e in dao.outbox()) {
            val schluessel = e.type to e.recordId
            val alt = vorher[schluessel]
            if (alt != e.queuedAt && (alt == null || selbstVorgemerkt[schluessel] == alt)) selbstVorgemerkt[schluessel] = e.queuedAt
        }
        dao.loeschenOutbox(RecordType.INGREDIENT.wire, lokal.id)
        // Hat die Zusammenführung die Server-Zutat nicht ergänzt, muss sie nicht zurückgesendet werden
        if (!geaendert) dao.loeschenOutbox(RecordType.INGREDIENT.wire, r.id)
    }

    private suspend fun loeschungAnwenden(r: SyncRecord) {
        val rev = SyncRevisionEntity(r.type.wire, r.id, r.rev ?: 0L)
        if (!existiert(r.type, r.id)) {
            dao.upsertRevision(rev)
            dao.loeschenProblem(r.type.wire, r.id)
            return
        }
        if (nochGebraucht(r.type, r.id)) {
            // Von Rezeptzeilen verwendet oder von einer Kaskade über offene lokale Änderungen betroffen: nicht löschen,
            // beim nächsten Push wiederbeleben (baseRev = rev)
            dao.upsertRevision(rev)
            dao.upsertOutbox(SyncOutboxEntity(r.type.wire, r.id, deleted = false, queuedAt = uhr()))
            return
        }
        loeschenLokal(r.type, r.id)
        dao.upsertRevision(rev)
        dao.loeschenProblem(r.type.wire, r.id)
    }

    /**
     * Würde das Löschen lokal noch gebrauchte Daten reißen (Fremdschlüssel RESTRICT oder Kaskade über offene
     * Änderungen)? Gilt für Server-Löschungen und den Voll-Abgleich.
     */
    private suspend fun nochGebraucht(typ: RecordType, id: String): Boolean = when (typ) {
        RecordType.INGREDIENT -> dao.zutatVerwendungen(id) > 0 || dao.wartendeArtikelZurZutat(id)
        RecordType.RECIPE -> dao.wartendePlanEintraegeZumRezept(id)
        RecordType.SHOPPING_LIST -> dao.wartendeArtikelDerListe(id)
        else -> false
    }

    /** Löscht den lokalen Datensatz (Kinder per Kaskade); `applyingRemote` setzt der Aufrufer. */
    private suspend fun loeschenLokal(typ: RecordType, id: String) {
        when (typ) {
            RecordType.INGREDIENT -> db.zutatDao().loeschen(id)
            RecordType.RECIPE -> {
                db.rezeptDao().loeschen(id)
                dao.loeschenFotoGewuenscht(id)
            }
            RecordType.MEAL_SLOT -> db.planEintragDao().loeschen(id)
            RecordType.SHOPPING_LIST -> db.einkaufslisteDao().loeschen(id)
            RecordType.SHOPPING_ITEM -> db.einkaufsartikelDao().loeschen(id)
            RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().loeschen(id)
        }
    }

    private suspend fun existiert(typ: RecordType, id: String): Boolean = when (typ) {
        RecordType.INGREDIENT -> db.zutatDao().nachId(id) != null
        RecordType.RECIPE -> db.rezeptDao().nachId(id) != null
        RecordType.MEAL_SLOT -> db.planEintragDao().nachId(id) != null
        RecordType.SHOPPING_LIST -> db.einkaufslisteDao().nachId(id) != null
        RecordType.SHOPPING_ITEM -> db.einkaufsartikelDao().nachId(id) != null
        RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().nachId(id) != null
    }

    // ---- Voll-Abgleich ----------------------------------------------------------------------------------------------

    /**
     * Voll-Abgleich nach `410` (Cursor abgelaufen): [serverDatensaetze] (alles ab 0) anwenden, danach lokale
     * Wurzeldatensätze löschen, die der Server nicht (mehr) kennt – ohne zu verlieren, was lokal noch nicht gesendet oder
     * abgelehnt ist. Es bleiben:
     * - Datensätze mit offener lokaler Änderung (Outbox) und vom Server abgelehnte (Problem vermerkt, die einzige Kopie);
     * - Ziele von Verweisen der Server-Datensätze (z. B. ein Rezept, auf das ein Plan-Eintrag zeigt) und noch gebrauchte
     *   (benutzte Zutaten, Listen mit wartenden Einträgen) – diese werden zum Senden vorgemerkt, damit beide Seiten
     *   wieder übereinstimmen.
     * Gelöscht wird mit `applyingRemote = 1`, Kinder vor Eltern. Der Cursor wird erst danach auf [cursor] gesetzt; bricht
     * es vorher ab, wird der Voll-Abgleich wiederholt.
     */
    suspend fun vollabgleich(serverDatensaetze: List<SyncRecord>, cursor: Long) {
        anwendenIntern(serverDatensaetze, neuerCursor = null, bekannteUeberspringen = true)
        val lebend = serverDatensaetze.filter { !it.deleted }
        val beimServer = lebend.mapTo(HashSet()) { it.type to it.id }
        // Unlesbare Datensätze hat das Anwenden schon als Problem vermerkt; hier überspringen.
        val verwiesen = lebend.flatMapTo(HashSet()) {
            try {
                PayloadValidator.references(it)
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }
        for (typ in LOESCH_REIHENFOLGE) {
            for (id in ids(typ)) {
                if ((typ to id) !in beimServer) veraltetEntfernen(typ, id, verwiesen)
            }
        }
        db.withTransaction {
            dao.upsertZustand((dao.zustand() ?: SyncZustandEntity()).copy(applyingRemote = false, cursor = cursor, lastSyncAt = uhr()))
        }
    }

    private suspend fun ids(typ: RecordType): List<String> = when (typ) {
        RecordType.INGREDIENT -> dao.zutatIds()
        RecordType.RECIPE -> dao.rezeptIds()
        RecordType.MEAL_SLOT -> dao.planEintragIds()
        RecordType.SHOPPING_LIST -> dao.einkaufslisteIds()
        RecordType.SHOPPING_ITEM -> dao.einkaufsartikelIds()
        RecordType.TAGEBUCH_EINTRAG -> dao.tagebuchIds()
    }

    private suspend fun veraltetEntfernen(typ: RecordType, id: String, verwiesen: Set<Pair<RecordType, String>>) {
        db.withTransaction {
            if (dao.outboxEintrag(typ.wire, id) != null) return@withTransaction
            if (dao.problem(typ.wire, id) != null) return@withTransaction
            if ((typ to id) in verwiesen || nochGebraucht(typ, id)) {
                dao.upsertOutbox(SyncOutboxEntity(typ.wire, id, deleted = false, queuedAt = uhr()))
                return@withTransaction
            }
            dao.upsertZustand((dao.zustand() ?: SyncZustandEntity()).copy(applyingRemote = true))
            try {
                loeschenLokal(typ, id)
            } finally {
                dao.applyingRemoteSetzen(false)
            }
            dao.loeschenProblem(typ.wire, id)
        }
    }

    // ---- Fotowünsche --------------------------------------------------------------------------------------------

    /**
     * Fotos, die Server-Rezepte brauchen und lokal fehlen, als (Rezept-ID, Hash). Verwaiste Wünsche werden entfernt,
     * Wünsche mit lokal schon vorhandener Datei sofort verknüpft; Rezepte mit dem Problem `photo_mismatch` werden nicht
     * erneut geladen (ein neuer Server-Stand löscht das Problem). Bei inaktivem Sync leer.
     */
    suspend fun fotowuensche(): List<Pair<String, String>> {
        if (!zustand().aktiv) return emptyList()
        val ergebnis = ArrayList<Pair<String, String>>()
        for (w in dao.fotosGewuenscht()) {
            if (db.rezeptDao().nachId(w.recipeId) == null) {
                dao.loeschenFotoGewuenscht(w.recipeId)
                continue
            }
            if (hatProblem(w.recipeId, FOTO_FALSCH)) continue
            val link = index.link(w.sha256)
            if (link != null) {
                verknuepfen(w.recipeId, w.sha256, link)
                continue
            }
            ergebnis += w.recipeId to w.sha256
        }
        return ergebnis
    }

    /**
     * Ein vom Server geladenes Foto für [rezeptId]. Passen die Bytes nicht zu [sha256], wird nichts abgelegt: Problem
     * `photo_mismatch`, der Wunsch bleibt (der Push schickt so weiter den Server-Hash). Sonst wird die Datei unverändert
     * abgelegt, im Hash-Zwischenspeicher gemerkt und verknüpft – nur, wenn der Wunsch unverändert noch besteht (ein lokal
     * neu gewähltes Foto löscht ihn). Das Verknüpfen ist keine lokale Änderung (keine Outbox).
     */
    suspend fun fotoErhalten(rezeptId: String, sha256: String, bytes: ByteArray) {
        if (PhotoHash.of(bytes) != sha256) {
            db.withTransaction { dao.upsertProblem(SyncProblemEntity(RecordType.RECIPE.wire, rezeptId, FOTO_FALSCH, uhr())) }
            return
        }
        val link = index.link(sha256) ?: ablage.ablegen(bytes).also { link ->
            val datei = requireNotNull(ablage.datei(link))
            dao.upsertFotoLokal(SyncFotoLokalEntity(link, sha256, datei.length(), datei.lastModified()))
        }
        verknuepfen(rezeptId, sha256, link)
    }

    /**
     * Verwirft den gemerkten Hash der eigenen Fotodatei [datei] (ihr Inhalt passt beim Hochladen nicht mehr dazu), damit
     * der nächste Paketbau sie neu hasht – auch wenn Größe und Änderungszeit gleich geblieben sind.
     */
    suspend fun fotoHashVerwerfen(datei: File) {
        val pfad = datei.absolutePath
        for (e in dao.fotosLokal()) {
            if (ablage.datei(e.uri)?.absolutePath == pfad) dao.loeschenFotoLokal(e.uri)
        }
    }

    private suspend fun verknuepfen(rezeptId: String, sha256: String, link: String) {
        db.withTransaction {
            if (dao.fotoGewuenscht(rezeptId)?.sha256 != sha256) return@withTransaction
            dao.upsertZustand((dao.zustand() ?: SyncZustandEntity()).copy(applyingRemote = true))
            try {
                dao.rezeptFotoSetzen(rezeptId, link)
            } finally {
                dao.applyingRemoteSetzen(false)
            }
            dao.loeschenFotoGewuenscht(rezeptId)
            if (hatProblem(rezeptId, FOTO_FALSCH)) dao.loeschenProblem(RecordType.RECIPE.wire, rezeptId)
        }
    }

    companion object {
        /** Problem-Code: das eigene Foto ist nicht übertragbar, das Rezept geht ohne Foto hinaus. */
        const val FOTO_NICHT_UEBERTRAGBAR = "photo_unsyncable"

        /** Problem-Code: geladene Foto-Bytes passen nicht zum Hash. */
        const val FOTO_FALSCH = "photo_mismatch"

        /** Problem-Code (vom Server oder beim Anwenden): ein Verweisziel fehlt. */
        const val FEHLENDER_VERWEIS = "missing_reference"

        /** Problem-Code beim Anwenden: der Server-Datensatz ist nicht lesbar. */
        const val UNGUELTIGER_PAYLOAD = "invalid_payload"

        /** Problem-Code beim Anwenden: Schreiben scheiterte an der Datenbank (Constraint). */
        const val ANWENDEN_FEHLGESCHLAGEN = "apply_failed"

        /** `{"records":[]}` – die Hülle des Push-Bodys. */
        private const val HUELLE_BYTES = 14L

        /** Kinder vor Eltern (umgekehrte Abhängigkeitsreihenfolge). */
        private val LOESCH_REIHENFOLGE = RecordType.entries.reversed()
    }
}
