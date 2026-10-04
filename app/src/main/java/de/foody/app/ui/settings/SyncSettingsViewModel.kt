package de.foody.app.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.sync.ErrorContext
import de.foody.app.sync.SyncAccounts
import de.foody.app.sync.SyncErrors
import de.foody.app.sync.SyncScheduler
import de.foody.app.util.runSuspendCatching
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.InviteDto
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ein Eintrag der Problemliste: [title] ist der Rezeptname (nur bei Rezepten), [type] die Art des Datensatzes. */
data class SyncProblemItem(
    @StringRes val type: Int,
    val title: String?,
    @StringRes val reason: Int,
)

/**
 * Zustand der Karte „Synchronisierung“. [connected] = Sync eingerichtet; [unauthorized] = Token widerrufen
 * (dann zeigt die Karte „Erneut verbinden“). [invite], [devices] und [problems] sind `null`, solange der jeweilige
 * Dialog zu ist. Enthält nie Token oder Passwörter.
 */
data class SyncSettingsState(
    val connected: Boolean = false,
    val serverUrl: String? = null,
    val householdName: String? = null,
    val lastSyncAt: Long? = null,
    val unauthorized: Boolean = false,
    val problemCount: Int = 0,
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    val invite: InviteDto? = null,
    val devices: List<DeviceDto>? = null,
    val problems: List<SyncProblemItem>? = null,
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val db: FoodyDatabase,
    private val repo: SyncAccounts,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val dao get() = db.syncDao()

    /** Teile des Zustands, die nicht aus der Datenbank kommen. */
    private data class Local(
        val busy: Boolean = false,
        val message: Int? = null,
        val invite: InviteDto? = null,
        val devices: List<DeviceDto>? = null,
        val problems: List<SyncProblemItem>? = null,
    )

    private val local = MutableStateFlow(Local())
    private val householdName = MutableStateFlow<String?>(null)

    init {
        // Haushaltsname je (Adresse, Haushalt) einmal holen; Fehler -> kein Name, die Karte zeigt nur „Verbunden“.
        viewModelScope.launch {
            dao.observeState()
                .map { s ->
                    if (s?.active == true && s.lastError != UNAUTHORIZED && s.serverUrl != null) s.serverUrl to s.householdId else null
                }
                .distinctUntilChanged()
                .collectLatest { key ->
                    householdName.value = null
                    if (key != null) {
                        householdName.value = runSuspendCatching { repo.households(key.first) }.getOrNull()
                            ?.firstOrNull { it.id == key.second }?.name
                    }
                }
        }
    }

    val state: StateFlow<SyncSettingsState> = combine(
        dao.observeState(), dao.observeProblemCount(), householdName, local,
    ) { stored, problems, name, l ->
        val s = stored?.takeIf { it.active }
        SyncSettingsState(
            connected = s != null,
            serverUrl = s?.serverUrl,
            householdName = name.takeIf { s != null },
            lastSyncAt = s?.lastSyncAt,
            unauthorized = s?.lastError == UNAUTHORIZED,
            problemCount = if (s != null) problems else 0,
            busy = l.busy,
            message = l.message,
            invite = l.invite,
            devices = l.devices,
            problems = l.problems,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettingsState())

    fun messageShown() = local.update { it.copy(message = null) }

    /** Gleicher Weg wie der Hintergrund-Sync; der Mutex der Engine schützt vor Überschneidung. */
    fun syncNow() {
        scheduler.requestSoon()
        local.update { it.copy(message = R.string.sync_now_started) }
    }

    fun loadDevices() = launchBusy { local.update { l -> l.copy(devices = repo.devices()) } }

    fun dismissDevices() = local.update { it.copy(devices = null) }

    fun revokeDevice(id: String) = launchBusy {
        repo.revokeDevice(id)
        local.update { l -> l.copy(devices = repo.devices(), message = R.string.sync_device_revoked) }
    }

    fun createInvite() = launchBusy { local.update { l -> l.copy(invite = repo.createInvite()) } }

    fun dismissInvite() = local.update { it.copy(invite = null) }

    fun disconnect() = launchBusy {
        repo.disconnect()
        local.update { Local(message = R.string.sync_disconnected) }
    }

    /** Löst die Probleme mit Rezeptnamen und Gründen auf und zeigt sie im Dialog. */
    fun loadProblems() = viewModelScope.launch {
        val items = dao.problems().sortedBy { it.at }.map { p ->
            SyncProblemItem(
                type = typeLabel(p.type),
                title = if (p.type == "recipe") db.recipeDao().get(p.recordId)?.name else null,
                reason = reasonLabel(p.code),
            )
        }
        local.update { it.copy(problems = items) }
    }

    fun dismissProblems() = local.update { it.copy(problems = null) }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        local.update { it.copy(busy = true) }
        runSuspendCatching { block() }.onFailure { e ->
            local.update { it.copy(message = SyncErrors.messageFor(e, ErrorContext.GENERAL)) }
        }
        local.update { it.copy(busy = false) }
    }

    private companion object {
        const val UNAUTHORIZED = "unauthorized"

        @StringRes
        fun typeLabel(type: String): Int = when (type) {
            "ingredient" -> R.string.sync_type_ingredient
            "recipe" -> R.string.sync_type_recipe
            "meal_slot" -> R.string.sync_type_meal_slot
            "pantry_item" -> R.string.sync_type_pantry_item
            "shopping_list" -> R.string.sync_type_shopping_list
            else -> R.string.sync_type_shopping_item
        }

        @StringRes
        fun reasonLabel(code: String): Int = when (code) {
            "too_large" -> R.string.sync_problem_too_large
            "photo_unsyncable" -> R.string.sync_problem_photo_unsyncable
            "invalid_payload" -> R.string.sync_problem_invalid_payload
            "missing_reference" -> R.string.sync_problem_missing_reference
            "apply_failed" -> R.string.sync_problem_apply_failed
            "photo_mismatch" -> R.string.sync_problem_photo_mismatch
            // Codes, die SyncEngine.rejectCode bei abgelehnten Datensätzen schreibt
            "rejected", "forbidden", "invalid_input", "not_found" -> R.string.sync_problem_rejected
            else -> R.string.sync_problem_unknown
        }
    }
}
