package de.foody.core.sync

import android.util.Log
import androidx.room.withTransaction
import de.foody.api.protocol.LoginRequest
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.sync.SyncSpeicher
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
 * 1. [anmelden] prüft die Adresse, meldet das Gerät an und speichert nur das Token – **aktiv** wird der Sync erst mit
 *    [aktivieren], damit eine falsche Adresse oder ein abgebrochener Dialog nie einen Hintergrund-Sync auslöst.
 * 2. [aktivieren] schaltet den Sync ein (Cursor 0) und stößt den ersten Lauf an.
 * 3. [trennen] meldet das Gerät beim Server ab (falls erreichbar) und leert Token und Sync-Tabellen; lokale Daten bleiben.
 *
 * Adresse und Konto-ID der Anmeldung bleiben bis zum Aktivieren nur im Speicher (die Datenbank kennt sie erst aktiv).
 * Tokens und Passwörter werden nie geloggt oder gemeldet.
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
    private data class Anmeldung(val adresse: String, val kontoId: String)

    @Volatile private var anmeldung: Anmeldung? = null

    /**
     * Meldet dieses Gerät als [geraet] beim Server unter [adresse] an und speichert das Token. Fehler kommen als
     * [Ergebnis.Fehler] mit [UNGUELTIGE_ADRESSE], [FALSCHE_ZUGANGSDATEN], [ZU_VIELE_VERSUCHE], [TOKEN_NICHT_GESPEICHERT]
     * oder einer Kennung aus [ServerFehler] (z. B. `offline`, `server_zu_alt`).
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
        try {
            token.speichern(antwort.token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keystore gestört o. Ä.; die Ausnahme könnte Einzelheiten tragen – nur die Kennung geht hinaus.
            return Ergebnis.Fehler(TOKEN_NICHT_GESPEICHERT)
        }
        anmeldung = Anmeldung(geprueft, antwort.kontoId)
        return Ergebnis.Erfolg(Unit)
    }

    /** Gibt es Rezepte, Plan-Einträge, Einkaufslisten oder Tagebuch-Einträge? Zutaten (Startzutaten) zählen nicht. */
    suspend fun hatLokaleDaten(): Boolean = withContext(Dispatchers.IO) {
        db.query(HAT_LOKALE_DATEN, null).use { it.moveToFirst() && it.getInt(0) != 0 }
    }

    /**
     * Schaltet den Sync für die letzte [anmelden]-Anmeldung ein (ohne sie: für die gespeicherte Verbindung, etwa nach
     * „Anmeldung abgelaufen“) und stößt den ersten Lauf an.
     * - [ErsterSync.ZUSAMMENFUEHREN]: alle lokalen Datensätze zum Senden vormerken; der Server nimmt Bekanntes an bzw.
     *   führt es zusammen.
     * - [ErsterSync.ERSETZEN]: Rezepte, Plan, Einkaufslisten und Tagebuch samt eigener Fotos verwerfen, ebenso offene
     *   Änderungen; der erste Lauf holt alles. Zutaten bleiben (Startzutaten; der Server führt gleichnamige zusammen, ein
     *   fehlendes Verweisziel reicht der Sync bei Bedarf nach).
     *
     * @throws IllegalStateException ohne Anmeldung.
     */
    suspend fun aktivieren(modus: ErsterSync) {
        val ziel = anmeldung ?: speicher.zustand().let { z ->
            val adresse = z.serverUrl
            val kontoId = z.kontoId
            if (adresse != null && kontoId != null && token.laden() != null) Anmeldung(adresse, kontoId) else null
        }
        checkNotNull(ziel) { "Nicht angemeldet" }
        if (modus == ErsterSync.ERSETZEN) lokalesVerwerfen()
        speicher.aktivieren(ziel.adresse, ziel.kontoId, allesHochladen = modus == ErsterSync.ZUSAMMENFUEHREN)
        anmeldung = null
        ausloeser.anstossen()
    }

    /**
     * Verwirft die lokalen Fachdaten außer Zutaten, offene Änderungen und Sync-Vermerke – ohne dass etwas davon als
     * Löschung zum Server geht (`applyingRemote = 1`; bei inaktivem Sync schreiben die Trigger ohnehin nichts). Wartet auf
     * einen laufenden Abgleich. Danach werden die nicht mehr benutzten eigenen Fotos entfernt.
     */
    private suspend fun lokalesVerwerfen() {
        val dao = db.syncDao()
        speicher.laufSperre.withLock {
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
     * Trennt dieses Gerät: meldet es beim Server ab, falls erreichbar (höchstens [ABMELDEN_MS], Fehler werden ignoriert),
     * dann – auch bei Abbruch – Token löschen, Aufträge abbrechen und den Sync lokal ausschalten (Outbox, Revisionen,
     * Probleme, Fotowünsche leeren; wartet auf einen laufenden Lauf). Lokale Daten bleiben.
     */
    suspend fun trennen() {
        try {
            val adresse = speicher.zustand().serverUrl ?: anmeldung?.adresse
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
