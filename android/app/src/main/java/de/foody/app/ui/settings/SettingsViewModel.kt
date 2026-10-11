package de.foody.app.ui.settings

import de.foody.app.scan.ScanPreferences
import de.foody.app.data.GoalPreferences
import de.foody.app.data.ThemePreferences
import de.foody.app.ui.theme.ThemeMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.BackupRepository
import de.foody.app.util.runSuspendCatching
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val db: FoodyDatabase,
    private val themePreferences: ThemePreferences,
    private val goals: GoalPreferences,
    private val scan: ScanPreferences,
) : ViewModel() {
    val dailyKcalGoal = goals.dailyKcal
    fun setDailyKcalGoal(text: String) = goals.setDailyKcal(text.trim().toIntOrNull())

    /** Online-Produktsuche (Open Food Facts) beim Strichcode-Scan; sendet nur die Produktnummer. */
    val onlineSuche = scan.onlineSuche
    fun setOnlineSuche(an: Boolean) = scan.setOnlineSuche(an)

    val themeMode = themePreferences.mode
    fun setThemeMode(mode: ThemeMode) = themePreferences.set(mode)

    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    /** Ob die Synchronisierung verbunden ist; Löschen und Import sind dann gesperrt (sie würden den Haushalt auf dem Server leeren). */
    val syncActive: StateFlow<Boolean> = db.syncDao().observeState().map { it?.active == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Für die Oberfläche vor dem Bestätigungsdialog: bei aktivem Sync die Meldung zeigen und `true` liefern. */
    suspend fun refuseWhileSyncActive(): Boolean {
        val active = db.syncDao().getState()?.active == true
        if (active) _message.value = R.string.sync_blocks_destructive
        return active
    }

    private fun run(ok: Int, block: suspend () -> Unit) = viewModelScope.launch {
        _message.value = runSuspendCatching { block() }.fold({ ok }, { R.string.backup_error })
    }

    private fun runDestructive(ok: Int, block: suspend () -> Unit) = viewModelScope.launch {
        if (!refuseWhileSyncActive()) _message.value = runSuspendCatching { block() }.fold({ ok }, { R.string.backup_error })
    }

    fun export(uri: android.net.Uri) = run(R.string.backup_exported) { backup.export(uri) }
    fun import(uri: android.net.Uri) = runDestructive(R.string.backup_imported) { backup.import(uri) }
    fun deleteAll() = runDestructive(R.string.data_deleted) { backup.deleteAll() }
}
