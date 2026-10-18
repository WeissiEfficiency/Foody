package de.foody.core.sync

import android.util.Log
import androidx.room.withTransaction
import de.foody.api.protocol.LoginRequest
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.sync.SyncSpeicher
import de.foody.core.data.sync.SyncZustand
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Ergebnis einer Aktion für die Oberfläche; [Fehler.kennung] ist eine feste Kennung, nie Token, Passwort oder Body. */
sealed interface Ergebnis<out T> {
    data class Erfolg<T>(val wert: T) : Ergebnis<T>

    data class Fehler(val kennung: String) : Ergebnis<Nothing>
}

/** Was beim ersten Abgleich mit den lokalen Daten geschieht. */
enum class ErsterSync {
    /** Alle lokalen Daten zum Server senden, dann die des Servers holen. */
    ZUSAMMENFUEHREN,

    /** Lokale Daten verwerfen und alles vom Server holen. */
    ERSETZEN,
}

/**
 * Verbinden und Trennen des Syncs (wie `SyncAccountRepository` der v1, ohne Haushalte):
 * 1. [anmelden] prüft die Adresse und meldet das Gerät an. Adresse, Konto-ID und Token bleiben bis [aktivieren] **nur im
 *    Speicher**: Ein abgebrochener Dialog oder eine Anmeldung an einem anderen Server/Konto berührt weder das gespeicherte
 *    Token noch einen laufenden Sync (sonst ginge das neue Token mit der alten Adresse hinaus).
 * 2. [aktivieren] speichert das Token und schaltet den Sync ein (Cursor 0) – bei einer Wiederanmeldung am selben Server
 *    und Konto übernimmt es nur das neue Token – und stößt einen Lauf an.
 * 3. [trennen] meldet das Gerät beim Server ab (falls erreichbar) und leert Token und Sync-Tabellen; lokale Daten bleiben.
 *
 * Nach der Migration 9 → 10 ist `kontoId` `NULL` (Haushalts- ≠ Konto-ID): Eine Anmeldung an derselben Adresse gilt
 * dann als Wiederanmeldung – sie trägt die Konto-ID ein und ersetzt nur das Token; Cursor und ungesendete Outbox der v1
 * bleiben. Die Oberfläche (Phase 6) braucht dafür keine Wahl „Zusammenführen/Ersetzen“. Tokens und Passwörter werden nie geloggt oder gemeldet.
 */
@Singleton
class SyncKonto @Inject constructor(
    private val db: FoodyDatenbank,
    private val speicher: SyncSpeicher,
    private val ablage: FotoAblage,
    private val token: TokenSpeicher,
    private val fabrik: ZugangFabrik,
    private val ausloeser: SyncAusloeser,
    private val konfiguration: SyncKonfiguration,
) {
    /** Anmeldung bis zum Aktivieren; [token] nie in [toString] (Logs, Fehlermeldungen). */
    private class Anmeldung(val adresse: String, val kontoId: String, val token: String) {
        override fun toString() = "Anmeldung($adresse, $kontoId)"
    }

    @Volatile private var anmeldung: Anmeldung? = null

    /**
     * Meldet dieses Gerät als [geraet] beim Server unter [adresse] an und merkt sich das Token im Speicher (gespeichert
     * wird es erst von [aktivieren]). Fehler kommen als [Ergebnis.Fehler] mit [UNGUELTIGE_ADRESSE], [FALSCHE_ZUGANGSDATEN],
     * [ZU_VIELE_VERSUCHE] oder einer Kennung aus [ServerFehler] (z. B. `offline`, `server_zu_alt`).
     */
    suspend fun anmelden(adresse: String, benutzer: String, passwort: String, geraet: String): Ergebnis<Unit> {
        val geprueft = ServerAdresse.pruefen(adresse, konfiguration.lokalesHttpErlaubt) ?: return Ergebnis.Fehler(UNGUELTIGE_ADRESSE)
        val antwort = try {
            fabrik.fuer(geprueft).anmelden(LoginRequest(benutzer, passwort, geraet))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerFehler) {
            return Ergebnis.Fehler(
                when {
                    e is ServerFehler.AnmeldungAbgelaufen -> FALSCHE_ZUGANGSDATEN
                    e is ServerFehler.Abgelehnt && e.status == 429 -> ZU_VIELE_VERSUCHE
                    else -> e.kennung
                },
            )
        }
        anmeldung = Anmeldung(geprueft, antwort.kontoId, antwort.token)
        return Ergebnis.Erfolg(Unit)
    }

    /** Gibt es Rezepte, Plan-Einträge, Einkaufslisten oder Tagebuch-Einträge? Zutaten (Startzutaten) zählen nicht. */
    suspend fun hatLokaleDaten(): Boolean = withContext(Dispatchers.IO) {
        db.query(HAT_LOKALE_DATEN, null).use { it.moveToFirst() && it.getInt(0) != 0 }
    }

    /**
     * Schaltet den Sync für die letzte [anmelden]-Anmeldung ein (ohne sie: für die gespeicherte Verbindung) und stößt
     * einen Lauf an. Entscheidung, Token und Sync-Zustand laufen gemeinsam unter der `laufSperre`: Kein Lauf und kein
     * Trennen kommt dazwischen, kein Lauf sieht das neue Token mit der alten Adresse.
     * - **Wiederanmeldung** (Sync aktiv, gleiche Adresse – beide Seiten über [ServerAdresse.pruefen] vereinheitlicht – und
     *   gleiche Konto-ID oder noch keine, wie nach der Migration 9 → 10): nur das neue Token übernehmen, die Konto-ID
     *   eintragen und den Fehler löschen; Cursor, Outbox und Daten bleiben, [modus] wirkt nicht (sonst verwürfe
     *   „Ersetzen“ direkt nach dem Update die ungesendete Outbox der v1).
     * - [ErsterSync.ZUSAMMENFUEHREN]: alle lokalen Datensätze zum Senden vormerken; der Server nimmt Bekanntes an bzw.
     *   führt es zusammen.
     * - [ErsterSync.ERSETZEN]: Rezepte, Plan, Einkaufslisten und Tagebuch samt eigener Fotos verwerfen, ebenso offene
     *   Änderungen; der erste Lauf holt alles. Zutaten bleiben (Startzutaten; der Server führt gleichnamige zusammen, ein
     *   fehlendes Verweisziel reicht der Sync bei Bedarf nach).
     *
     * Liefert [TOKEN_NICHT_GESPEICHERT], wenn das Token nicht abgelegt werden kann. Sync-Zustand und Daten bleiben dann
     * unverändert, die Anmeldung bleibt für einen neuen Versuch. Das alte Token nicht unbedingt: Der [TokenSpeicher]
     * verwirft beim Fehlschlag den Keystore-Schlüssel und versucht es mit einem neuen; scheitert auch das, ist das alte
     * Token unlesbar. Es wird dann zurückzuschreiben versucht; gelingt auch das nicht (Keystore dauerhaft gestört), meldet
     * der nächste Lauf „Anmeldung abgelaufen“.
     *
     * @throws IllegalStateException ohne Anmeldung und ohne gespeicherte Verbindung.
     */
    suspend fun aktivieren(modus: ErsterSync): Ergebnis<Unit> {
        val ergebnis = speicher.laufSperre.withLock {
            val neu = anmeldung
            val z = speicher.zustand()
            when {
                neu == null -> {
                    val adresse = z.serverUrl
                    val kontoId = z.kontoId
                    check(adresse != null && kontoId != null && token.laden() != null) { "Nicht angemeldet" }
                    einschalten(adresse, kontoId, modus, neuesToken = null)
                }
                wiederanmeldung(z, neu) -> {
                    val altesToken = token.laden()
                    if (tokenSpeichern(neu.token)) {
                        val dao = db.syncDao()
                        db.withTransaction {
                            val aktuell = dao.zustand() ?: SyncZustandEntity()
                            dao.upsertZustand(aktuell.copy(serverUrl = neu.adresse, kontoId = neu.kontoId, lastError = null))
                        }
                        Ergebnis.Erfolg(Unit)
                    } else {
                        altesTokenZurueck(altesToken)
                        Ergebnis.Fehler(TOKEN_NICHT_GESPEICHERT)
                    }
                }
                else -> einschalten(neu.adresse, neu.kontoId, modus, neu.token)
            }
        }
        if (ergebnis is Ergebnis.Erfolg) {
            anmeldung = null
            ausloeser.anstossen()
        }
        return ergebnis
    }

    /** Aktiver Sync, dieselbe (vereinheitlichte) Adresse und dieselbe oder noch keine Konto-ID (nach der Migration). */
    private fun wiederanmeldung(z: SyncZustand, neu: Anmeldung): Boolean {
        val gespeichert = z.serverUrl?.let { ServerAdresse.pruefen(it, konfiguration.lokalesHttpErlaubt) }
        return z.aktiv && gespeichert != null && gespeichert == neu.adresse && (z.kontoId == null || z.kontoId == neu.kontoId)
    }

    /**
     * Speichert [neuesToken] (falls nicht `null`), verwirft bei [ErsterSync.ERSETZEN] das Lokale und schaltet den Sync für
     * [adresse]/[kontoId] ein. Nur unter der `laufSperre` aufrufen. Scheitert ein Schritt nach dem Speichern, kommt das
     * alte Token zurück.
     */
    private suspend fun einschalten(adresse: String, kontoId: String, modus: ErsterSync, neuesToken: String?): Ergebnis<Unit> {
        val altesToken = if (neuesToken != null) token.laden() else null
        if (neuesToken != null && !tokenSpeichern(neuesToken)) {
            altesTokenZurueck(altesToken)
            return Ergebnis.Fehler(TOKEN_NICHT_GESPEICHERT)
        }
        try {
            if (modus == ErsterSync.ERSETZEN) lokalesVerwerfen()
            speicher.aktivieren(adresse, kontoId, allesHochladen = modus == ErsterSync.ZUSAMMENFUEHREN)
        } catch (e: Throwable) {
            if (neuesToken != null) {
                withContext(NonCancellable) {
                    if (altesToken == null || !tokenSpeichern(altesToken)) token.loeschen()
                }
            }
            throw e
        }
        return Ergebnis.Erfolg(Unit)
    }

    /**
     * Nach einem gescheiterten Speichern: Der [TokenSpeicher] hat dabei womöglich den Schlüssel verworfen, das alte Token
     * wäre unlesbar. Es wird (bestmöglich) neu abgelegt.
     */
    private fun altesTokenZurueck(altesToken: String?) {
        if (altesToken != null && token.laden() == null) tokenSpeichern(altesToken)
    }

    /** `false`, wenn der Keystore das Token nicht annimmt; die Ausnahme könnte Einzelheiten tragen und bleibt hier. */
    private fun tokenSpeichern(wert: String): Boolean = try {
        token.speichern(wert)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Verwirft die lokalen Fachdaten außer Zutaten, offene Änderungen und Sync-Vermerke – ohne dass etwas davon als
     * Löschung zum Server geht (`applyingRemote = 1`; bei inaktivem Sync schreiben die Trigger ohnehin nichts). Danach
     * werden die nicht mehr benutzten eigenen Fotos entfernt. Nur unter der `laufSperre` aufrufen.
     */
    private suspend fun lokalesVerwerfen() {
        val dao = db.syncDao()
        db.withTransaction {
            dao.applyingRemoteSetzen(true)
            db.tagebuchDao().alleLoeschen()
            db.einkaufsartikelQuelleDao().alleLoeschen()
            db.einkaufsartikelDao().alleLoeschen()
            db.einkaufslisteDao().alleLoeschen()
            db.planEintragDao().alleLoeschen()
            db.arbeitsschrittDao().alleLoeschen()
            db.rezeptZutatDao().alleLoeschen()
            db.rezeptDao().alleLoeschen()
            dao.outboxLeeren()
            dao.revisionenLeeren()
            dao.problemeLeeren()
            dao.fotowuenscheLeeren()
            dao.applyingRemoteSetzen(false)
        }
        try {
            val benutzt = db.rezeptDao().alle().mapNotNull { it.imageUri }
            for (link in ablage.unbenutzteEntfernen(benutzt)) dao.loeschenFotoLokal(link)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nur Aufräumen: übrig gebliebene Dateien stören nicht, der nächste Import oder das nächste Ersetzen räumt auf.
            Log.w(TAG, "Fotos nach dem Ersetzen nicht vollständig entfernt")
        }
    }

    /**
     * Trennt dieses Gerät: meldet es zuerst mit dem gespeicherten Token bei der gespeicherten Adresse ab, falls erreichbar
     * (höchstens [ABMELDEN_MS], Fehler werden ignoriert), dann – auch bei Abbruch – Token löschen, Aufträge abbrechen und
     * erst danach auf die `laufSperre` warten und den Sync lokal ausschalten (Outbox, Revisionen, Probleme, Fotowünsche
     * leeren). Eine nicht aktivierte Anmeldung wird nur vergessen. Lokale Daten bleiben.
     */
    suspend fun trennen() {
        try {
            val adresse = speicher.zustand().serverUrl
            if (adresse != null && token.laden() != null) {
                withTimeout(ABMELDEN_MS) { fabrik.fuer(adresse).abmelden() }
            }
        } catch (_: TimeoutCancellationException) {
            // Server antwortet nicht rechtzeitig: lokal trotzdem trennen.
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline, Token schon ungültig o. Ä.: lokal trotzdem trennen.
        } finally {
            withContext(NonCancellable) {
                anmeldung = null
                token.loeschen()
                // Erst die Aufträge abbrechen: deaktivieren wartet auf einen laufenden Lauf.
                ausloeser.abbrechen()
                speicher.deaktivieren()
            }
        }
    }

    companion object {
        /** Die Adresse ist nicht erlaubt (kein `https`, oder `http` außerhalb des Debug-Builds). */
        const val UNGUELTIGE_ADRESSE = "ungueltige_adresse"

        /** Benutzername oder Passwort falsch. */
        const val FALSCHE_ZUGANGSDATEN = "falsche_zugangsdaten"

        /** Der Server drosselt Anmeldeversuche (`429`). */
        const val ZU_VIELE_VERSUCHE = "zu_viele_versuche"

        /** Das Token ließ sich nicht sicher ablegen (Keystore). */
        const val TOKEN_NICHT_GESPEICHERT = "token_nicht_gespeichert"

        private const val ABMELDEN_MS = 8_000L
        private const val TAG = "SyncKonto"

        private const val HAT_LOKALE_DATEN =
            "SELECT EXISTS(SELECT 1 FROM recipe) OR EXISTS(SELECT 1 FROM meal_slot) OR EXISTS(SELECT 1 FROM shopping_list) " +
                "OR EXISTS(SELECT 1 FROM shopping_item) OR EXISTS(SELECT 1 FROM tagebuch_eintrag)"
    }
}
