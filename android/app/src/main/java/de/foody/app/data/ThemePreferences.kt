package de.foody.app.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Gewählte Darstellung (System/Hell/Dunkel), gespeichert in den App-Einstellungen. */
@Singleton
class ThemePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs.getString(KEY, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val mode = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY, mode.name) }
        _mode.value = mode
    }

    private companion object {
        const val KEY = "themeMode"
    }
}
