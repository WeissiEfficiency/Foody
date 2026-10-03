package de.foody.app.ui.settings

import de.foody.app.data.ThemePreferences
import de.foody.app.ui.theme.ThemeMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.repo.BackupRepository
import de.foody.app.util.runSuspendCatching
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {
    val themeMode = themePreferences.mode
    fun setThemeMode(mode: ThemeMode) = themePreferences.set(mode)

    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private fun run(ok: Int, block: suspend () -> Unit) = viewModelScope.launch {
        _message.value = runSuspendCatching { block() }.fold({ ok }, { R.string.backup_error })
    }

    fun export(uri: android.net.Uri) = run(R.string.backup_exported) { backup.export(uri) }
    fun import(uri: android.net.Uri) = run(R.string.backup_imported) { backup.import(uri) }
    fun deleteAll() = run(R.string.data_deleted) { backup.deleteAll() }
}
