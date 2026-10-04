package de.foody.app.sync

import android.os.Build
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.di.AppScope
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.RegisterRequest
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Ergebnis von Anmelden/Registrieren; [householdId] ist `null`, solange das Gerät keinem Haushalt zugeordnet ist. */
data class LoginResult(val householdId: String?)

/** Was beim ersten Abgleich mit den lokalen Daten geschieht. */
enum class FirstSync {
    /** Alle lokalen Daten zum Server hochladen (und die des Servers übernehmen). */
    UPLOAD_ALL,

    /** Nur vom Server herunterladen; lokal vorhandene Daten werden nicht hochgeladen. */
    DOWNLOAD_ONLY,
}

/**
 * Alle Konto-Funktionen des Syncs für die Oberfläche: Anmelden/Registrieren, Haushalt, Einladen, Geräte,
 * Aktivieren und Trennen.
 *
 * Vor dem Aktivieren ist die Server-Adresse noch nicht gespeichert, deshalb bekommen Login, Registrierung und
 * Haushaltsaufrufe die [url] mit und bauen je Aufruf eine [KtorSyncApi]; alle übrigen Aufrufe nutzen die
 * gespeicherte Adresse aus `sync_state`. Das Token liest die API je Aufruf aus dem [TokenStore]. Der
 * [httpClient] ist der Singleton der [SyncEngineFactory] (siehe `SyncModule`). Login und Registrierung
 * speichern nur das Token; **aktiv** wird der Sync erst mit [activate], damit eine falsche Adresse oder ein
 * abgebrochener Einrichtungsdialog nie einen Hintergrund-Sync auslöst. Tokens und Passwörter werden nie geloggt.
 */
@Singleton
class SyncAccountRepository @Inject constructor(
    private val db: FoodyDatabase,
    private val tokenStore: TokenStore,
    private val localStore: SyncLocalStore,
    private val scheduler: SyncScheduler,
    private val httpClient: HttpClient,
    @AppScope private val appScope: CoroutineScope,
) {
    private fun api(url: String) = KtorSyncApi(url, httpClient) { tokenStore.load() }

    /** API für die gespeicherte Adresse (Sync eingerichtet). */
    private suspend fun storedApi(): KtorSyncApi {
        val url = db.syncDao().getState()?.serverUrl ?: error("Kein Server verbunden")
        return api(url)
    }

    suspend fun login(url: String, username: String, password: String, deviceName: String): LoginResult {
        val auth = api(url).login(LoginRequest(username, password, deviceName))
        tokenStore.save(auth.token)
        return LoginResult(auth.householdId)
    }

    suspend fun register(url: String, code: String, username: String, password: String, deviceName: String): LoginResult {
        val auth = api(url).register(RegisterRequest(code, username, password, deviceName))
        tokenStore.save(auth.token)
        return LoginResult(auth.householdId)
    }

    suspend fun households(url: String): List<HouseholdDto> = api(url).households()

    suspend fun createHousehold(url: String, name: String): HouseholdDto = api(url).createHousehold(name)

    suspend fun selectHousehold(url: String, id: String) = api(url).selectHousehold(id)

    /** Gibt es Rezepte, Planpositionen, Vorrat oder Einkaufslisten? Mitgelieferte Startzutaten zählen nicht. */
    suspend fun hasLocalData(): Boolean = db.syncDao().hasUserData()

    /**
     * Schaltet den Sync ein und stößt den ersten Abgleich an. Auch für „Erneut verbinden“ nutzbar (dann sind
     * Adresse und Haushalt schon gespeichert): [FirstSync.UPLOAD_ALL] merkt erneut alles zum Senden vor, der
     * Server nimmt Bekanntes unverändert an bzw. führt es zusammen.
     */
    suspend fun activate(url: String, householdId: String, mode: FirstSync) {
        localStore.activate(url, householdId, uploadExisting = mode == FirstSync.UPLOAD_ALL)
        scheduler.schedulePeriodic()
        scheduler.startObservingOutbox(appScope)
        scheduler.requestSoon()
    }

    suspend fun createInvite(): InviteDto = storedApi().createInvite()

    suspend fun devices(): List<DeviceDto> = storedApi().devices()

    suspend fun revokeDevice(id: String) = storedApi().revokeDevice(id)

    /**
     * Trennt dieses Gerät: Meldet es best effort beim Server ab (Fehler und Zeitüberschreitung werden ignoriert,
     * auch offline), löscht das Token, schaltet den Sync lokal aus und beendet die Planung. Lokale Daten bleiben.
     */
    suspend fun disconnect() {
        try {
            val api = db.syncDao().getState()?.serverUrl?.let { api(it) }
            if (api != null && tokenStore.load() != null) {
                try {
                    withTimeout(REVOKE_TIMEOUT_MS) {
                        api.devices().firstOrNull { it.current }?.let { api.revokeDevice(it.id) }
                    }
                } catch (_: TimeoutCancellationException) {
                    // Server antwortet nicht rechtzeitig: lokal trotzdem trennen.
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: Server nicht erreichbar, Token schon ungültig o. Ä.
        } finally {
            tokenStore.clear()
            localStore.deactivate()
            scheduler.cancelAll()
        }
    }

    companion object {
        private const val REVOKE_TIMEOUT_MS = 8_000L
        private const val MAX_DEVICE_NAME = 64

        /** Name dieses Geräts für die Geräteliste des Servers, z. B. „Google Pixel 8“ (höchstens 64 Zeichen). */
        fun defaultDeviceName(): String =
            "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(MAX_DEVICE_NAME).ifBlank { "Android" }
    }
}
