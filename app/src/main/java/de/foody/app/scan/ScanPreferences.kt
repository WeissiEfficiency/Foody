package de.foody.app.scan

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Einstellung „Online-Produktsuche (Open Food Facts)“; standardmäßig an, sendet nur die Produktnummer. */
@Singleton
class ScanPreferences @Inject constructor(@ApplicationContext context: Context) : OnlineSucheErlaubt {
    private val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)
    private val _onlineSuche = MutableStateFlow(prefs.getBoolean(KEY, true))
    val onlineSuche = _onlineSuche.asStateFlow()

    fun setOnlineSuche(an: Boolean) {
        prefs.edit { putBoolean(KEY, an) }
        _onlineSuche.value = an
    }

    override fun erlaubt() = _onlineSuche.value

    private companion object {
        const val KEY = "onlineProduktsuche"
    }
}
