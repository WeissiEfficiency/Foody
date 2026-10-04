package de.foody.app.ui.sync

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.BuildConfig
import de.foody.app.R
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.BackupRepository
import de.foody.app.di.AppScope
import de.foody.app.sync.ErrorContext
import de.foody.app.sync.FirstSync
import de.foody.app.sync.ServerUrl
import de.foody.app.sync.SyncAccountRepository
import de.foody.app.sync.SyncAccounts
import de.foody.app.sync.SyncApiException
import de.foody.app.sync.SyncErrors
import de.foody.app.util.runSuspendCatching
import de.foody.sync.protocol.HouseholdDto
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sicherung und Löschen der lokalen Daten für „Dieses Gerät ersetzen“. Schnittstelle, damit ViewModel-Tests
 * ohne Dateizugriff auskommen; die einzige Implementierung ist [BackupSetupAdapter] (Bindung in `SyncModule`).
 */
interface SetupBackup {
    suspend fun export(uri: Uri)
    suspend fun deleteAll()
}

/** Reicht [SetupBackup] an das vorhandene [BackupRepository] durch. */
class BackupSetupAdapter @Inject constructor(private val backup: BackupRepository) : SetupBackup {
    override suspend fun export(uri: Uri) = backup.export(uri)
    override suspend fun deleteAll() = backup.deleteAll()
}

/** Schritte des Verbinden-Assistenten. */
enum class SetupStep { URL, ACCOUNT, HOUSEHOLD, FIRST_SYNC, DONE }

/**
 * Zustand des Bildschirms. [password] lebt nur im Speicher des ViewModels (nie im `SavedStateHandle`) und wird
 * nach dem Absenden geleert; [inviteCode] ebenso.
 */
data class SyncSetupState(
    val step: SetupStep = SetupStep.URL,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val inviteCode: String = "",
    val register: Boolean = false,
    val households: List<HouseholdDto> = emptyList(),
    val newHouseholdName: String = "",
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    val reconnect: Boolean = false,
)

/**
 * Assistent „Server verbinden“: URL → Konto (anmelden oder mit Einladungscode registrieren) → Haushalt (nur wenn
 * der Login keinen liefert) → erster Abgleich (nur beim Beitritt zu einem bestehenden Haushalt mit lokalen
 * Daten) → fertig. Schritt, Adresse, Benutzername und Modus überleben einen Prozessneustart über das
 * [SavedStateHandle]; Passwörter nicht.
 *
 * Aktiv wird der Sync erst am Ende (`activate`). Verlässt der Nutzer den Assistenten nach dem Anmelden, aber vor
 * dem Aktivieren, meldet [cancel] das gerade angelegte Gerät beim Server (mit der Adresse des Assistenten) und lokal
 * wieder ab. Beim erneuten Verbinden verwirft [cancel] (und jeder Fehler vor dem Aktivieren) nur das Token: Outbox,
 * Haushalt und der Status „Abgemeldet“ bleiben, `disconnect` würde sie verwerfen.
 * Restrisiko: Wird der Prozess mitten im Verbinden beendet, bleibt ein angemeldetes, aber nie aktiviertes Gerät
 * in der Geräteliste des Servers, bis es dort abgemeldet wird.
 */
@HiltViewModel
class SyncSetupViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repo: SyncAccounts,
    private val db: FoodyDatabase,
    private val backup: SetupBackup,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val reconnect = savedState.get<Boolean>(ARG_RECONNECT) == true

    private val _state = MutableStateFlow(
        SyncSetupState(
            step = savedState.get<String>(KEY_STEP)?.let { runCatching { SetupStep.valueOf(it) }.getOrNull() } ?: SetupStep.URL,
            url = savedState[KEY_URL] ?: "",
            username = savedState[KEY_USERNAME] ?: "",
            register = savedState[KEY_REGISTER] ?: false,
            reconnect = reconnect,
        ),
    )
    val state: StateFlow<SyncSetupState> = _state.asStateFlow()

    private val _offerBackup = MutableStateFlow(false)

    /** `true`, solange die Oberfläche den Speicherdialog für die Sicherung öffnen soll (danach [offerBackupHandled]). */
    val offerBackup: StateFlow<Boolean> = _offerBackup.asStateFlow()

    init {
        // Nach Prozessneustart in einem späteren Schritt: Token liegt im TokenStore, Haushaltsliste neu laden.
        if (_state.value.step == SetupStep.HOUSEHOLD) loadHouseholds()
        if (reconnect && _state.value.url.isEmpty()) {
            viewModelScope.launch {
                val stored = db.syncDao().getState()?.serverUrl
                if (stored != null && _state.value.url.isEmpty()) setUrl(stored)
            }
        }
    }

    private fun update(block: (SyncSetupState) -> SyncSetupState) = _state.update(block)

    private fun setStep(step: SetupStep) {
        savedState[KEY_STEP] = step.name
        update { it.copy(step = step, error = null) }
    }

    fun setUrl(value: String) {
        savedState[KEY_URL] = value
        update { it.copy(url = value) }
    }

    fun setUsername(value: String) {
        savedState[KEY_USERNAME] = value
        update { it.copy(username = value) }
    }

    fun setPassword(value: String) = update { it.copy(password = value) }

    fun setInviteCode(value: String) = update { it.copy(inviteCode = value) }

    fun setRegister(value: Boolean) {
        savedState[KEY_REGISTER] = value
        update { it.copy(register = value, error = null) }
    }

    fun setNewHouseholdName(value: String) = update { it.copy(newHouseholdName = value) }

    /** Prüft die Adresse (ohne Netzwerkzugriff) und geht zum Konto-Schritt. */
    fun submitUrl() {
        val normalized = ServerUrl.normalize(_state.value.url, BuildConfig.DEBUG)
        if (normalized == null) {
            update { it.copy(error = R.string.sync_error_invalid_url) }
            return
        }
        setUrl(normalized)
        setStep(SetupStep.ACCOUNT)
    }

    /** Vom Konto-Schritt zurück zur Adresse (nur solange noch nicht angemeldet); `false` = nicht möglich. */
    fun backToUrl(): Boolean {
        if (savedState.get<Boolean>(KEY_LOGGED_IN) == true) return false
        setStep(SetupStep.URL)
        return true
    }

    /** Anmelden oder Registrieren; danach je nach Ergebnis Haushalt wählen, erster Abgleich oder Fertig. */
    fun submitAccount() = launchBusy(if (_state.value.register) ErrorContext.REGISTER else ErrorContext.LOGIN) {
        val s = _state.value
        val deviceName = SyncAccountRepository.defaultDeviceName()
        val result = if (s.register) {
            repo.register(s.url, s.inviteCode.trim(), s.username.trim(), s.password, deviceName)
        } else {
            repo.login(s.url, s.username.trim(), s.password, deviceName)
        }
        savedState[KEY_LOGGED_IN] = true
        update { it.copy(password = "", inviteCode = "") }
        try {
            runAfterLogin(result.householdId)
        } catch (e: Throwable) {
            // Erneut verbinden: Scheitert etwas vor dem Aktivieren, bleibt kein Token zurück (Sync-Zustand und Outbox bleiben).
            if (reconnect) discardReconnectToken()
            throw e
        }
    }

    private suspend fun discardReconnectToken() {
        savedState[KEY_LOGGED_IN] = false
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { repo.discardToken() }
    }

    private suspend fun runAfterLogin(householdId: String?) {
        val url = _state.value.url
        val previous = if (reconnect) db.syncDao().getState()?.householdId else null
        when {
            // Erneut verbinden: den bisherigen Haushalt behalten und alles Vorgemerkte senden; keine Rückfrage.
            previous != null -> {
                if (householdId != previous) repo.selectHousehold(url, previous)
                activate(previous, FirstSync.UPLOAD_ALL)
            }
            householdId != null -> joinExisting(householdId)
            else -> {
                val list = repo.households(url)
                update { it.copy(households = list) }
                setStep(SetupStep.HOUSEHOLD)
            }
        }
    }

    private fun loadHouseholds() = launchBusy(ErrorContext.GENERAL) {
        val list = repo.households(_state.value.url)
        update { it.copy(households = list) }
    }

    /** Einem vorhandenen Haushalt beitreten (Auswahl in der Liste). */
    fun selectHousehold(id: String) = launchBusy(ErrorContext.GENERAL) {
        repo.selectHousehold(_state.value.url, id)
        joinExisting(id)
    }

    /** Neuen Haushalt anlegen: alle lokalen Daten werden ohne Rückfrage hochgeladen. */
    fun createHousehold() = launchBusy(ErrorContext.GENERAL) {
        val name = _state.value.newHouseholdName.trim()
        val household = repo.createHousehold(_state.value.url, name)
        activate(household.id, FirstSync.UPLOAD_ALL)
    }

    /** Beitritt zu einem bestehenden Haushalt: ohne lokale Daten nur herunterladen, sonst nachfragen. */
    private suspend fun joinExisting(householdId: String) {
        savedState[KEY_HOUSEHOLD] = householdId
        if (repo.hasLocalData()) setStep(SetupStep.FIRST_SYNC) else activate(householdId, FirstSync.DOWNLOAD_ONLY)
    }

    /** „Zusammenführen“: alle lokalen Daten hochladen. */
    fun chooseMerge() = launchBusy(ErrorContext.GENERAL) {
        activate(requireNotNull(savedState.get<String>(KEY_HOUSEHOLD)), FirstSync.UPLOAD_ALL)
    }

    /** „Dieses Gerät ersetzen“: erst die Sicherung anbieten; gelöscht wird erst nach deren Erfolg. */
    fun chooseReplace() {
        update { it.copy(error = null) }
        _offerBackup.value = true
    }

    fun offerBackupHandled() {
        _offerBackup.value = false
    }

    /** Der Speicherdialog wurde abgebrochen: nichts wird gelöscht, der Nutzer bleibt auf dem Schritt. */
    fun onBackupCancelled() {
        _offerBackup.value = false
        update { it.copy(error = R.string.sync_setup_backup_cancelled) }
    }

    /**
     * Schreibt die Sicherung nach [uri]; nur wenn das gelingt, werden die lokalen Daten gelöscht und danach nur
     * heruntergeladen. Schlägt die Sicherung fehl, bleibt alles unverändert.
     */
    fun onBackupChosen(uri: Uri) {
        _offerBackup.value = false
        if (_state.value.busy) return
        val householdId = savedState.get<String>(KEY_HOUSEHOLD) ?: return
        viewModelScope.launch {
            update { it.copy(busy = true, error = null) }
            if (runSuspendCatching { backup.export(uri) }.isFailure) {
                update { it.copy(busy = false, error = R.string.sync_setup_backup_failed) }
                return@launch
            }
            runSuspendCatching {
                backup.deleteAll()
                activate(householdId, FirstSync.DOWNLOAD_ONLY)
            }.onFailure { e ->
                update { it.copy(error = if (e is SyncApiException) SyncErrors.messageFor(e, ErrorContext.GENERAL) else R.string.sync_setup_replace_failed) }
            }
            update { it.copy(busy = false) }
        }
    }

    private suspend fun activate(householdId: String, mode: FirstSync) {
        repo.activate(_state.value.url, householdId, mode)
        // Ab hier ist der Sync aktiv: ein späteres Zurück darf das Gerät nicht mehr abmelden.
        savedState[KEY_LOGGED_IN] = false
        setStep(SetupStep.DONE)
    }

    /**
     * Vom Bildschirm bei „Zurück“ aufgerufen. Nach erfolgreichem Anmelden, aber vor dem Aktivieren wird das neu
     * angelegte Gerät abgemeldet (Token weg, Gerät beim Server widerrufen). Beim erneuten Verbinden nie.
     */
    fun cancel() {
        if (savedState.get<Boolean>(KEY_LOGGED_IN) != true || _state.value.step == SetupStep.DONE) return
        savedState[KEY_LOGGED_IN] = false
        val url = _state.value.url
        // App-Scope: Der ViewModel-Scope endet gleich mit dem Verlassen des Bildschirms.
        appScope.launch {
            // Erneut verbinden: nur das Token verwerfen, damit Outbox, Haushalt und „Abgemeldet“-Status bleiben.
            runSuspendCatching { if (reconnect) repo.discardToken() else repo.disconnect(url) }
        }
    }

    private fun launchBusy(context: ErrorContext, block: suspend () -> Unit) {
        if (_state.value.busy) return
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runSuspendCatching { block() }.onFailure { e ->
                update { it.copy(error = SyncErrors.messageFor(e, context)) }
            }
            update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val ARG_RECONNECT = "reconnect"
        const val KEY_STEP = "step"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_REGISTER = "register"
        const val KEY_HOUSEHOLD = "householdId"
        const val KEY_LOGGED_IN = "loggedIn"
    }
}
